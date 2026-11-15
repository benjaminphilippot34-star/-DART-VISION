package com.dartvision.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.AlertDialog
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
    private lateinit var calibrationOverlay: CalibrationOverlay
    private val calibrationPoints = mutableListOf<ImagePoint>()
    private var boardCalibration: BoardCalibration? = null
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
    private var cameraModeEnabled = false
    private var cameraPanelView: LinearLayout? = null
    private var selectedMode: DartGameMode = DartGameMode.X01
    private var x01Engine: X01GameEngine? = null
    private var trainingTurnHandler: ((List<DartHit>) -> String)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadBoardCalibration()
        startLocalGame()
        restoreX01Game()
        checkForUpdates(silent = true)
    }

    private fun buildUi() {
        val bg = 0xFF101318.toInt()
        val panel = 0xFF1B2028.toInt()
        val accent = 0xFFE32636.toInt()
        val white = 0xFFF5F6F8.toInt()
        val muted = 0xFF9AA3AF.toInt()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float, color: Int = white, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(5), dp(4), dp(5))
            }
        fun panel(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panel); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFF2B323D.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        fun button(row: LinearLayout, title: String, primary: Boolean = false, click: () -> Unit) {
            row.addView(Button(this).apply {
                text = title; isAllCaps = false; textSize = 12f
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else white)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (primary) accent else 0xFF252C36.toInt())
                    cornerRadius = dp(14).toFloat()
                    if (!primary) setStroke(dp(1), 0xFF343D49.toInt())
                }
                minHeight = dp(48)
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener { click() }
            })
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.dart_vision_logo); adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(48))
            contentDescription = "Logo DART VISION"
        }
        header.addView(logo)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("DART VISION", 22f, white, true))
        brand.addView(text("PRÉCISION • PERFORMANCE", 10f, muted, true))
        header.addView(brand)
        root.addView(header)

        // Accueil : accès immédiat aux parties et aux modes de jeu.
        val startPanel = panel()
        startPanel.addView(text("DART VISION  •  PRÊT À JOUER", 12f, accent, true))
        startPanel.addView(text("LE JEU COMMENCE ICI", 13f, muted, true))
        val startActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(startActions, "NOUVELLE PARTIE", true) { showGameModes() }
        button(startActions, "JOUEURS") { managePlayers() }
        startPanel.addView(startActions)
        val quickModes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(quickModes, "X01") { selectedMode = DartGameMode.X01; configureX01() }
        button(quickModes, "CRICKET") { selectedMode = DartGameMode.CRICKET; configureTrainingMode() }
        button(quickModes, "ENTRAÎNEMENT") { showTrainingModes() }
        startPanel.addView(quickModes)
        root.addView(startPanel)
        root.addView(buildAccountPanel())

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(8)) }
        modeRow.addView(text("TABLEAU DE BORD", 17f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        button(modeRow, "CHOISIR MODE") { showGameModes() }
        root.addView(modeRow)

        val game = panel()
        val players = state.get().optJSONArray("players") ?: JSONArray()
        val active = state.get().optInt("active", 0).coerceIn(0, (players.length() - 1).coerceAtLeast(0))
        val currentName = players.optJSONObject(active)?.optString("name", "Joueur 1") ?: "Joueur 1"
        game.addView(text("PARTIE EN COURS  •  AU TOUR DE", 11f, accent, true))
        game.addView(text(currentName.uppercase(), 25f, white, true))
        scoreView = text(players.optJSONObject(active)?.optInt("score", 301)?.toString() ?: "301", 54f, white, true).apply { gravity = Gravity.CENTER }
        game.addView(scoreView)
        game.addView(text("SCORE RESTANT", 10f, muted, true).apply { gravity = Gravity.CENTER })
        val scoreButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(scoreButtons, "−20") { action("adjust", "delta" to "-20") }
        button(scoreButtons, "−1") { action("adjust", "delta" to "-1") }
        button(scoreButtons, "+1") { action("adjust", "delta" to "1") }
        button(scoreButtons, "+20") { action("adjust", "delta" to "20") }
        game.addView(scoreButtons)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(actions, "SAISIR 3 FLÉCHETTES", true) { enterManualTurn() }
        button(actions, "ANNULER") { if (x01Engine?.undo() == true) refreshX01() else action("undo") }
        game.addView(actions)
        val next = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(next, "TOUR SUIVANT", true) { action("next_turn") }
        button(next, "NOUVELLE PARTIE") { if (x01Engine != null) configureX01() else action("new_game") }
        game.addView(next)
        root.addView(game)

        val playersPanel = panel()
        playersPanel.addView(text("JOUEURS", 12f, accent, true))
        for (index in 0 until players.length()) {
            val player = players.optJSONObject(index) ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text((if (index == active) "▶  " else "") + player.optString("name", "Joueur ${index + 1}"), 16f, if (index == active) accent else white, index == active), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text(player.optInt("score", 301).toString(), 20f, white, true))
            row.addView(Button(this).apply {
                text = "✎"
                contentDescription = "Modifier le nom de ${player.optString("name", "Joueur ${index + 1}")}"
                isAllCaps = false
                setOnClickListener { editPlayerName(index) }
            })
            playersPanel.addView(row)
        }
        val playerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(playerActions, "AJOUTER JOUEUR") { action("add_player") }
        button(playerActions, "CONFIGURER X01") { configureX01() }
        playersPanel.addView(playerActions)
        root.addView(playersPanel)

        val cameraPanel = panel()
        cameraPanel.addView(text("DART VISION • CAMÉRA & CALIBRATION", 13f, accent, true))
        cameraPanel.addView(text("Aide au cadrage et analyse des changements d’image. La reconnaissance automatique de la pointe et du score n’est pas encore disponible : vérifie chaque résultat manuellement.", 12f, muted))
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(230))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF05090D.toInt()); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
        }
        frame.addView(preview)
        calibrationOverlay = CalibrationOverlay(this).apply { layoutParams = FrameLayout.LayoutParams(-1, -1); onPoint = ::onCalibrationTap }
        frame.addView(calibrationOverlay)
        cameraPanel.addView(frame)
        val camButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(camButtons, "− ZOOM") { setZoom(zoom / 1.25f) }
        button(camButtons, "+ ZOOM") { setZoom(zoom * 1.25f) }
        button(camButtons, "CALIBRER") { beginBoardCalibration() }
        cameraPanel.addView(camButtons)
        val aiButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(aiButtons, "IMAGE AVANT TIR") {
            status.text = if (frameAnalyzer.captureReference()) "Image de référence enregistrée." else "Caméra en attente."
        }
        button(aiButtons, "ANALYSER") {
            status.text = if (!frameAnalyzer.hasReference()) "Enregistre d'abord une image de référence."
            else "Analyse : ${"%.2f".format(frameAnalyzer.changedPercent ?: 0.0)} % de différence. Vérifie le score proposé."
        }
        cameraPanel.addView(aiButtons)
        cameraPanelView = cameraPanel
        cameraPanel.visibility = View.GONE

        val choicePanel = panel()
        choicePanel.addView(text("MODE DE JEU", 13f, accent, true))
        choicePanel.addView(text("Tu peux jouer sans caméra ou l'activer quand tu le souhaites.", 12f, muted))
        val choiceButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(choiceButtons, "MODE NORMAL", true) { setCameraMode(false) }
        button(choiceButtons, "MODE CAMÉRA") { setCameraMode(true) }
        choicePanel.addView(choiceButtons)
        root.addView(choicePanel)
        root.addView(cameraPanel)

        val settings = panel()
        status = text("PRÊT À JOUER", 12f, accent, true)
        settings.addView(text("ÉTAT DE L’APPLICATION", 10f, muted, true))
        settings.addView(status)
        val settingsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button(settingsRow, "MODES DE JEU") { showGameModes() }
        button(settingsRow, "MISES À JOUR") { checkForUpdates(silent = false) }
        button(settingsRow, "RÉGLAGES") { showAppSettings() }
        settings.addView(settingsRow)
        root.addView(settings)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xFF191E26.toInt())
            elevation = dp(12).toFloat()
        }
        button(bottomNav, "ACCUEIL", true) { scroll.smoothScrollTo(0, 0) }
        button(bottomNav, "JOUEURS") { scroll.post { scroll.smoothScrollTo(0, playersPanel.top) } }
        button(bottomNav, "PARTIE") { scroll.post { scroll.smoothScrollTo(0, game.top) } }
        button(bottomNav, "MENU") { showMainMenu() }
        shell.addView(bottomNav, LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(shell)
    }


    private fun buildAccountPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF202630.toInt())
                cornerRadius = 18f
                setStroke(1, 0xFF343D49.toInt())
            }
        }
        panel.addView(TextView(this).apply {
            text = "COMPTE DART VISION"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFF563D.toInt())
        })
        panel.addView(TextView(this).apply {
            text = "Crée un compte ou connecte-toi. La synchronisation du profil et des parties entre appareils sera activée dans une prochaine version."
            textSize = 13f
            setTextColor(0xFFB4BDC8.toInt())
        })
        val email = EditText(this).apply {
            hint = "Adresse e-mail"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val password = EditText(this).apply {
            hint = "Mot de passe (8 caractères minimum)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val phone = EditText(this).apply {
            hint = "Téléphone au format +33612345678"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        panel.addView(email)
        panel.addView(password)
        panel.addView(phone)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun actionButton(label: String, action: () -> Unit) {
            row.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
        }
        actionButton("Créer compte") { runAccountRequest { SupabaseAuthClient().signUp(email.text.toString().trim(), password.text.toString()) } }
        actionButton("Connexion") { runAccountRequest { SupabaseAuthClient().signIn(email.text.toString().trim(), password.text.toString()) } }
        panel.addView(row)
        val phoneRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        phoneRow.addView(Button(this).apply {
            text = "Code SMS"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                val number = phone.text.toString().trim()
                if (!number.startsWith("+") || number.length < 8) {
                    Toast.makeText(this@MainActivity, "Saisis le numéro au format international (+33...).", Toast.LENGTH_LONG).show()
                } else runAccountRequest { SupabaseAuthClient().requestPhoneOtp(number) }
            }
        })
        phoneRow.addView(Button(this).apply {
            text = "Valider SMS"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                val number = phone.text.toString().trim()
                val code = EditText(this@MainActivity).apply { hint = "Code reçu par SMS"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                AlertDialog.Builder(this@MainActivity).setTitle("Vérification du téléphone").setView(code)
                    .setPositiveButton("Valider") { _, _ ->
                        runAccountRequest { SupabaseAuthClient().verifyPhoneOtp(number, code.text.toString().trim()) }
                    }.setNegativeButton("Annuler", null).show()
            }
        })
        panel.addView(phoneRow)
        return panel
    }

    private fun runAccountRequest(request: () -> SupabaseAuthClient.AuthResult) {
        Toast.makeText(this, "Connexion à Supabase…", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val result = request()
                runOnUiThread {
                    val message = if (result.accessToken != null) {
                        getSharedPreferences("dart_vision_account", MODE_PRIVATE).edit()
                            .putString("user_id", result.userId).putString("email", result.email).apply()
                        "Compte connecté : ${result.email ?: result.userId ?: "joueur"}"
                    } else "Demande envoyée. Vérifie ta boîte mail ou tes SMS."
                    status.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val message = e.message ?: "Connexion impossible."
                    status.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showMainMenu() {
        val items = arrayOf(
            "Nouvelle partie / configuration X01",
            "Choisir un mode de jeu",
            "Gérer les joueurs",
            "Statistiques de la partie",
            "Historique des tours",
            "Mode caméra",
            "Paramètres et mises à jour"
        )
        AlertDialog.Builder(this)
            .setTitle("DART VISION • MENU")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> configureX01()
                    1 -> showGameModes()
                    2 -> managePlayers()
                    3 -> showGameStatistics()
                    4 -> showTurnHistory()
                    5 -> setCameraMode(true)
                    6 -> showAppSettings()
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun managePlayers() {
        val players = state.get().optJSONArray("players") ?: JSONArray()
        val labels = (0 until players.length()).map { i ->
            val p = players.optJSONObject(i)
            "${i + 1}. ${p?.optString("name", "Joueur ${i + 1}") ?: "Joueur ${i + 1}"}"
        }.toMutableList()
        labels.add("＋ Ajouter un joueur")
        AlertDialog.Builder(this)
            .setTitle("Gestion des joueurs")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == players.length()) editPlayerName(null)
                else {
                    val player = players.optJSONObject(which) ?: return@setItems
                    val actions = arrayOf("Renommer", "Supprimer")
                    AlertDialog.Builder(this)
                        .setTitle(player.optString("name", "Joueur ${which + 1}"))
                        .setItems(actions) { _, action ->
                            if (action == 0) editPlayerName(which)
                            else if (players.length() > 1) {
                                players.remove(which)
                                val s = state.get()
                                s.put("players", players)
                                s.put("active", s.optInt("active", 0).coerceIn(0, players.length() - 1))
                                state.set(s); saveLocalState(s); refreshScore()
                                Toast.makeText(this, "Joueur supprimé.", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(this, "Il faut conserver au moins un joueur.", Toast.LENGTH_LONG).show()
                        }.show()
                }
            }.setNegativeButton("Fermer", null).show()
    }

    private fun editPlayerName(index: Int?) {
        val input = EditText(this).apply {
            hint = "Écris le nom que tu veux"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            setSingleLine(true)
            if (index != null) {
                val ps = state.get().optJSONArray("players") ?: JSONArray()
                setText(ps.optJSONObject(index)?.optString("name", ""))
                setSelection(text.length)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(if (index == null) "Ajouter un joueur" else "Modifier le nom du joueur")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Valider") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Saisis un nom.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val s = state.get()
                val ps = s.optJSONArray("players") ?: JSONArray()
                if (index == null) ps.put(JSONObject().put("name", name).put("score", s.optInt("mode", "301".toIntOrNull() ?: 301)))
                else ps.optJSONObject(index)?.put("name", name)
                s.put("players", ps); state.set(s); saveLocalState(s); refreshScore()
                Toast.makeText(this, "Joueurs mis à jour.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showGameStatistics() {
        val engine = x01Engine
        val title = "STATISTIQUES"
        val rows = mutableListOf<Pair<String, String>>()
        val metrics = mutableListOf<Pair<String, String>>()
        if (engine != null) {
            val s = engine.state
            val turns = s.history
            val darts = turns.sumOf { it.hits.size }
            val points = turns.sumOf { turn -> turn.hits.sumOf { it.points } }
            val average = if (darts == 0) 0.0 else points * 3.0 / darts
            val bestVisit = turns.maxOfOrNull { turn -> turn.hits.sumOf { it.points } } ?: 0
            metrics.add("TOURS" to turns.size.toString())
            metrics.add("FLÉCHETTES" to darts.toString())
            metrics.add("MOYENNE" to String.format("%.2f", average))
            metrics.add("MEILLEURE VOLÉE" to bestVisit.toString())
            s.players.forEach { player ->
                rows.add(player.name to "${player.score} restants   •   ${player.legsWon} legs   •   ${player.setsWon} sets")
            }
        } else {
            val ps = state.get().optJSONArray("players") ?: JSONArray()
            metrics.add("MODE" to state.get().optString("mode", "301"))
            metrics.add("JOUEURS" to ps.length().toString())
            metrics.add("TOUR" to state.get().optInt("turn", 1).toString())
            metrics.add("HISTORIQUE" to (state.get().optJSONArray("history")?.length() ?: 0).toString())
            for (i in 0 until ps.length()) {
                val p = ps.optJSONObject(i) ?: continue
                rows.add(p.optString("name", "Joueur ${i + 1}") to "${p.optInt("score")} points restants")
            }
        }
        showInsightScreen(title, "VUE D’ENSEMBLE DE LA PARTIE", metrics, rows)
    }

    private fun showTurnHistory() {
        val engine = x01Engine
        val rows = if (engine == null || engine.state.history.isEmpty()) {
            listOf("Aucun tour enregistré" to "Les tours apparaîtront ici après la première volée.")
        } else engine.state.history.takeLast(30).asReversed().mapIndexed { index, turn ->
            val player = engine.state.players.getOrNull(turn.playerIndex)?.name ?: "Joueur"
            val hits = turn.hits.joinToString("   •   ") { hit ->
                when {
                    hit.number == null -> "MISS"
                    hit.number == 25 && hit.multiplier == 2 -> "BULL"
                    hit.number == 25 -> "25"
                    else -> "${if (hit.multiplier == 1) "S" else if (hit.multiplier == 2) "D" else "T"}${hit.number}"
                }
            }
            val result = if (turn.bust) "BUST" else "${turn.scoreBefore}  →  ${turn.scoreAfter}"
            "VOLÉE ${engine.state.history.size - index} • $player" to "$hits\n$result"
        }
        showInsightScreen("HISTORIQUE", "30 DERNIÈRES VOLÉES", emptyList(), rows)
    }

    private fun showInsightScreen(
        title: String,
        subtitle: String,
        metrics: List<Pair<String, String>>,
        rows: List<Pair<String, String>>
    ) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val backgroundColor = 0xFF101318.toInt()
        val cardColor = 0xFF1B2028.toInt()
        val accentColor = 0xFFE32636.toInt()
        val whiteColor = 0xFFF5F6F8.toInt()
        val mutedColor = 0xFF9AA3AF.toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(backgroundColor)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(this).apply {
            text = title
            textSize = 23f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(whiteColor)
        })
        heading.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "FERMER"
            isAllCaps = false
            setTextColor(whiteColor)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF303844.toInt())
                cornerRadius = dp(12).toFloat()
            }
            contentDescription = "Fermer l’écran"
        })
        content.addView(header)
        if (metrics.isNotEmpty()) {
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(20), 0, dp(8))
            }
            metrics.chunked(2).forEach { pair ->
                val metricRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { (label, value) ->
                    val metric = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(12), dp(10), dp(12))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(cardColor)
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(1), 0xFF2B323D.toInt())
                        }
                    }
                    metric.addView(TextView(this).apply {
                        text = label
                        textSize = 9f
                        setTextColor(mutedColor)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    metric.addView(TextView(this).apply {
                        text = value
                        textSize = 24f
                        setTextColor(whiteColor)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    metric.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                    metricRow.addView(metric)
                }
                if (pair.size == 1) {
                    metricRow.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
                }
                grid.addView(metricRow)
            }
            content.addView(grid)
        }
        val section = TextView(this).apply {
            text = if (title == "HISTORIQUE") "DÉTAIL DES VOLÉES" else "JOUEURS"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(3), dp(14), dp(3), dp(8))
        }
        content.addView(section)
        rows.forEachIndexed { index, (label, detail) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), 0xFF2B323D.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            }
            item.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(whiteColor)
            })
            item.addView(TextView(this).apply {
                text = detail
                textSize = 12f
                setTextColor(mutedColor)
                setPadding(0, dp(4), 0, 0)
            })
            content.addView(item)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val dialog = android.app.Dialog(this)
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // Le bouton de fermeture est relié après l’affichage pour utiliser le contexte de la fenêtre.
        (header.getChildAt(1) as? Button)?.setOnClickListener { dialog.dismiss() }
    }

    private fun showAppSettings() {
        val items = arrayOf("Activer le mode normal", "Activer le mode caméra", "Effacer le calibrage", "Rechercher une mise à jour")
        AlertDialog.Builder(this).setTitle("Paramètres")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setCameraMode(false)
                    1 -> setCameraMode(true)
                    2 -> clearBoardCalibration()
                    3 -> checkForUpdates(silent = false)
                }
            }.setNegativeButton("Fermer", null).show()
    }

    private fun beginBoardCalibration() {
        calibrationPoints.clear()
        calibrationOverlay.resetPoints()
        calibrationOverlay.active = true
        status.text = "CALIBRATION : touche les 4 coins extérieurs dans l'ordre : haut gauche, haut droite, bas droite, bas gauche. Garde toute la cible visible."
    }

    private fun onCalibrationTap(viewX: Double, viewY: Double) {
        if (!calibrationOverlay.active) return
        if (frameAnalyzer.imageWidth <= 0 || frameAnalyzer.imageHeight <= 0) {
            Toast.makeText(this, "Attends que l'image caméra soit prête.", Toast.LENGTH_SHORT).show()
            return
        }
        val point = PreviewCoordinateMapper.viewToImage(
            viewX, viewY, calibrationOverlay.width, calibrationOverlay.height,
            frameAnalyzer.imageWidth, frameAnalyzer.imageHeight, frameAnalyzer.rotationDegrees
        )
        if (point == null) {
            Toast.makeText(this, "Touche à l'intérieur de l'image caméra.", Toast.LENGTH_SHORT).show()
            return
        }
        calibrationPoints.add(point)
        calibrationOverlay.addScreenPoint(viewX, viewY)
        if (calibrationPoints.size < 4) {
            status.text = "Calibration : point ${calibrationPoints.size}/4 enregistré. Vise le coin ${calibrationPoints.size + 1}."
            return
        }
        try {
            boardCalibration = BoardCalibration.fromFourCorners(calibrationPoints.toList())
            val json = JSONArray().apply {
                calibrationPoints.forEach { put(JSONObject().put("x", it.x).put("y", it.y)) }
            }
            getSharedPreferences("dart_vision_calibration", MODE_PRIVATE).edit()
                .putString("corners", json.toString()).apply()
            calibrationOverlay.active = false
            status.text = "Calibration enregistrée. La transformation perspective est prête ; la localisation automatique de la pointe reste à intégrer."
            Toast.makeText(this, "Calibration de la cible enregistrée.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            calibrationPoints.clear()
            calibrationOverlay.resetPoints()
            status.text = "Calibration invalide : ${e.message}. Recommence en visant les quatre coins."
        }
    }

    private fun loadBoardCalibration() {
        val raw = getSharedPreferences("dart_vision_calibration", MODE_PRIVATE).getString("corners", null) ?: return
        try {
            val json = JSONArray(raw)
            if (json.length() != 4) return
            val points = (0 until 4).map { i ->
                val p = json.getJSONObject(i)
                ImagePoint(p.getDouble("x"), p.getDouble("y"))
            }
            boardCalibration = BoardCalibration.fromFourCorners(points)
            calibrationPoints.clear()
            calibrationPoints.addAll(points)
            status.text = "Calibration de cible restaurée."
        } catch (_: Exception) {
            boardCalibration = null
        }
    }

    private fun clearBoardCalibration() {
        boardCalibration = null
        calibrationPoints.clear()
        calibrationOverlay.resetPoints()
        calibrationOverlay.active = false
        getSharedPreferences("dart_vision_calibration", MODE_PRIVATE).edit().remove("corners").apply()
        status.text = "Calibration effacée."
    }

    private fun showTrainingModes() {
        val modes = DartCounterModeCatalog.default.modes.filter { it.category != GameModeCategory.CLASSIC || it == DartGameMode.X01 }
            .filter { it != DartGameMode.X01 }
        val labels = modes.map { "${it.title} — ${it.description}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Entraînement et jeux")
            .setItems(labels) { _, which ->
                selectedMode = modes[which]
                configureTrainingMode()
            }
            .setNegativeButton("Retour", null)
            .show()
    }

    private fun showGameModes() {
        val modes = DartCounterModeCatalog.default.modes
        val labels = modes.map { "${it.title} — ${it.description}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choisir un mode hors ligne")
            .setItems(labels) { _, which ->
                selectedMode = modes[which]
                if (selectedMode == DartGameMode.X01) configureX01() else configureTrainingMode()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }


    private fun configureTrainingMode() {
        x01Engine = null
        val names = mutableListOf<String>()
        val players = state.get().optJSONArray("players") ?: JSONArray()
        for (i in 0 until players.length()) names.add(players.getJSONObject(i).optString("name", "Joueur ${i + 1}"))
        if (names.isEmpty()) names.add("Joueur 1")
        val summary = StringBuilder()
        trainingTurnHandler = when (selectedMode) {
            DartGameMode.CRICKET -> {
                val game = CricketGameEngine(if (names.size >= 2) names else names + "Joueur 2")
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\n${game.state.players.joinToString { "${it.name} : ${it.points} pts" }}"); scoreView.text = "${selectedMode.title}\nMode local hors ligne$summary"; "Cricket : tour enregistré." }
                handler
            }
            DartGameMode.TACTICS -> {
                val game = TacticsGameEngine(if (names.size >= 2) names else names + "Joueur 2")
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\n${game.state.players.joinToString { "${it.name} : ${it.points} pts" }}"); scoreView.text = "${selectedMode.title}\nMode local hors ligne$summary"; "Tactics : tour enregistré." }
                handler
            }
            DartGameMode.SHANGHAI -> {
                val game = ShanghaiGameEngine(if (names.size >= 2) names else names + "Joueur 2")
                val handler: (List<DartHit>) -> String = { hits -> val player = game.state.activePlayer; game.playTurn(player, hits); summary.append("\n${game.state.players.joinToString { "${it.name} : ${it.score} pts" }}"); scoreView.text = "${selectedMode.title}\nManche ${game.state.round}$summary"; "Shanghai : tour enregistré." }
                handler
            }
            DartGameMode.AROUND_THE_CLOCK -> {
                val game = AroundClockEngine(names.first())
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nCible : ${game.state.target}"); scoreView.text = "${selectedMode.title}$summary"; if (game.state.complete) "Bravo, parcours terminé !" else "Cible suivante : ${game.state.target}" }
                handler
            }
            DartGameMode.BOBS_27 -> {
                val game = Bobs27Engine()
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nScore : ${game.state.score} • Double : ${game.state.doubleNumber}"); scoreView.text = "${selectedMode.title}$summary"; "Bob's 27 : score ${game.state.score}." }
                handler
            }
            DartGameMode.HALVE_IT -> {
                val game = HalveItEngine()
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nScore : ${game.state.score} • Manche : ${game.state.roundIndex}"); scoreView.text = "${selectedMode.title}$summary"; "Halve-It : score ${game.state.score}." }
                handler
            }
            DartGameMode.CHECKOUT_121 -> {
                val game = Checkout121Engine()
                val handler: (List<DartHit>) -> String = { hits -> val success = game.attempt(hits); summary.append("\nNiveau : ${game.state.level} • cible : ${game.state.targetScore} • réussites : ${game.state.completedAttempts}"); scoreView.text = "${selectedMode.title}$summary"; if (success) "Checkout réussi !" else "Échec : retour au Safehouse ${game.state.safehouseScore}." }
                handler
            }
            DartGameMode.RANDOM_CHECKOUT -> {
                val game = RandomCheckoutEngine()
                val handler: (List<DartHit>) -> String = { hits -> val target = game.state.target; game.attempt(hits); summary.append("\nCible : ${game.state.target} • réussites : ${game.state.completed}"); scoreView.text = "${selectedMode.title}$summary"; if (game.state.completed > 0) "Checkout validé !" else "Cible : $target • tentative enregistrée." }
                handler
            }
            DartGameMode.DOUBLES_TRAINING -> {
                val game = DoublesTrainingEngine()
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nDouble visé : ${game.state.target} • réussites : ${game.state.hits}/${game.state.attempts}"); scoreView.text = "${selectedMode.title}$summary"; "Doubles : cible ${game.state.target}." }
                handler
            }
            DartGameMode.SINGLES_TRAINING -> {
                val game = SinglesTrainingEngine()
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nSimple visé : ${game.state.target} • réussites : ${game.state.hits}/${game.state.attempts}"); scoreView.text = "${selectedMode.title}$summary"; "Simples : cible ${game.state.target}." }
                handler
            }
            DartGameMode.SCORE_TRAINING -> {
                val game = ScoreTrainingEngine()
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\nTotal : ${game.state.totalPoints} pts • fléchettes : ${game.state.darts}"); scoreView.text = "${selectedMode.title}$summary"; "Score enregistré : +${hits.sumOf { it.points }}." }
                handler
            }
            DartGameMode.KILLER -> {
                val assigned = (1..20).shuffled().take(names.size.coerceAtLeast(2))
                val game = KillerGameEngine(if (names.size >= 2) names else names + "Joueur 2", assigned)
                val handler: (List<DartHit>) -> String = { hits -> game.playTurn(hits); summary.append("\n${game.state.players.joinToString { "${it.name} : ${it.number} / vies ${it.lives} / killer ${it.isKiller}" }}"); scoreView.text = "${selectedMode.title}$summary"; if (game.state.winner != null) "Gagnant : ${game.state.players[game.state.winner!!].name}" else "Killer : tour de ${game.state.players[game.state.activePlayer].name}." }
                handler
            }
            DartGameMode.X01 -> null
        }
        scoreView.text = "${selectedMode.title}\nMode local hors ligne${summary}"
        status.text = "MODE ${selectedMode.title.uppercase()} • moteur local • saisie manuelle"
        Toast.makeText(this, "${selectedMode.title} prêt. Saisis les fléchettes du tour.", Toast.LENGTH_LONG).show()
    }

    private fun configureX01() {
        fun spinner(values: List<String>): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
        }
        val scores = listOf("301", "501", "701")
        val inRules = listOf("Entrée libre", "Entrée double", "Entrée master")
        val outRules = listOf("Sortie libre", "Sortie double", "Sortie master")
        val legs = listOf("1", "2", "3", "4", "5")
        val sets = listOf("1", "2", "3", "4", "5")
        val playerCounts = (1..4).map { it.toString() }
        val scoreSpinner = spinner(scores)
        val inSpinner = spinner(inRules)
        val outSpinner = spinner(outRules).apply { setSelection(1) }
        val legsSpinner = spinner(legs)
        val setsSpinner = spinner(sets)
        val playerSpinner = spinner(playerCounts).apply {
            setSelection(((state.get().optJSONArray("players")?.length() ?: 1) - 1).coerceIn(0, 3))
        }
        val namesInput = EditText(this).apply {
            hint = "Joueur 1, Joueur 2, ..."
            setSingleLine(true)
            val existing = state.get().optJSONArray("players") ?: JSONArray()
            setText((0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("name") }.joinToString(", "))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        fun addChoice(label: String, control: Spinner) {
            content.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(0xFF202020.toInt())
                setPadding(0, 10, 0, 0)
            })
            content.addView(control)
        }
        addChoice("Nombre de joueurs", playerSpinner)
        content.addView(TextView(this).apply {
            text = "Noms des joueurs (séparés par des virgules)"
            textSize = 14f
            setTextColor(0xFF202020.toInt())
            setPadding(0, 10, 0, 0)
        })
        content.addView(namesInput)
        addChoice("Score de départ", scoreSpinner)
        addChoice("Règle d'entrée", inSpinner)
        addChoice("Règle de sortie", outSpinner)
        addChoice("Manches à gagner par set", legsSpinner)
        addChoice("Sets à gagner", setsSpinner)
        AlertDialog.Builder(this)
            .setTitle("Configuration X01")
            .setView(content)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Démarrer") { _, _ ->
                val count = playerSpinner.selectedItemPosition + 1
                val enteredNames = namesInput.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                val names = (0 until count).map { i -> enteredNames.getOrNull(i) ?: "Joueur ${i + 1}" }
                val playersJson = JSONArray()
                names.forEach { playersJson.put(JSONObject().put("name", it).put("score", scores[scoreSpinner.selectedItemPosition].toInt())) }
                val localState = state.get()
                localState.put("players", playersJson).put("active", 0).put("turn", 1).put("mode", "x01")
                state.set(localState)
                saveLocalState(localState)
                val settings = X01Settings(
                    startingScore = scores[scoreSpinner.selectedItemPosition].toInt(),
                    inRule = X01InRule.entries[inSpinner.selectedItemPosition],
                    outRule = X01OutRule.entries[outSpinner.selectedItemPosition],
                    legsToWin = legs[legsSpinner.selectedItemPosition].toInt(),
                    setsToWin = sets[setsSpinner.selectedItemPosition].toInt()
                )
                x01Engine = X01GameEngine(settings, names)
                trainingTurnHandler = null
                selectedMode = DartGameMode.X01
                saveX01Game()
                refreshX01()
            }
            .show()
    }

    private fun enterManualTurn() {
        val engine = x01Engine
        if (engine == null && trainingTurnHandler == null) {
            AlertDialog.Builder(this).setTitle("Aucun mode sélectionné")
                .setMessage("Choisis un mode dans « Modes de jeu ».")
                .setPositiveButton("OK", null).show()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val options = mutableListOf("MISS", "25", "BULL")
        for (n in 1..20) {
            options.add("S$n")
            options.add("D$n")
            options.add("T$n")
        }
        val labels = listOf("FLÉCHETTE 1", "FLÉCHETTE 2", "FLÉCHETTE 3")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val totalView = TextView(this).apply {
            text = "TOTAL : 0"
            textSize = 25f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFE32636.toInt())
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(totalView)
        val selectors = mutableListOf<Spinner>()
        fun dartValue(label: String): DartHit = when {
            label == "MISS" -> DartHit(null, 1)
            label == "25" -> DartHit(25, 1)
            label == "BULL" -> DartHit(25, 2)
            else -> {
                val multiplier = when (label.first()) { 'D' -> 2; 'T' -> 3; else -> 1 }
                DartHit(label.drop(1).toInt(), multiplier)
            }
        }
        fun points(label: String): Int = dartValue(label).points
        fun updateTotal() {
            totalView.text = "TOTAL : ${selectors.sumOf { points(it.selectedItem.toString()) }}"
        }
        labels.forEachIndexed { index, title ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            row.addView(TextView(this).apply {
                text = title
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFF9AA3AF.toInt())
            })
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
                setSelection(0)
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        updateTotal()
                    }
                }
            }
            selectors.add(spinner)
            row.addView(spinner)
            content.addView(row)
        }
        content.addView(TextView(this).apply {
            text = "Choisis le segment et le multiplicateur de chaque fléchette. Les MISS sont prises en compte."
            textSize = 12f
            setTextColor(0xFF50645E.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("SAISIE DES 3 FLÉCHETTES")
            .setView(content)
            .setNegativeButton("ANNULER", null)
            .setPositiveButton("VALIDER LE TOUR", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val hits = selectors.map { dartValue(it.selectedItem.toString()) }
                    val message = if (engine != null) {
                        val result = engine.throwTurn(hits)
                        refreshX01()
                        saveX01Game()
                        when {
                            result.bust -> "Bust : le score du tour est annulé."
                            result.legWon -> "Leg gagné !"
                            else -> "Tour enregistré : ${result.scoreBefore} → ${result.scoreAfter}"
                        }
                    } else trainingTurnHandler!!.invoke(hits)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "Impossible d'enregistrer ce tour.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun scoreToDarts(total: Int): List<DartHit> {
        require(total in 0..180)
        if (total == 0) return List(3) { DartHit(null, 1) }
        val values = (1..20).flatMap { n -> listOf(n to 1, n * 2 to 2, n * 3 to 3) } +
            listOf(25 to 1, 50 to 2)
        fun find(remaining: Int, dartsLeft: Int): List<DartHit>? {
            if (remaining == 0) return emptyList()
            if (dartsLeft == 0) return null
            for ((points, multiplier) in values.asReversed()) {
                if (points <= remaining) {
                    val tail = find(remaining - points, dartsLeft - 1)
                    if (tail != null) {
                        val number = if (points == 25 || points == 50) 25 else points / multiplier
                        return listOf(DartHit(number, multiplier)) + tail
                    }
                }
            }
            return null
        }
        val hits = find(total, 3) ?: throw IllegalArgumentException("Score non représentable")
        return hits + List(3 - hits.size) { DartHit(null, 1) }
    }

    private fun parseDartHit(raw: String): DartHit = DartHitParser.parse(raw)


    private fun saveX01Game() {
        val game = x01Engine ?: return
        val snapshot = game.state
        val settings = snapshot.settings
        val json = JSONObject()
            .put("startingScore", settings.startingScore)
            .put("inRule", settings.inRule.name)
            .put("outRule", settings.outRule.name)
            .put("legsToWin", settings.legsToWin)
            .put("setsToWin", settings.setsToWin)
            .put("activePlayer", snapshot.activePlayer)
            .put("turnNumber", snapshot.turnNumber)
            .put("dartsThrown", snapshot.dartsThrown)
            .put("finished", snapshot.finished)
            .put("legFinished", snapshot.legFinished)
        val players = JSONArray()
        snapshot.players.forEach { p ->
            players.put(JSONObject().put("name", p.name).put("score", p.score)
                .put("hasEntered", p.hasEntered).put("legsWon", p.legsWon).put("setsWon", p.setsWon))
        }
        val history = JSONArray()
        snapshot.history.forEach { turn ->
            val hits = JSONArray()
            turn.hits.forEach { hit ->
                hits.put(JSONObject().put("number", hit.number ?: JSONObject.NULL).put("multiplier", hit.multiplier))
            }
            history.put(JSONObject().put("playerIndex", turn.playerIndex).put("hits", hits)
                .put("scoreBefore", turn.scoreBefore).put("scoreAfter", turn.scoreAfter)
                .put("bust", turn.bust).put("legWon", turn.legWon))
        }
        json.put("players", players).put("history", history)
        getSharedPreferences("dart_vision_local", MODE_PRIVATE).edit()
            .putString("x01_state", json.toString()).apply()
    }

    private fun restoreX01Game(): Boolean {
        val raw = getSharedPreferences("dart_vision_local", MODE_PRIVATE).getString("x01_state", null) ?: return false
        return try {
            val json = JSONObject(raw)
            val settings = X01Settings(
                startingScore = json.getInt("startingScore"),
                inRule = X01InRule.valueOf(json.getString("inRule")),
                outRule = X01OutRule.valueOf(json.getString("outRule")),
                legsToWin = json.getInt("legsToWin"),
                setsToWin = json.getInt("setsToWin")
            )
            val playerJson = json.getJSONArray("players")
            val players = (0 until playerJson.length()).map { i ->
                val p = playerJson.getJSONObject(i)
                X01PlayerState(p.getString("name"), p.getInt("score"), p.optBoolean("hasEntered"),
                    p.optInt("legsWon"), p.optInt("setsWon"))
            }
            val historyJson = json.getJSONArray("history")
            val history = (0 until historyJson.length()).map { i ->
                val turn = historyJson.getJSONObject(i)
                val hitsJson = turn.getJSONArray("hits")
                val hits = (0 until hitsJson.length()).map { j ->
                    val h = hitsJson.getJSONObject(j)
                    val n = h.opt("number")
                    DartHit(if (n == null || n == JSONObject.NULL) null else (n as Number).toInt(), h.getInt("multiplier"))
                }
                X01TurnResult(turn.getInt("playerIndex"), hits, turn.getInt("scoreBefore"),
                    turn.getInt("scoreAfter"), turn.getBoolean("bust"), turn.getBoolean("legWon"))
            }
            val engine = X01GameEngine(settings, players.map { it.name })
            engine.restore(X01GameState(settings, players, json.getInt("activePlayer"),
                json.getInt("turnNumber"), json.getInt("dartsThrown"), json.getBoolean("finished"),
                json.getBoolean("legFinished"), history))
            x01Engine = engine
            selectedMode = DartGameMode.X01
            refreshX01()
            true
        } catch (_: Exception) {
            getSharedPreferences("dart_vision_local", MODE_PRIVATE).edit().remove("x01_state").apply()
            false
        }
    }

    private fun refreshX01() {
        val engine = x01Engine ?: return
        val s = engine.state
        val lines = StringBuilder("X01 ${s.settings.startingScore} • Tour ${s.turnNumber}\n")
        s.players.forEachIndexed { i, p ->
            lines.append(if (i == s.activePlayer) "▶ " else "   ")
                .append(p.name).append(" : ").append(p.score).append("  • Legs ").append(p.legsWon).append(" • Sets ").append(p.setsWon).append("\n")
        }
        if (s.legFinished && !s.finished) lines.append("\nManche terminée — appuie sur Nouvelle manche.")
        if (s.finished) lines.append("\nMatch terminé !")
        scoreView.text = lines.toString()
        status.text = "MODE X01 • ${s.settings.startingScore} • hors ligne"
    }

    private fun addButton(row: LinearLayout, title: String, click: () -> Unit) {
        val b = Button(this).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFB71929.toInt())
                cornerRadius = 12f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), 0xFFFF4655.toInt())
            }
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setPadding(8, 5, 8, 5)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins((3 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt(),
                    (3 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt())
            }
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

    private fun setCameraMode(enabled: Boolean) {
        cameraModeEnabled = enabled
        cameraPanelView?.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
            camera = null
            status.text = "MODE NORMAL • Saisie manuelle activée, caméra désactivée."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
            status.text = "MODE CAMÉRA • Caméra activée."
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 51)
        }
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
            val current = state.get()
            val s = JSONObject(current.toString())
            val players = s.optJSONArray("players") ?: JSONArray()
            when (name) {
                "undo" -> {
                    val history = s.optJSONArray("history") ?: JSONArray()
                    if (history.length() > 0) {
                        val previous = history.getJSONObject(history.length() - 1)
                        s.put("players", JSONArray(previous.getJSONArray("players").toString()))
                        s.put("active", previous.optInt("active", 0))
                        s.put("turn", previous.optInt("turn", 1))
                        s.put("mode", previous.optString("mode", "301"))
                        history.remove(history.length() - 1)
                        s.put("history", history)
                    } else {
                        s.put("lastAction", "Aucune action à annuler")
                        state.set(s)
                        saveLocalState(s)
                        return
                    }
                }
                "new_game", "add_player", "next_turn", "adjust" -> {
                    val history = s.optJSONArray("history") ?: JSONArray()
                    val previousPlayers = JSONArray(players.toString())
                    history.put(JSONObject()
                        .put("players", previousPlayers)
                        .put("active", s.optInt("active", 0))
                        .put("turn", s.optInt("turn", 1))
                        .put("mode", s.optString("mode", "301")))
                    while (history.length() > 20) history.remove(0)
                    s.put("history", history)
                    when (name) {
                        "new_game" -> {
                            val fresh = JSONArray()
                            for (i in 0 until players.length()) {
                                fresh.put(JSONObject().put("name", players.getJSONObject(i).optString("name", "Joueur ${i+1}")).put("score", 301))
                            }
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
                                val i = s.optInt("active").coerceIn(0, players.length() - 1)
                                val p = players.getJSONObject(i)
                                val delta = params["delta"]?.toIntOrNull() ?: 0
                                p.put("score", (p.optInt("score", 301) + delta).coerceAtLeast(0))
                            }
                        }
                    }
                }
                else -> return
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

    // Manifeste public du canal bêta ; le workflow de publication le met à jour.
    private val updateManifestUrl = "https://raw.githubusercontent.com/benjaminphilippot34-star/-DART-VISION/main/latest.json"
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
                if (apk.exists()) apk.delete()
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
        if (requestCode == 51 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && cameraModeEnabled) startCamera()
        else if (requestCode == 51) { cameraModeEnabled = false; cameraPanelView?.visibility = View.GONE; status.text = "Caméra non activée. Le mode normal reste disponible." }
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
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "DART VISION 0.7.0 • API locale active")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}
