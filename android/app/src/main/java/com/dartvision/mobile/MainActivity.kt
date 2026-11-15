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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var scoreContextView: TextView
    private lateinit var scoreTurnView: TextView
    private lateinit var opponentScoresContainer: LinearLayout
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
    private var navigateToMorePage: (() -> Unit)? = null
    private var gamePageView: LinearLayout? = null
    private var manualEntryHost: LinearLayout? = null
    private var manualEntryView: LinearLayout? = null
    private var selectedMode: DartGameMode = DartGameMode.X01
    private var selectedDartMultiplier: Int = 1
    private var x01Engine: X01GameEngine? = null
    private var trainingTurnHandler: ((List<DartHit>) -> String)? = null
    private var availableUpdate: JSONObject? = null
    private var updateNavButton: Button? = null
    private var updateVersionLabel: TextView? = null
    private var updatePageView: LinearLayout? = null
    private var homeUpdateBanner: LinearLayout? = null
    private var updateCheckRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadBoardCalibration()
        startLocalGame()
        restoreX01Game()
        handleAuthCallback(intent)
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates(silent = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(callbackIntent: Intent?) {
        val uri = callbackIntent?.data ?: return
        if (uri.scheme != "dartvision" || uri.host != "auth-callback") return
        val fragment = uri.fragment.orEmpty()
        // Supabase peut renvoyer les paramètres dans le fragment ou dans la query string.
        val values = listOfNotNull(uri.query, fragment)
            .flatMap { it.split("&") }
            .mapNotNull { part ->
                val bits = part.split("=", limit = 2)
                if (bits.size == 2) bits[0] to Uri.decode(bits[1]) else null
            }.toMap()
        val error = values["error_description"] ?: values["error"]
        if (!error.isNullOrBlank()) {
            status.post { status.text = "Confirmation impossible : $error"; Toast.makeText(this, "Confirmation impossible : $error", Toast.LENGTH_LONG).show() }
            return
        }
        val accessToken = values["access_token"]
        if (accessToken.isNullOrBlank()) {
            Toast.makeText(this, "Lien reçu, mais aucun jeton de confirmation trouvé.", Toast.LENGTH_LONG).show()
            return
        }
        worker.execute {
            try {
                val user = SupabaseAuthClient().getUser(accessToken)
                runOnUiThread {
                    getSharedPreferences("dart_vision_account", MODE_PRIVATE).edit()
                        .putString("user_id", user.userId).putString("email", user.email)
                        .putString("access_token", accessToken).putString("refresh_token", values["refresh_token"]).apply()
                    Toast.makeText(this, "Adresse confirmée. Compte connecté.", Toast.LENGTH_LONG).show()
                    showAuthenticatedHome()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message ?: "Validation du compte impossible.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun buildUi() {
        val bg = 0xFF080E15.toInt()
        val panelColor = 0xFF121D27.toInt()
        val teal = 0xFF28D7C2.toInt()
        val white = 0xFFF4F8FA.toInt()
        val muted = 0xFF91A4B2.toInt()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun txt(value: String, size: Float, color: Int = white, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(2), dp(3), dp(2), dp(3))
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL))
            }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panelColor); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFF263744.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
        }
        fun btn(row: LinearLayout, title: String, primary: Boolean = false, click: () -> Unit) {
            row.addView(Button(this).apply {
                text = title; isAllCaps = false; textSize = 12f
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else white)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (primary) teal else 0xFF1B2A35.toInt())
                    cornerRadius = dp(13).toFloat()
                    if (!primary) setStroke(dp(1), 0xFF314653.toInt())
                }
                minHeight = dp(46); stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener { click() }
            })
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18)); setBackgroundColor(bg)
        }
        scroll.addView(root)
        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val gamePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gamePageView = gamePage
        val playersPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val morePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accountPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val updatePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        updatePageView = updatePage
        val hasAccountAtStart = getSharedPreferences("dart_vision_account", MODE_PRIVATE).getString("user_id", null) != null
        val navButtons = mutableListOf<Pair<String, Button>>()
        fun showPage(page: String) {
            home.visibility = if (page == "home") View.VISIBLE else View.GONE
            gamePage.visibility = if (page == "game") View.VISIBLE else View.GONE
            playersPage.visibility = if (page == "players") View.VISIBLE else View.GONE
            morePage.visibility = if (page == "more") View.VISIBLE else View.GONE
            accountPage.visibility = if (page == "account") View.VISIBLE else View.GONE
            updatePage.visibility = if (page == "update") View.VISIBLE else View.GONE
            navButtons.forEach { (key, view) ->
                val selected = key == page
                view.alpha = if (selected) 1f else 0.58f
                view.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) 0xFF174E4B.toInt() else 0xFF121D27.toInt())
                    cornerRadius = dp(13).toFloat()
                    if (selected) setStroke(dp(1), teal)
                }
            }
        }
        navigateToMorePage = { showPage("more") }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(13))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.dart_vision_logo); adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(48))
            contentDescription = "Logo DART VISION"
        })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(txt("DART VISION", 21f, white, true))
        brand.addView(txt("SMART DARTS EXPERIENCE", 9f, teal, true))
        header.addView(brand)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(txt("● LOCAL", 9f, teal, true))
        root.addView(header)

        // Accueil premium
        val hero = card().apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF174541.toInt(), 0xFF122B35.toInt(), 0xFF121D27.toInt())
            ).apply { cornerRadius = dp(21).toFloat(); setStroke(dp(1), 0xFF26766F.toInt()) }
        }
        hero.addView(txt("BIENVENUE SUR", 10f, teal, true))
        hero.addView(txt("La précision à\\nchaque fléchette.", 27f, white, true))
        hero.addView(txt("Joue, progresse et analyse tes performances.", 11f, muted))
        val play = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        btn(play, "▶  JOUER", true) { showPage("game"); showGameModes() }
        hero.addView(play)
        homeUpdateBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(12), dp(13))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFE5C36B.toInt(), 0xFFAA812F.toInt())
            ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFFFFE5A1.toInt()) }
            visibility = View.GONE
        }
        val updateBannerTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val updateBannerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        updateBannerText.addView(txt("↻  NOUVELLE VERSION DISPONIBLE", 14f, 0xFF30230D.toInt(), true))
        updateBannerText.addView(txt("Une mise à jour de DART VISION est prête à installer.", 10f, 0xFF493715.toInt()))
        updateBannerTop.addView(updateBannerText)
        val closeUpdateBanner = TextView(this).apply {
            text = "×"; textSize = 25f; setTextColor(0xFF30230D.toInt()); gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(4), 0); contentDescription = "Masquer l'annonce de mise à jour"
            setOnClickListener { homeUpdateBanner?.visibility = View.GONE }
        }
        updateBannerTop.addView(closeUpdateBanner)
        homeUpdateBanner?.addView(updateBannerTop)
        val updateBannerAction = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(8), 0, 0) }
        btn(updateBannerAction, "METTRE À JOUR", true) {
            val info = availableUpdate
            if (info != null) downloadAndInstallUpdate(info.optString("apkUrl"), info.optString("sha256", ""))
        }
        homeUpdateBanner?.addView(updateBannerAction)
        home.addView(homeUpdateBanner)
        home.addView(hero)
        home.addView(txt("MENU PRINCIPAL", 11f, muted, true).apply { setPadding(dp(3), dp(4), 0, dp(7)) })
        fun tile(title: String, sub: String, glyph: String, click: () -> Unit) {
            val item = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10)) }
            item.addView(txt(glyph, 25f, teal, true).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)) })
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            copy.addView(txt(title, 14f, white, true)); copy.addView(txt(sub, 10f, muted))
            item.addView(copy); item.addView(txt("›", 25f, teal, true))
            item.setOnClickListener { click() }; home.addView(item)
        }
        tile("Modes de jeu", "501, Cricket, Shanghai, entraînement", "◎") { showPage("game"); showGameModes() }
        tile("Caméra & calibrage", "Cadre et calibre ta cible", "⌖") { showPage("more"); setCameraMode(true) }
        tile("Joueurs & équipes", "Profils et participants", "♙") { showPage("players"); managePlayers() }
        tile("Statistiques", "Performances et progression", "▥") { showGameStatistics() }
        tile("Historique", "Retrouve tes dernières manches", "◷") { showTurnHistory() }
        tile("Paramètres", "Application et mises à jour", "⚙") { showPage("more") }
        // L’inscription et la connexion disposent de leur propre onglet.
        accountPage.addView(buildAccountPanel())
        updatePage.addView(txt("MISE À JOUR DART VISION", 20f, white, true))
        updatePage.addView(txt("Les mises à jour apparaissent ici uniquement lorsqu'une nouvelle version est réellement publiée.", 11f, muted))
        updateVersionLabel = txt("Vérification des mises à jour…", 13f, teal, true).apply { setPadding(0, dp(12), 0, dp(12)) }
        updatePage.addView(updateVersionLabel)
        val installUpdateButton = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(installUpdateButton, "TÉLÉCHARGER ET INSTALLER", true) {
            val info = availableUpdate
            if (info != null) downloadAndInstallUpdate(info.optString("apkUrl"), info.optString("sha256", ""))
        }
        updatePage.addView(installUpdateButton)
        updatePage.addView(txt("Android demandera de confirmer l'installation. La mise à jour conserve les données de l'application si elle est signée avec la même clé.", 10f, muted))

        // Écran de jeu compact inspiré de la maquette fournie.
        val currentPlayers = state.get().optJSONArray("players") ?: JSONArray()
        val active = state.get().optInt("active", 0).coerceIn(0, (currentPlayers.length() - 1).coerceAtLeast(0))
        val gameHeading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headingText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        headingText.addView(txt("PARTIE EN COURS", 19f, white, true))
        headingText.addView(txt("501 • SCORE EN DIRECT", 9f, teal, true))
        gameHeading.addView(headingText)
        btn(gameHeading, "SORTIES", false) { showCheckoutSuggestions() }
        btn(gameHeading, "MODES", false) { showGameModes() }
        gamePage.addView(gameHeading)

        val scoreCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF121D27.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFF26766F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
        }
        val activePlayerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, -1, 1.15f)
        }
        activePlayerPanel.addView(txt("JOUEUR ACTIF", 9f, teal, true))
        scoreContextView = txt(currentPlayers.optJSONObject(active)?.optString("name", "Joueur " + (active + 1)) ?: "Joueur 1", 15f, white, true)
            .apply { setPadding(0, dp(5), 0, 0); maxLines = 1 }
        activePlayerPanel.addView(scoreContextView)
        activePlayerPanel.addView(txt("SCORE RESTANT", 9f, muted, true).apply { setPadding(0, dp(8), 0, 0) })
        scoreView = txt(currentPlayers.optJSONObject(active)?.optInt("score", 501)?.toString() ?: "501", 46f, white, true)
            .apply { setPadding(0, 0, 0, 0) }
        activePlayerPanel.addView(scoreView)
        scoreTurnView = txt("TOUR " + state.get().optInt("turn", 1) + " • 3 FLÉCHETTES", 9f, teal, true)
            .apply { setPadding(0, dp(5), 0, 0) }
        activePlayerPanel.addView(scoreTurnView)
        scoreCard.addView(activePlayerPanel)

        val divider = View(this).apply { setBackgroundColor(0xFF314653.toInt()); layoutParams = LinearLayout.LayoutParams(dp(1), -1) }
        scoreCard.addView(divider)
        opponentScoresContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(3), dp(3), dp(3))
        }
        val opponentScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(0, dp(190), 1f)
            addView(opponentScoresContainer, FrameLayout.LayoutParams(-1, -2))
        }
        scoreCard.addView(opponentScroll)
        refreshOpponentScores(currentPlayers, active, muted, white, teal)
        gamePage.addView(scoreCard)

        manualEntryHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gamePage.addView(manualEntryHost)
        showManualEntryPrompt()
        val cameraShortcut = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btn(cameraShortcut, "▣ CAMÉRA", false) {
            cameraPanelView?.let { panel ->
                (panel.parent as? android.view.ViewGroup)?.removeView(panel)
                panel.visibility = View.VISIBLE
                gamePage.addView(panel)
            }
            setCameraMode(true)
        }
        gamePage.addView(cameraShortcut)
        val next = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(next, "TOUR SUIVANT", true) { action("next_turn") }
        btn(next, "NOUVELLE PARTIE") { if (x01Engine != null) configureX01() else showGameModes() }
        gamePage.addView(next)

        // Joueurs
        playersPage.addView(txt("JOUEURS & ÉQUIPES", 20f, white, true))
        playersPage.addView(txt("Gère les participants de tes parties.", 11f, muted))
        val roster = card()
        roster.addView(txt("LISTE DES JOUEURS", 10f, teal, true))
        for (i in 0 until currentPlayers.length()) {
            val p = currentPlayers.optJSONObject(i) ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            row.addView(txt((if (i == active) "●  " else "") + p.optString("name", "Joueur " + (i + 1)), 14f, if (i == active) teal else white, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(txt(p.optInt("score", 301).toString(), 17f, white, true))
            row.addView(Button(this).apply {
                text = "✎"; isAllCaps = false; setTextColor(teal)
                background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                setOnClickListener { editPlayerName(i) }
            })
            roster.addView(row)
        }
        roster.addView(txt("Les modifications sont enregistrées sur cet appareil.", 10f, muted))
        val playerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(playerActions, "＋ AJOUTER") { editPlayerName(null) }
        btn(playerActions, "CONFIGURER X01", true) { configureX01() }
        roster.addView(playerActions); playersPage.addView(roster)

        // Outils
        morePage.addView(txt("OUTILS & PARAMÈTRES", 20f, white, true))
        morePage.addView(txt("Configure ton installation DART VISION.", 11f, muted))
        val cameraPanel = card()
        cameraPanel.addView(txt("CAMÉRA & CALIBRAGE", 13f, teal, true))
        cameraPanel.addView(txt("Aide au cadrage et analyse des changements d’image. La reconnaissance automatique de la pointe et du score n’est pas encore disponible : vérifie chaque résultat manuellement.", 11f, muted))
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; layoutParams = FrameLayout.LayoutParams(-1, -1) }
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(230))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF0D151D.toInt()); cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        frame.addView(preview)
        calibrationOverlay = CalibrationOverlay(this).apply { layoutParams = FrameLayout.LayoutParams(-1, -1); onPoint = ::onCalibrationTap }
        frame.addView(calibrationOverlay); cameraPanel.addView(frame)
        val zoomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(zoomRow, "− ZOOM") { setZoom(zoom / 1.25f) }
        btn(zoomRow, "+ ZOOM") { setZoom(zoom * 1.25f) }
        btn(zoomRow, "CALIBRER", true) { beginBoardCalibration() }
        cameraPanel.addView(zoomRow)
        val analyze = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(analyze, "IMAGE AVANT TIR") { status.text = if (frameAnalyzer.captureReference()) "Image de référence enregistrée." else "Caméra en attente." }
        btn(analyze, "ANALYSER") { status.text = if (!frameAnalyzer.hasReference()) "Enregistre d'abord une image de référence." else "Analyse : " + "%.2f".format(frameAnalyzer.changedPercent ?: 0.0) + " % de différence. Vérifie le score proposé." }
        cameraPanel.addView(analyze); cameraPanelView = cameraPanel; cameraPanel.visibility = View.GONE
        morePage.addView(cameraPanel)
        val settings = card()
        status = txt("PRÊT À JOUER", 12f, teal, true)
        settings.addView(txt("ÉTAT DE L’APPLICATION", 10f, muted, true)); settings.addView(status)
        val settingButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(settingButtons, "MODES") { showPage("game"); showGameModes() }
        btn(settingButtons, "MISES À JOUR") { checkForUpdates(silent = false) }
        btn(settingButtons, "RÉGLAGES") { showAppSettings() }
        settings.addView(settingButtons); morePage.addView(settings)

        root.addView(home); root.addView(gamePage); root.addView(playersPage); root.addView(morePage); root.addView(accountPage); root.addView(updatePage)
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7)); setBackgroundColor(0xFF0D151D.toInt()); elevation = dp(14).toFloat()
        }
        fun nav(key: String, title: String, symbol: String) {
            val button = Button(this).apply {
                text = symbol + "\n" + title; isAllCaps = false; textSize = 10f; setTextColor(white)
                minHeight = dp(54); stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                setOnClickListener { showPage(key) }
            }
            if (key == "update") { updateNavButton = button; button.visibility = View.GONE }
            navButtons.add(key to button); bottom.addView(button)
        }
        nav("home", "ACCUEIL", "⌂"); nav("game", "JOUER", "◎"); nav("players", "JOUEURS", "♙"); nav("more", "PLUS", "•••")
        nav("account", if (hasAccountAtStart) "COMPTE" else "INSCRIPTION", "♙")
        nav("update", "MISE À JOUR", "⬇")
        showPage(if (hasAccountAtStart) "home" else "account")
        shell.addView(bottom, LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(shell)
        // Android 15+ dessine l'application derrière la barre système :
        // réserver sa hauteur sous la navigation pour garder les onglets entièrement visibles.
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val navigationInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, 0, 0, navigationInset)
            insets
        }
        ViewCompat.requestApplyInsets(shell)
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

    private fun showAuthenticatedHome() {
        // Après inscription/connexion, l’onglet d’accueil devient l’écran d’entrée.
        val root = (findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
        val buttons = mutableListOf<Button>()
        fun collect(view: android.view.View) {
            if (view is Button && (view.text.toString().contains("INSCRIPTION") || view.text.toString().contains("COMPTE"))) buttons.add(view)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
        }
        root?.let { collect(it) }
        buttons.forEach { it.text = "♙\\nCOMPTE" }
        // La page d’accueil est l’écran par défaut après authentification.
        recreate()
    }

    private fun runAccountRequest(request: () -> SupabaseAuthClient.AuthResult) {
        Toast.makeText(this, "Connexion à Supabase…", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val result = request()
                runOnUiThread {
                    val message = if (result.accessToken != null) {
                        getSharedPreferences("dart_vision_account", MODE_PRIVATE).edit()
                            .putString("user_id", result.userId).putString("email", result.email)
                            .putString("access_token", result.accessToken)
                            .putString("refresh_token", result.refreshToken).apply()
                        "Compte connecté : ${result.email ?: result.userId ?: "joueur"}"
                    } else "Demande envoyée. Vérifie ta boîte mail ou tes SMS."
                    status.text = message
                    if (result.accessToken != null) {
                        showAuthenticatedHome()
                    }
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
                                state.set(s); saveLocalState(s); refreshUiAfterPlayersChanged()
                                Toast.makeText(this, "Joueur supprimé.", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(this, "Il faut conserver au moins un joueur.", Toast.LENGTH_LONG).show()
                        }.show()
                }
            }.setNegativeButton("Fermer", null).show()
    }

    private fun refreshUiAfterPlayersChanged() {
        buildUi()
        if (x01Engine != null) refreshX01() else refreshScore()
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
                if (index == null) {
                    val startingScore = x01Engine?.state?.settings?.startingScore ?: 301
                    ps.put(JSONObject().put("name", name).put("score", startingScore))
                } else ps.optJSONObject(index)?.put("name", name)
                s.put("players", ps); state.set(s); saveLocalState(s); refreshUiAfterPlayersChanged()
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
            // En X01, une volée BUST ne retire aucun point : elle doit compter pour zéro dans les moyennes.
            val points = turns.sumOf { turn -> if (turn.bust) 0 else (turn.scoreBefore - turn.scoreAfter).coerceAtLeast(0) }
            val average = if (darts == 0) 0.0 else points * 3.0 / darts
            val bestVisit = turns.maxOfOrNull { turn -> if (turn.bust) 0 else (turn.scoreBefore - turn.scoreAfter).coerceAtLeast(0) } ?: 0
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
        showModeCatalogue(trainingOnly = true)
    }

    private fun showGameModes() {
        showModeCatalogue(trainingOnly = false)
    }

    private fun showModeCatalogue(trainingOnly: Boolean) {
        val modes = DartCounterModeCatalog.default.modes.filter {
            if (trainingOnly) it.category == GameModeCategory.TRAINING else true
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val accent = 0xFF28D7C2.toInt()
        val ink = 0xFFF4F8FA.toInt()
        val muted = 0xFF91A4B2.toInt()
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(14), dp(8), dp(14), dp(8))
            useDefaultMargins = false
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (trainingOnly) "ENTRAÎNEMENT" else "CHOISIR UN MODE")
            .setView(ScrollView(this).apply { addView(grid) })
            .setNegativeButton("FERMER", null)
            .create()
        modes.forEach { mode ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(14), dp(13), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF172630.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), 0xFF314653.toInt())
                }
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(132)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
            }
            tile.addView(TextView(this).apply {
                text = when (mode.category) {
                    GameModeCategory.CLASSIC -> "CLASSIQUE"
                    GameModeCategory.TRAINING -> "ENTRAÎNEMENT"
                    GameModeCategory.PARTY -> "MULTIJOUEUR"
                }
                textSize = 9f; setTextColor(accent)
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
            })
            tile.addView(TextView(this).apply {
                text = mode.title; textSize = 16f; setTextColor(ink)
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
                setPadding(0, dp(5), 0, dp(3))
            })
            tile.addView(TextView(this).apply {
                text = mode.description; textSize = 10f; setTextColor(muted)
                maxLines = 3
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            tile.setOnClickListener {
                selectedMode = mode
                dialog.dismiss()
                if (mode == DartGameMode.X01) configureX01() else configureTrainingMode()
            }
            grid.addView(tile)
        }
        dialog.show()
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

    private fun showCheckoutSuggestions() {
        val game = x01Engine
        if (game == null) {
            Toast.makeText(this, "Le calculateur est disponible en mode X01.", Toast.LENGTH_SHORT).show()
            return
        }
        val state = game.state
        val score = state.players[state.activePlayer].score
        if (state.settings.outRule == X01OutRule.STRAIGHT_OUT) {
            AlertDialog.Builder(this).setTitle("Conseils de sortie")
                .setMessage("La partie est configurée en sortie libre : aucun finish en double n'est requis.")
                .setPositiveButton("OK", null).show()
            return
        }
        val routes = CheckoutCalculator.routes(score, 12, masterOut = state.settings.outRule == X01OutRule.MASTER_OUT)
        val message = if (routes.isEmpty()) {
            if (score > 170) "Il n'existe pas de sortie en trois fléchettes au-dessus de 170."
            else "Aucune sortie directe en trois fléchettes pour $score."
        } else routes.mapIndexed { i, route -> "${i + 1}. ${route.notation}" }.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("SORTIES POSSIBLES • $score")
            .setMessage(message)
            .setPositiveButton("FERMER", null)
            .show()
    }

    private fun configureX01() {
        fun spinner(values: List<String>): Spinner = Spinner(this).apply {
            val spinnerAdapter = object : ArrayAdapter<String>(
                this@MainActivity, android.R.layout.simple_spinner_item, values
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val label = (convertView as? TextView) ?: TextView(this@MainActivity)
                    label.text = getItem(position) ?: ""
                    label.textSize = 16f
                    label.setTextColor(0xFF263D39.toInt())
                    label.gravity = Gravity.CENTER_VERTICAL
                    label.setPadding(12, 10, 12, 10)
                    return label
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val label = (convertView as? TextView) ?: TextView(this@MainActivity)
                    label.text = getItem(position) ?: ""
                    label.textSize = 16f
                    label.setTextColor(0xFF263D39.toInt())
                    label.setBackgroundColor(android.graphics.Color.WHITE)
                    label.setPadding(18, 16, 18, 16)
                    return label
                }
            }
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = spinnerAdapter
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFF7FAF9.toInt())
                cornerRadius = 10f
                setStroke(1, 0xFFB8CECA.toInt())
            }
            minimumHeight = 50
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
            textSize = 16f
            setTextColor(0xFF183B36.toInt())
            setHintTextColor(0xFF788A86.toInt())
            setSelectAllOnFocus(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(14, 12, 14, 12)
            minimumHeight = 54
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = 10f
                setStroke(2, 0xFFB8CECA.toInt())
            }
            val existing = state.get().optJSONArray("players") ?: JSONArray()
            setText((0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("name") }.joinToString(", "))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xFFF1F8F7.toInt())
        }
        fun section(title: String, subtitle: String? = null): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 22f
                    setStroke(1, 0xFFD5E8E5.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, 0, 0, 14)
                }
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 17f
                setTextColor(0xFF102C29.toInt())
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
                setPadding(0, 0, 0, 5)
            })
            if (subtitle != null) card.addView(TextView(this).apply {
                text = subtitle
                textSize = 11f
                setTextColor(0xFF647A76.toInt())
                setPadding(0, 0, 0, 8)
            })
            content.addView(card)
            return card
        }
        fun addChoice(parent: LinearLayout, label: String, control: Spinner) {
            parent.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(0xFF263D39.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 9, 0, 2)
            })
            parent.addView(control)
        }
        val playerSection = section("DÉTAILS DES JOUEURS", "Choisis les participants avant de commencer la partie.")
        addChoice(playerSection, "Nombre de joueurs", playerSpinner)
        playerSection.addView(TextView(this).apply {
            text = "Noms des joueurs (séparés par des virgules)"
            textSize = 13f
            setTextColor(0xFF263D39.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 3)
        })
        playerSection.addView(namesInput)
        val rulesSection = section("PARAMÈTRES DE JEU", "Définis le score de départ et les règles de la partie.")
        addChoice(rulesSection, "Score de départ", scoreSpinner)
        addChoice(rulesSection, "Entrée dans la partie", inSpinner)
        addChoice(rulesSection, "Sortie de la partie", outSpinner)
        addChoice(rulesSection, "Manches à gagner par set", legsSpinner)
        addChoice(rulesSection, "Sets à gagner", setsSpinner)
        val scrollContent = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        AlertDialog.Builder(this)
            .setTitle("MATCH")
            .setView(scrollContent)
            .setNegativeButton("ANNULER", null)
            .setPositiveButton("COMMENCER LE JEU") { _, _ ->
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

    private fun showManualEntryPrompt() {
        val host = manualEntryHost ?: return
        host.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val prompt = TextView(this).apply {
            text = "✦  SAISIR LES 3 FLÉCHETTES"
            textSize = 15f
            setTextColor(0xFF28CDBA.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(17), dp(12), dp(17))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1C2A35.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFF28CDBA.toInt())
            }
            setOnClickListener { enterManualTurn() }
        }
        host.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun enterManualTurn(initialHit: DartHit? = null) {
        var engine = x01Engine
        if (engine == null && trainingTurnHandler == null) {
            // Permet la saisie depuis le pavé principal même si aucune partie n'a encore été configurée.
            val savedPlayers = state.get().optJSONArray("players") ?: JSONArray()
            val names = (0 until savedPlayers.length()).mapNotNull { savedPlayers.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }.ifEmpty { listOf("Joueur 1") }
            val startingScore = state.get().optString("mode", "301").toIntOrNull()?.takeIf { it in listOf(301, 501, 701) } ?: 301
            x01Engine = X01GameEngine(
                X01Settings(startingScore = startingScore, inRule = X01InRule.STRAIGHT_IN, outRule = X01OutRule.DOUBLE_OUT),
                names
            )
            selectedMode = DartGameMode.X01
            saveX01Game()
            engine = x01Engine
            refreshX01()
        }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val ink = 0xFFF2F6F8.toInt()
        val teal = 0xFF28CDBA.toInt()
        val coral = 0xFFFF654F.toInt()
        val pale = 0xFF101B24.toInt()
        val white = android.graphics.Color.WHITE
        val hits = mutableListOf<DartHit>()
        initialHit?.let { hits.add(it) }
        var multiplier = selectedDartMultiplier
        var selectedTab = when (multiplier) { 2 -> 1; 3 -> 2; else -> 0 }
        lateinit var render: () -> Unit
        lateinit var addHit: (DartHit) -> Unit
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            setBackgroundColor(pale)
        }
        fun rounded(color: Int, stroke: Int = color, radius: Int = 10) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(color); cornerRadius = dp(radius).toFloat()
                if (stroke != color) setStroke(dp(1), stroke)
            }
        fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; gravity = Gravity.CENTER
                setTextColor(color)
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed",
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL))
                setPadding(dp(2), dp(3), dp(2), dp(3))
            }
        val title = text("C’EST AU TOUR DE LANCER !", 20f, coral, true).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(title, LinearLayout.LayoutParams(-1, dp(42)))
        // Bandeau des joueurs : tous les participants et le score restant du joueur actif.
        val playersBand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(8))
        }
        val playerScoreViews = mutableListOf<TextView>()
        val gamePlayers = engine?.state?.players
        if (gamePlayers != null) {
            gamePlayers.forEachIndexed { index, player ->
                val activePlayer = index == engine.state.activePlayer
                val playerCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                    background = rounded(if (activePlayer) 0xFF174E4B.toInt() else 0xFF1C2A35.toInt(),
                        if (activePlayer) teal else 0xFF3A4D59.toInt(), 9)
                    layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                }
                playerCard.addView(text(player.name, 12f, if (activePlayer) white else ink, true))
                val playerScore = text(player.score.toString(), 19f, if (activePlayer) white else ink, true)
                playerScoreViews.add(playerScore)
                playerCard.addView(playerScore)
                playersBand.addView(playerCard)
            }
        }
        content.addView(playersBand)
        val entry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 30); setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        entry.addView(text("▦", 22f, ink, true), LinearLayout.LayoutParams(dp(42), dp(56)))
        val slots = mutableListOf<TextView>()
        repeat(3) {
            val slot = text("—", 19f, ink, true).apply { background = rounded(0xFF172630.toInt(), 0xFF3A4D59.toInt(), 6) }
            slots.add(slot)
            entry.addView(slot, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        val submit = Button(this).apply {
            text = "SAISIR"; isAllCaps = false; textSize = 13f; setTextColor(0xFF062A27.toInt())
            background = rounded(teal, teal, 28)
            stateListAnimator = null; minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(94), dp(48)).apply { setMargins(dp(3), 0, 0, 0) }
        }
        entry.addView(submit)
        content.addView(entry, LinearLayout.LayoutParams(-1, dp(66)))
        val count = text("0 / 3 fléchettes", 11f, 0xFF9AADB8.toInt(), true)
        content.addView(count, LinearLayout.LayoutParams(-1, dp(25)))
        val visitTotal = text("TOTAL DE LA VOLÉE : 0", 15f, coral, true)
        content.addView(visitTotal, LinearLayout.LayoutParams(-1, dp(30)))
        val tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabs = mutableListOf<TextView>()
        val tabNames = listOf("Simple", "Double", "Treble", "Bull\n50", "Outer\n25")
        tabNames.forEachIndexed { i, name ->
            val tab = text(name, 13f, ink, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)
                setOnClickListener {
                    selectedTab = i
                    multiplier = when (i) { 1 -> 2; 2 -> 3; else -> 1 }
                    render()
                }
            }
            tabs.add(tab); tabsRow.addView(tab)
        }
        content.addView(tabsRow)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cells = mutableListOf<TextView>()
        for (r in 0 until 4) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 5) {
                val n = r * 5 + c + 1
                val cell = text(n.toString(), 25f, ink, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(66), 1f)
                    background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 9)
                    setOnClickListener { if (selectedTab < 3) addHit(DartHit(n, multiplier)) }
                }
                cells.add(cell); row.addView(cell)
            }
            grid.addView(row)
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun bottomButton(caption: String, weight: Float = 1f, click: () -> Unit) =
            Button(this).apply {
                text = caption; isAllCaps = false; textSize = 15f; setTextColor(ink)
                background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 9)
                layoutParams = LinearLayout.LayoutParams(0, dp(56), weight)
                setOnClickListener { click() }
            }
        bottom.addView(bottomButton("↶") { if (hits.isNotEmpty()) { hits.removeAt(hits.lastIndex); render() } })
        bottom.addView(bottomButton("MANQUÉ") { addHit(DartHit.MISS) })
        bottom.addView(Button(this).apply {
            text = "▣"; isAllCaps = false; textSize = 21f; setTextColor(0xFF062A27.toInt())
            background = rounded(teal, teal, 12)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(52))
            setOnClickListener {
                (content.parent as? android.view.ViewGroup)?.removeView(content)
                manualEntryView = null
                showManualEntryPrompt()
                navigateToMorePage?.invoke()
                setCameraMode(true)
            }
        })
        content.addView(bottom)
        render = {
            slots.forEachIndexed { i, slot ->
                val hit = hits.getOrNull(i)
                slot.text = hit?.let {
                    when {
                        it.number == null -> "MISS"
                        it.number == 25 && it.multiplier == 2 -> "50"
                        it.number == 25 -> "25"
                        else -> (when (it.multiplier) { 2 -> "D"; 3 -> "T"; else -> "S" }) + it.number
                    }
                } ?: "—"
                slot.background = rounded(if (i == hits.size && hits.size < 3) 0xFF174E4B.toInt() else 0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 2)
            }
            count.text = "${hits.size} / 3 fléchettes"
            visitTotal.text = "TOTAL DE LA VOLÉE : ${hits.sumOf { it.points }}"
            tabs.forEachIndexed { i, tab ->
                tab.setTextColor(if (i == selectedTab) white else ink)
                tab.background = rounded(if (i == selectedTab) coral else 0xFF1C2A35.toInt(), if (i == selectedTab) coral else 0xFF3A4D59.toInt(), 3)
            }
            cells.forEach { it.alpha = if (hits.size == 3 || selectedTab >= 3) .35f else 1f }
            submit.isEnabled = hits.size == 3
            submit.alpha = if (submit.isEnabled) 1f else .45f
            if (engine != null) {
                if (hits.isNotEmpty()) {
                    val live = engine.previewTurn(hits.toList())
                    playerScoreViews.getOrNull(live.playerIndex)?.text = live.scoreAfter.toString()
                    if (live.playerIndex == engine.state.activePlayer) {
                        scoreView.text = live.scoreAfter.toString()
                        scoreTurnView.text = if (live.bust) "BUST • SCORE RESTAURÉ" else "TOUR ${engine.state.turnNumber} • ${hits.size}/3 FLÉCHETTES"
                    }
                } else {
                    playerScoreViews.getOrNull(engine.state.activePlayer)?.text = engine.state.players[engine.state.activePlayer].score.toString()
                    scoreView.text = engine.state.players[engine.state.activePlayer].score.toString()
                    scoreTurnView.text = "TOUR ${engine.state.turnNumber} • 3 FLÉCHETTES"
                }
            }
        }
        // Sélectionne le bull ou le 25 extérieur directement depuis leur onglet.
        tabs[3].setOnClickListener { selectedTab = 3; addHit(DartHit(25, 2)); render() }
        tabs[4].setOnClickListener { selectedTab = 4; addHit(DartHit(25, 1)); render() }
        // Le mode de saisie est une vraie saisie fléchette par fléchette, pas une estimation du total.
        addHit = { hit ->
            if (hits.size < 3) {
                hits.add(hit)
                val live = if (engine != null) engine.previewTurn(hits.toList()) else null
                if (live != null && (live.bust || live.legWon)) {
                    val result = engine!!.throwTurn(hits.toList())
                    refreshX01(); saveX01Game()
                    val message = if (result.bust) "Bust : score restauré à ${result.scoreBefore}. Tour terminé." else "Leg gagné !"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    (content.parent as? android.view.ViewGroup)?.removeView(content)
                    manualEntryView = null
                    showManualEntryPrompt()
                } else render()
            }
        }
        submit.setOnClickListener {
            if (hits.size != 3) return@setOnClickListener
            try {
                val message = if (engine != null) {
                    val result = engine.throwTurn(hits.toList())
                    refreshX01(); saveX01Game()
                    when {
                        result.bust -> "Bust : le score de la volée est annulé."
                        result.legWon -> "Leg gagné !"
                        else -> "Volée enregistrée : ${result.scoreBefore} → ${result.scoreAfter}"
                    }
                } else trainingTurnHandler!!.invoke(hits.toList())
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                (content.parent as? android.view.ViewGroup)?.removeView(content)
                manualEntryView = null
                showManualEntryPrompt()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Score impossible à enregistrer.", Toast.LENGTH_LONG).show()
            }
        }
        val page = manualEntryHost ?: return
        page.removeAllViews()
        manualEntryView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        manualEntryView = content
        page.addView(content)
        render()
    }

    private fun scoreToDarts(total: Int): List<DartHit> {
        require(total in 0..180)
        if (total == 0) return List(3) { DartHit.MISS }
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
        val active = s.players[s.activePlayer]
        scoreContextView.text = active.name
        scoreView.text = active.score.toString()
        scoreTurnView.text = if (s.finished) "MATCH TERMINÉ" else if (s.legFinished) "MANCHE TERMINÉE" else "TOUR ${s.turnNumber} • 3 FLÉCHETTES"
        val opponentJson = JSONArray()
        s.players.forEach { p -> opponentJson.put(JSONObject().put("name", p.name).put("score", p.score)) }
        refreshOpponentScores(opponentJson, s.activePlayer, 0xFF9AADB8.toInt(), 0xFFF2F6F8.toInt(), 0xFF28CDBA.toInt())
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
        val active = s.optInt("active", 0).coerceIn(0, (players.length() - 1).coerceAtLeast(0))
        val player = players.optJSONObject(active)
        scoreView.text = player?.optInt("score", 301)?.toString() ?: "301"
        scoreContextView.text = player?.optString("name", "Joueur " + (active + 1)) ?: "Joueur " + (active + 1)
        scoreTurnView.text = "TOUR " + s.optInt("turn", 1) + " • 3 FLÉCHETTES"
        refreshOpponentScores(players, active, 0xFF91A4B2.toInt(), 0xFFF4F8FA.toInt(), 0xFF28D7C2.toInt())
    }

    private fun refreshOpponentScores(players: JSONArray, active: Int, muted: Int, white: Int, teal: Int) {
        if (!::opponentScoresContainer.isInitialized) return
        opponentScoresContainer.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        var opponents = 0
        for (i in 0 until players.length()) {
            if (i == active) continue
            val p = players.optJSONObject(i) ?: continue
            opponents++
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(7), dp(7), dp(4), dp(7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF1B2A35.toInt()); cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            }
            row.addView(TextView(this).apply {
                text = p.optString("name", "Joueur " + (i + 1))
                textSize = 11f; setTextColor(muted)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
            })
            row.addView(TextView(this).apply {
                text = p.optInt("score", 301).toString()
                textSize = 20f; setTextColor(white)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            opponentScoresContainer.addView(row)
        }
        if (opponents == 0) {
            opponentScoresContainer.addView(TextView(this).apply {
                text = "EN ATTENTE D’AUTRES JOUEURS"
                textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER
            })
        }
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 12000
        c.requestMethod = "GET"
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        c.setRequestProperty("Pragma", "no-cache")
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // Manifeste public du canal bêta ; le workflow de publication le met à jour.
    private val updateManifestUrl = "https://raw.githubusercontent.com/benjaminphilippot34-star/-DART-VISION/main/latest.json"
    private var downloadedUpdate: File? = null

    private fun checkForUpdates(silent: Boolean) {
        if (updateCheckRunning) return
        if (updateManifestUrl.contains("OWNER/REPO")) {
            if (!silent) AlertDialog.Builder(this)
                .setTitle("Mises à jour DART VISION")
                .setMessage("Le système de mise à jour est préparé. La publication en ligne n'est pas encore configurée.")
                .setPositiveButton("OK", null).show()
            return
        }
        updateCheckRunning = true
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
                    runOnUiThread {
                        availableUpdate = null
                        updateNavButton?.visibility = View.GONE
                        homeUpdateBanner?.visibility = View.GONE
                        updateVersionLabel?.text = "Aucune mise à jour disponible. DART VISION est à jour."
                        if (!silent) AlertDialog.Builder(this).setTitle("DART VISION est à jour").setMessage("Version installée : ${packageManager.getPackageInfo(packageName, 0).versionName}").setPositiveButton("OK", null).show()
                    }
                    return@execute
                }
                val versionName = manifest.optString("versionName", "nouvelle version")
                val apkUrl = manifest.getString("apkUrl")
                val sha256 = manifest.optString("sha256", "").lowercase()
                if (!apkUrl.startsWith("https://") || remoteCode <= 0 || versionName.isBlank()) throw IllegalStateException("Manifeste de mise à jour invalide")
                runOnUiThread {
                    availableUpdate = manifest
                    updateVersionLabel?.text = "DART VISION $versionName est disponible. Appuie ci-dessous pour télécharger la version officielle signée."
                    updateNavButton?.visibility = View.VISIBLE
                    homeUpdateBanner?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (!silent) runOnUiThread { AlertDialog.Builder(this).setTitle("Vérification impossible").setMessage("Impossible de joindre le serveur de mises à jour (${e.message ?: "erreur réseau"}). Vérifie ta connexion puis réessaie depuis PLUS > MISES À JOUR.").setPositiveButton("OK", null).show() }
            } finally {
                updateCheckRunning = false
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


private class DartboardPreview(context: android.content.Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val numbers = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
    private val colors = intArrayOf(0xFF101820.toInt(), 0xFFF2E8D5.toInt(), 0xFFB92735.toInt(), 0xFF168C67.toInt())
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = side * 0.43f
        paint.color = 0xFF080D13.toInt()
        canvas.drawCircle(cx, cy, radius * 1.08f, paint)
        val rings = floatArrayOf(0f, .10f, .48f, .54f, .88f, .94f, 1f)
        val sweep = 360f / 20f
        for (i in 0 until 20) {
            val start = -99f + i * sweep
            val base = if (i % 2 == 0) 1 else 0
            val path = android.graphics.Path()
            path.moveTo(cx, cy)
            path.arcTo(android.graphics.RectF(cx-radius, cy-radius, cx+radius, cy+radius), start, sweep, false)
            path.close()
            paint.color = colors[base]
            canvas.drawPath(path, paint)
            for (ring in 0 until rings.size - 1) {
                val inner = radius * rings[ring]
                val outer = radius * rings[ring + 1]
                val ringPath = android.graphics.Path()
                ringPath.moveTo(cx + inner * kotlin.math.cos(Math.toRadians(start.toDouble())).toFloat(), cy + inner * kotlin.math.sin(Math.toRadians(start.toDouble())).toFloat())
                ringPath.arcTo(android.graphics.RectF(cx-outer, cy-outer, cx+outer, cy+outer), start, sweep, false)
                ringPath.arcTo(android.graphics.RectF(cx-inner, cy-inner, cx+inner, cy+inner), start+sweep, -sweep, false)
                ringPath.close()
                paint.color = when (ring) {
                    2, 4 -> if (i % 2 == 0) colors[2] else colors[3]
                    else -> if ((i + ring) % 2 == 0) colors[0] else colors[1]
                }
                canvas.drawPath(ringPath, paint)
            }
            paint.color = 0xFFB8C7C7.toInt()
            paint.strokeWidth = side * .003f
            canvas.drawLine(
                cx + radius * .10f * kotlin.math.cos(Math.toRadians(start.toDouble())).toFloat(),
                cy + radius * .10f * kotlin.math.sin(Math.toRadians(start.toDouble())).toFloat(),
                cx + radius * kotlin.math.cos(Math.toRadians(start.toDouble())).toFloat(),
                cy + radius * kotlin.math.sin(Math.toRadians(start.toDouble())).toFloat(), paint
            )
            paint.color = 0xFFF3F5F4.toInt()
            paint.textSize = side * .045f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            val angle = Math.toRadians((start + sweep/2).toDouble())
            canvas.drawText(numbers[i].toString(), cx + radius*1.045f*kotlin.math.cos(angle).toFloat(), cy + radius*1.045f*kotlin.math.sin(angle).toFloat() + paint.textSize*.35f, paint)
        }
        paint.color = 0xFF168C67.toInt()
        canvas.drawCircle(cx, cy, radius*.095f, paint)
        paint.color = 0xFFB92735.toInt()
        canvas.drawCircle(cx, cy, radius*.045f, paint)
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
