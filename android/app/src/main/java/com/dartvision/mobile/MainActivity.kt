package com.dartvision.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class Player(var name: String, var score: Int)

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var scoreView: TextView
    private lateinit var preview: PreviewView
    private lateinit var joinInput: EditText
    private lateinit var connectButton: Button
    private var camera: Camera? = null
    private val frameAnalyzer = FrameChangeAnalyzer()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var server: GameServer? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val poller = Executors.newSingleThreadScheduledExecutor()
    private val state = AtomicReference(JSONObject())
    private var hostUrl: String? = null
    private var isHost = true
    private var zoom = 1f
    private var selectedMode: DartGameMode = DartGameMode.X01
    private var x01Engine: X01GameEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        startLocalGame()
        checkForUpdates(silent = true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 51)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 7, 0, 7)
        }
        val logoView = ImageView(this).apply {
            setImageResource(com.dartvision.mobile.R.drawable.dart_vision_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, 150)
            contentDescription = "Logo DART VISION"
        }
        root.addView(logoView)
        status = label("DART VISION 0.6.1 PREMIUM • démarrage…", 18f)
        root.addView(status)
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(preview)
        val analysisRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(analysisRow, "Référence avant lancer") {
            val captured = frameAnalyzer.captureReference()
            status.text = if (captured) "Image de référence enregistrée. Lance une fléchette puis mesure le changement." else "Attends que la caméra fournisse une image."
        }
        addButton(analysisRow, "Mesurer changement") {
            val percent = frameAnalyzer.changedPercent
            status.text = if (!frameAnalyzer.hasReference()) "Enregistre d'abord une image de référence."
                else if (percent == null) "Analyse caméra en cours…"
                else {
                    val regions = frameAnalyzer.changedRegions
                    val largest = regions.firstOrNull()
                    if (largest == null) "Différence : %.2f %% • aucune zone assez grande".format(percent)
                    else "Différence : %.2f %% • %d zone(s) • principale : %d px, centre (%.1f ; %.1f) • contrôle visuel nécessaire"
                        .format(percent, regions.size, largest.pixelCount, largest.centroidX, largest.centroidY)
                }
        }
        root.addView(analysisRow)
        scoreView = label("301", 28f)
        scoreView.gravity = Gravity.CENTER
        root.addView(scoreView)
        val rowJoin = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        joinInput = EditText(this).apply {
            hint = "IP du téléphone principal (ex. 192.168.1.20)"
            setSingleLine(true); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFFB0BEC5.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        connectButton = Button(this).apply { text = "Rejoindre" ; setOnClickListener { joinHost(joinInput.text.toString().trim()) } }
        rowJoin.addView(joinInput); rowJoin.addView(connectButton); root.addView(rowJoin)
        root.addView(label("Commandes partagées — chaque téléphone peut piloter la partie", 13f))
        val updateButton = Button(this).apply {
            text = "Vérifier les mises à jour"
            setOnClickListener { checkForUpdates(silent = false) }
        }
        root.addView(updateButton)
        val row1 = LinearLayout(this)
        addButton(row1, "Nouvelle 301") { action("new_game") }
        addButton(row1, "+ Joueur") { action("add_player") }
        addButton(row1, "Tour suivant") { action("next_turn") }
        root.addView(row1)
        val row2 = LinearLayout(this)
        addButton(row2, "− 1") { action("adjust", "delta" to "-1") }
        addButton(row2, "− 20") { action("adjust", "delta" to "-20") }
        addButton(row2, "+ 20") { action("adjust", "delta" to "20") }
        root.addView(row2)
        val row3 = LinearLayout(this)
        addButton(row3, "Annuler") { if (x01Engine?.undo() == true) refreshX01() else action("undo") }
        addButton(row3, "Zoom −") { setZoom(zoom / 1.25f) }
        addButton(row3, "Zoom +") { setZoom(zoom * 1.25f) }
        root.addView(row3)
        val row4 = LinearLayout(this)
        addButton(row4, "Modes DartCounter") { showGameModes() }
        addButton(row4, "Saisir un tour") { enterManualTurn() }
        root.addView(row4)
        setContentView(root)
    }


    private fun showGameModes() {
        val modes = DartCounterModeCatalog.default.modes
        val labels = modes.map { "${it.title} — ${it.description}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choisir un mode hors ligne")
            .setItems(labels) { _, which ->
                selectedMode = modes[which]
                if (selectedMode == DartGameMode.X01) configureX01()
                else AlertDialog.Builder(this)
                    .setTitle(selectedMode.title)
                    .setMessage("${selectedMode.description}\n\nLe moteur de règles local est ajouté au projet. L'écran de partie dédié à ce mode est encore en cours d'intégration. Tu peux continuer à utiliser le X01.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun configureX01() {
        val choices = arrayOf("301", "501", "701")
        AlertDialog.Builder(this)
            .setTitle("Score de départ X01")
            .setSingleChoiceItems(choices, 0) { dialog, which ->
                val score = choices[which].toInt()
                val names = mutableListOf<String>()
                val players = state.get().optJSONArray("players") ?: JSONArray()
                for (i in 0 until players.length()) names.add(players.getJSONObject(i).optString("name", "Joueur ${i + 1}"))
                if (names.isEmpty()) names.add("Joueur 1")
                x01Engine = X01GameEngine(X01Settings(startingScore = score), names)
                selectedMode = DartGameMode.X01
                refreshX01()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun enterManualTurn() {
        val engine = x01Engine
        if (engine == null) {
            AlertDialog.Builder(this).setTitle("Mode X01 non configuré")
                .setMessage("Choisis d'abord le mode X01 dans « Modes DartCounter ». La saisie manuelle sert de secours en attendant la détection automatique fiable par caméra.")
                .setPositiveButton("OK", null).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Ex. T20, S20, D16"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Saisir les fléchettes du tour")
            .setMessage("Formats : S20, D20, T20, S25 (25), D25 (50), MISS. Sépare les impacts par des virgules.")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Valider") { _, _ ->
                try {
                    val hits = input.text.toString().split(",").map { parseDartHit(it.trim()) }
                    require(hits.size in 1..3)
                    val result = engine.throwTurn(hits)
                    refreshX01()
                    val message = when {
                        result.bust -> "Bust : le score du tour est annulé."
                        result.legWon -> "Leg gagné !"
                        else -> "Tour enregistré : ${result.scoreBefore} → ${result.scoreAfter}"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Saisie invalide. Exemple : T20, S20, D16", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun parseDartHit(raw: String): DartHit {
        val value = raw.uppercase().replace(" ", "")
        if (value == "MISS" || value == "0") return DartHit.MISS
        val multiplier = when {
            value.startsWith("T") -> 3
            value.startsWith("D") -> 2
            value.startsWith("S") -> 1
            else -> 1
        }
        val numberText = if (value.firstOrNull() in listOf('S', 'D', 'T')) value.drop(1) else value
        val number = numberText.toInt()
        return DartHit(number, multiplier)
    }

    private fun refreshX01() {
        val engine = x01Engine ?: return
        val s = engine.state
        val lines = StringBuilder("X01 ${s.settings.startingScore} • Tour ${s.turnNumber}\n")
        s.players.forEachIndexed { i, p ->
            lines.append(if (i == s.activePlayer) "▶ " else "   ")
                .append(p.name).append(" : ").append(p.score).append("  • Legs ").append(p.legsWon).append("\n")
        }
        if (s.finished) lines.append("\nLeg terminé — configure une nouvelle manche pour continuer.")
        scoreView.text = lines.toString()
        status.text = "MODE X01 • ${s.settings.startingScore} • hors ligne"
    }

    private fun addButton(row: LinearLayout, title: String, click: () -> Unit) {
        val b = Button(this).apply {
            text = title; textSize = 11f; setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        row.addView(b)
    }

    private fun startLocalGame() {
        val saved = getSharedPreferences("dart_vision_local", MODE_PRIVATE).getString("game_state", null)
        val restored = try { saved?.let { JSONObject(it) } } catch (_: Exception) { null }
        if (restored != null && restored.optJSONArray("players") != null && restored.optString("mode") == "301") {
            state.set(restored)
        } else {
            val players = JSONArray().put(JSONObject().put("name", "Joueur 1").put("score", 301))
            state.set(JSONObject().put("mode", "301").put("players", players).put("active", 0).put("turn", 1).put("lastAction", "Partie 301 créée"))
            saveLocalState(state.get())
        }
        isHost = true
        hostUrl = null
        try {
            server = GameServer(8765, state, ::handleHostAction).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            val ip = localIp()
            status.text = if (ip == "IP inconnue") {
                "DART VISION • MODE SOLO HORS LIGNE\nAucun Wi-Fi nécessaire pour jouer. Partie enregistrée sur ce téléphone."
            } else {
                "DART VISION • PARTIE LOCALE\nSolo sans Internet possible. Pour connecter d'autres téléphones : $ip:8765 (réseau local requis)."
            }
        } catch (_: Exception) {
            status.text = "DART VISION • MODE SOLO HORS LIGNE\nServeur multi-téléphones indisponible, mais la partie locale reste utilisable."
        }
        refreshScore()
    }

    private fun saveLocalState(value: JSONObject) {
        getSharedPreferences("dart_vision_local", MODE_PRIVATE)
            .edit().putString("game_state", value.toString()).apply()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val previewCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysisCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewCase, analysisCase)
            } catch (e: Exception) { status.text = status.text.toString() + "\nCaméra : ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setZoom(value: Float) {
        val z = camera?.cameraInfo?.zoomState?.value ?: return
        zoom = value.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    private fun localIp(): String {
        val all = NetworkInterface.getNetworkInterfaces()
        while (all.hasMoreElements()) {
            val addresses = all.nextElement().inetAddresses
            while (addresses.hasMoreElements()) {
                val a = addresses.nextElement()
                if (!a.isLoopbackAddress && a is Inet4Address) return a.hostAddress ?: "IP inconnue"
            }
        }
        return "IP inconnue"
    }

    private fun joinHost(ip: String) {
        if (ip.isBlank()) { status.text = "Saisissez l’adresse IP du téléphone principal."; return }
        val clean = ip.removePrefix("http://").removeSuffix("/")
        worker.execute {
            try {
                val response = httpGet("http://$clean:8765/api/state")
                JSONObject(response)
                hostUrl = "http://$clean:8765"
                isHost = false
                runOnUiThread {
                    status.text = "CONNECTÉ • téléphone secondaire\nToutes les commandes sont actives."
                    connectButton.text = "Connecté"
                }
                poller.scheduleWithFixedDelay({ fetchRemoteState() }, 0, 500, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                runOnUiThread { status.text = "Connexion impossible. Vérifiez le même Wi-Fi et l’adresse IP." }
            }
        }
    }

    private fun fetchRemoteState() {
        val base = hostUrl ?: return
        try {
            val json = JSONObject(httpGet("$base/api/state"))
            state.set(json)
            runOnUiThread { refreshScore() }
        } catch (_: Exception) { runOnUiThread { status.text = "Connexion interrompue • tentative de reconnexion…" } }
    }

    private fun action(name: String, vararg params: Pair<String, String>) {
        worker.execute {
            try {
                if (isHost) handleHostAction(name, params.toMap())
                else {
                    val base = hostUrl ?: return@execute
                    val query = (listOf("action" to name) + params).joinToString("&") {
                        URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
                    }
                    httpGet("$base/api/action?$query")
                }
                if (isHost) runOnUiThread { refreshScore() } else fetchRemoteState()
            } catch (_: Exception) { runOnUiThread { status.text = "Action non transmise. Vérifiez la connexion." } }
        }
    }

    private fun handleHostAction(name: String, params: Map<String, String>) {
        synchronized(state) {
            val s = JSONObject(state.get().toString())
            val players = s.optJSONArray("players") ?: JSONArray()
            when (name) {
                "new_game" -> {
                    val fresh = JSONArray()
                    for (i in 0 until players.length()) fresh.put(JSONObject().put("name", players.getJSONObject(i).optString("name", "Joueur ${i+1}")).put("score", 301))
                    if (fresh.length() == 0) fresh.put(JSONObject().put("name", "Joueur 1").put("score", 301))
                    s.put("players", fresh).put("active", 0).put("turn", 1).put("mode", "301")
                }
                "add_player" -> players.put(JSONObject().put("name", "Joueur ${players.length()+1}").put("score", 301))
                "next_turn" -> {
                    if (players.length() > 0) s.put("active", (s.optInt("active") + 1) % players.length())
                    s.put("turn", s.optInt("turn") + 1)
                }
                "adjust" -> {
                    if (players.length() > 0) {
                        val i = s.optInt("active").coerceIn(0, players.length()-1)
                        val p = players.getJSONObject(i)
                        val next = (p.optInt("score", 501) - (params["delta"]?.toIntOrNull() ?: 0)).coerceAtLeast(0)
                        p.put("score", next)
                    }
                }
                "undo" -> s.put("lastAction", "Annulation demandée (historique complet à intégrer)")
            }
            s.put("lastAction", name)
            state.set(s)
            saveLocalState(s)
        }
    }

    private fun refreshScore() {
        val s = state.get()
        val players = s.optJSONArray("players") ?: JSONArray()
        val lines = StringBuilder("301 • Tour ${s.optInt("turn", 1)}\n")
        for (i in 0 until players.length()) {
            val p = players.optJSONObject(i) ?: continue
            lines.append(if (i == s.optInt("active")) "▶ " else "   ")
                .append(p.optString("name")).append(" : ").append(p.optInt("score")).append("\n")
        }
        scoreView.text = lines.toString()
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 2500; c.requestMethod = "GET"
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    // URL à remplacer lors de la publication du dépôt. Le fichier JSON doit contenir
    // versionCode, versionName, apkUrl et sha256.
    private val updateManifestUrl = "https://raw.githubusercontent.com/OWNER/REPO/main/latest.json"
    private var downloadedUpdate: File? = null

    private fun checkForUpdates(silent: Boolean) {
        if (updateManifestUrl.contains("OWNER/REPO")) {
            if (!silent) AlertDialog.Builder(this)
                .setTitle("Mises à jour DART VISION")
                .setMessage("Le système de mise à jour est préparé. La publication en ligne n'est pas encore configurée.")
                .setPositiveButton("OK", null).show()
            return
        }
        worker.execute {
            try {
                val manifest = JSONObject(httpGet(updateManifestUrl))
                val remoteCode = manifest.optInt("versionCode", -1)
                @Suppress("DEPRECATION")
                val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } else {
                    packageManager.getPackageInfo(packageName, 0).versionCode
                }
                if (remoteCode <= currentCode) {
                    if (!silent) runOnUiThread { AlertDialog.Builder(this).setTitle("DART VISION est à jour").setMessage("Version installée : ${packageManager.getPackageInfo(packageName, 0).versionName}").setPositiveButton("OK", null).show() }
                    return@execute
                }
                val versionName = manifest.optString("versionName", "nouvelle version")
                val apkUrl = manifest.getString("apkUrl")
                val sha256 = manifest.optString("sha256", "").lowercase()
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Mise à jour disponible")
                        .setMessage("DART VISION $versionName est disponible. Télécharger et installer ?")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Télécharger") { _, _ -> downloadAndInstallUpdate(apkUrl, sha256) }
                        .show()
                }
            } catch (e: Exception) {
                if (!silent) runOnUiThread { AlertDialog.Builder(this).setTitle("Vérification impossible").setMessage("Connexion ou publication indisponible. Réessaie plus tard.").setPositiveButton("OK", null).show() }
            }
        }
    }

    private fun downloadAndInstallUpdate(apkUrl: String, expectedSha256: String) {
        runOnUiThread { status.text = "Téléchargement de la mise à jour…" }
        worker.execute {
            try {
                val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 30000; requestMethod = "GET"
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("Téléchargement indisponible")
                val apk = File(cacheDir, "dart-vision-update.apk")
                connection.inputStream.use { input -> FileOutputStream(apk).use { output -> input.copyTo(output) } }
                connection.disconnect()
                if (expectedSha256.isNotBlank()) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    if (!digest.equals(expectedSha256, ignoreCase = true)) {
                        apk.delete(); throw SecurityException("La vérification du fichier a échoué")
                    }
                }
                downloadedUpdate = apk
                runOnUiThread {
                    status.text = "Mise à jour téléchargée. Prête à installer."
                    launchApkInstaller(apk)
                }
            } catch (e: Exception) {
                runOnUiThread { AlertDialog.Builder(this).setTitle("Échec du téléchargement").setMessage(e.message ?: "Erreur inconnue").setPositiveButton("OK", null).show() }
            }
        }
    }

    private fun launchApkInstaller(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Autoriser l'installation")
                .setMessage("Android doit autoriser DART VISION à installer les mises à jour téléchargées.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Ouvrir les paramètres") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }.show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 51 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else status.text = "Autorisation caméra refusée. Les fonctions de score restent disponibles."
    }

    override fun onDestroy() {
        saveLocalState(state.get())
        server?.stop(); worker.shutdown(); poller.shutdownNow(); analysisExecutor.shutdown()
        super.onDestroy()
    }
}

private class GameServer(
    port: Int,
    private val state: AtomicReference<JSONObject>,
    private val action: (String, Map<String, String>) -> Unit
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/api/state" -> newFixedLengthResponse(Response.Status.OK, "application/json", state.get().toString())
            "/api/action" -> {
                val name = session.parameters["action"]?.firstOrNull() ?: ""
                val params = session.parameters.mapValues { it.value.firstOrNull() ?: "" }
                action(name, params)
                newFixedLengthResponse(Response.Status.OK, "application/json", state.get().toString())
            }
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "DART VISION 0.6.1 • API locale active")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}
