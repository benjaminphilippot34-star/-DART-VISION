package com.dartvision.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.SharedPreferences
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

/** Original illustrated DART VISION avatar portraits, drawn locally without remote assets. */
private class DartAvatarPortraitView(context: android.content.Context, private val style: Int) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val palettes = arrayOf(
        intArrayOf(0xFF182D43.toInt(), 0xFF20D9D2.toInt()),
        intArrayOf(0xFF24172F.toInt(), 0xFFFF4058.toInt()),
        intArrayOf(0xFF102D35.toInt(), 0xFF31E6C4.toInt()),
        intArrayOf(0xFF20263B.toInt(), 0xFF5AA9FF.toInt()),
        intArrayOf(0xFF332018.toInt(), 0xFFFF914D.toInt()),
        intArrayOf(0xFF28233A.toInt(), 0xFFFFD166.toInt())
    )
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat(); val h=height.toFloat()
        val cx=w/2f; val cy=h/2f; val r=minOf(w,h)*.47f
        val accent=palettes[style%palettes.size][1]
        val skin=when(style%5) {
            0 -> 0xFFD08A63.toInt()
            1 -> 0xFF9A604C.toInt()
            2 -> 0xFFE0A47D.toInt()
            3 -> 0xFFB97858.toInt()
            else -> 0xFF8D5847.toInt()
        }
        paint.shader=android.graphics.RadialGradient(cx-r*.15f,cy-r*.25f,r*1.5f,
            intArrayOf(palettes[style%palettes.size][0],0xFF060B13.toInt()),
            null,android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,r,paint); paint.shader=null
        paint.style=android.graphics.Paint.Style.STROKE
        paint.strokeWidth=maxOf(2f,r*.045f); paint.color=accent
        paint.setShadowLayer(r*.13f,0f,0f,accent); canvas.drawCircle(cx,cy,r*.94f,paint)
        paint.clearShadowLayer(); paint.style=android.graphics.Paint.Style.FILL

        // Epaules et veste esport
        val jacket=android.graphics.Path().apply {
            moveTo(cx-r*.72f,cy+r*.78f); lineTo(cx-r*.58f,cy+r*.42f)
            quadTo(cx-r*.42f,cy+r*.26f,cx-r*.18f,cy+r*.30f)
            lineTo(cx+r*.18f,cy+r*.30f); quadTo(cx+r*.42f,cy+r*.26f,cx+r*.58f,cy+r*.42f)
            lineTo(cx+r*.72f,cy+r*.78f); close()
        }
        paint.color=0xFF080F19.toInt(); canvas.drawPath(jacket,paint)
        paint.color=accent; paint.strokeWidth=r*.045f
        canvas.drawLine(cx-r*.48f,cy+r*.49f,cx-r*.28f,cy+r*.78f,paint)
        canvas.drawLine(cx+r*.48f,cy+r*.49f,cx+r*.28f,cy+r*.78f,paint)

        // Cou et visage
        paint.color=skin
        canvas.drawRoundRect(cx-r*.17f,cy+r*.25f,cx+r*.17f,cy+r*.57f,r*.08f,r*.08f,paint)
        canvas.drawOval(cx-r*.40f,cy-r*.49f,cx+r*.40f,cy+r*.48f,paint)
        // Cheveux / capuche, variations selon le personnage
        paint.color=0xFF10131A.toInt()
        when(style%4) {
            0 -> {
                val hair=android.graphics.Path().apply {
                    moveTo(cx-r*.43f,cy-r*.12f); quadTo(cx-r*.55f,cy-r*.68f,cx-r*.06f,cy-r*.72f)
                    quadTo(cx+r*.18f,cy-r*.84f,cx+r*.43f,cy-r*.42f)
                    lineTo(cx+r*.35f,cy-r*.27f); quadTo(cx,cy-r*.43f,cx-r*.43f,cy-r*.12f); close()
                }; canvas.drawPath(hair,paint)
            }
            1 -> canvas.drawRoundRect(cx-r*.46f,cy-r*.62f,cx+r*.46f,cy-r*.24f,r*.12f,r*.12f,paint)
            2 -> canvas.drawOval(cx-r*.47f,cy-r*.70f,cx+r*.47f,cy-r*.25f,paint)
            else -> {
                canvas.drawOval(cx-r*.48f,cy-r*.66f,cx+r*.48f,cy-r*.22f,paint)
                canvas.drawRect(cx-r*.47f,cy-r*.43f,cx-r*.34f,cy+r*.18f,paint)
                canvas.drawRect(cx+r*.34f,cy-r*.43f,cx+r*.47f,cy+r*.18f,paint)
            }
        }
        // Oreilles
        paint.color=skin
        canvas.drawCircle(cx-r*.40f,cy+r*.02f,r*.085f,paint)
        canvas.drawCircle(cx+r*.40f,cy+r*.02f,r*.085f,paint)
        // Yeux et sourcils
        paint.color=0xFF17151A.toInt()
        paint.strokeWidth=r*.055f; paint.strokeCap=android.graphics.Paint.Cap.ROUND
        canvas.drawLine(cx-r*.25f,cy-r*.08f,cx-r*.08f,cy-r*.10f,paint)
        canvas.drawLine(cx+r*.08f,cy-r*.10f,cx+r*.25f,cy-r*.08f,paint)
        paint.color=0xFFF5FBFF.toInt()
        canvas.drawOval(cx-r*.23f,cy-r*.035f,cx-r*.09f,cy+r*.045f,paint)
        canvas.drawOval(cx+r*.09f,cy-r*.035f,cx+r*.23f,cy+r*.045f,paint)
        paint.color=0xFF10151E.toInt()
        canvas.drawCircle(cx-r*.16f,cy+.005f*r,r*.035f,paint)
        canvas.drawCircle(cx+r*.16f,cy+.005f*r,r*.035f,paint)
        // Barbe légère et masque signature neon
        if(style%3==0) {
            paint.color=0xFF34252A.toInt()
            canvas.drawOval(cx-r*.29f,cy+r*.17f,cx+r*.29f,cy+r*.48f,paint)
        }
        paint.color=accent
        paint.alpha=225
        canvas.drawRoundRect(cx-r*.30f,cy+r*.23f,cx+r*.30f,cy+r*.31f,r*.035f,r*.035f,paint)
        paint.alpha=255
        // Reflet lumineux sur la veste
        paint.style=android.graphics.Paint.Style.STROKE
        paint.strokeWidth=r*.025f
        paint.color=0xFFE8FCFF.toInt()
        canvas.drawArc(cx-r*.88f,cy-r*.88f,cx+r*.88f,cy+r*.88f,205f,55f,false,paint)
        paint.style=android.graphics.Paint.Style.FILL
        paint.strokeCap=android.graphics.Paint.Cap.BUTT
    }
}


/** Original neon dartboard artwork drawn natively for the DART VISION home screen. */
private class DartNeonArtworkView(context: android.content.Context, private val kind: Int = 0) : View(context) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val s=minOf(width,height).toFloat(); val cx=width/2f; val cy=height/2f
        p.style=android.graphics.Paint.Style.FILL; p.color=0xFF071018.toInt()
        canvas.drawCircle(cx,cy,s*.48f,p)
        p.style=android.graphics.Paint.Style.STROKE; p.strokeWidth=s*.025f
        p.color=0xFFFF1E1E.toInt(); p.setShadowLayer(s*.10f,0f,0f,0xFFFF203D.toInt())
        canvas.drawCircle(cx,cy,s*.46f,p); p.clearShadowLayer()
        if(kind==0 || kind==2) {
            val rings=intArrayOf(0xFF00F0FF.toInt(),0xFFFF1E1E.toInt(),0xFFF3F5F7.toInt(),0xFF00F0FF.toInt(),0xFFFF1E1E.toInt())
            rings.forEachIndexed { i,col -> p.color=col; p.strokeWidth=s*(if(i==0) .045f else .025f); canvas.drawCircle(cx,cy,s*(.37f-i*.067f),p) }
            p.style=android.graphics.Paint.Style.FILL; p.color=0xFFFF1E1E.toInt(); canvas.drawCircle(cx,cy,s*.055f,p)
            p.color=0xFF00F0FF.toInt(); canvas.drawCircle(cx,cy,s*.022f,p)
            p.strokeWidth=s*.035f; p.strokeCap=android.graphics.Paint.Cap.ROUND; p.color=0xFFEAFBFF.toInt()
            canvas.drawLine(cx+s*.02f,cy-s*.02f,cx+s*.32f,cy-s*.38f,p)
            p.style=android.graphics.Paint.Style.FILL
            val dart=android.graphics.Path().apply { moveTo(cx+s*.28f,cy-s*.42f); lineTo(cx+s*.47f,cy-s*.48f); lineTo(cx+s*.39f,cy-s*.30f); close() }
            p.color=0xFF00F0FF.toInt(); canvas.drawPath(dart,p)
            p.color=0xFFFF1E1E.toInt(); canvas.drawCircle(cx+s*.02f,cy-s*.02f,s*.04f,p)
        } else {
            p.style=android.graphics.Paint.Style.STROKE; p.strokeWidth=s*.065f
            p.color=if(kind==1) 0xFF00F0FF.toInt() else 0xFFFF1E1E.toInt()
            val r=s*.25f; canvas.drawCircle(cx,cy,r,p); canvas.drawLine(cx-r*.9f,cy,cx+r*.9f,cy,p)
            canvas.drawLine(cx,cy-r*.9f,cx,cy+r*.9f,p)
            canvas.drawArc(cx-r*.7f,cy-r*.7f,cx+r*.7f,cy+r*.7f,210f,120f,false,p)
        }
        p.clearShadowLayer(); p.strokeCap=android.graphics.Paint.Cap.BUTT; p.style=android.graphics.Paint.Style.FILL
    }
}

/** Fond turquoise clair et trame géométrique discrète de la maquette validée. */
private class DartNeonBackgroundDrawable : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        // Dégradé doux menthe / turquoise très clair.
        paint.style = android.graphics.Paint.Style.FILL
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, w * .18f, h,
            intArrayOf(0xFFC8E7E2.toInt(), 0xFFE8F5F2.toInt(), 0xFFF7FBF9.toInt()),
            floatArrayOf(0f, .52f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, paint)
        paint.shader = null

        // Trame fine, presque ton sur ton : lignes diagonales et points légers.
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, resourcesDensity() * .65f)
        paint.color = 0x183A8F88
        val step = 34f * resourcesDensity()
        var x = -h
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h, h, paint)
            x += step
        }
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = 0x183A8F88
        val dotStep = 26f * resourcesDensity()
        var y = dotStep
        while (y < h) {
            var dx = dotStep
            while (dx < w) {
                canvas.drawCircle(dx, y, 0.8f * resourcesDensity(), paint)
                dx += dotStep
            }
            y += dotStep
        }
        paint.alpha = 255
    }
    private fun resourcesDensity(): Float = android.content.res.Resources.getSystem().displayMetrics.density
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var scoreView: TextView
    private lateinit var scoreContextView: TextView
    private lateinit var scoreTurnView: TextView
    private lateinit var playerStatsView: TextView
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
    private var gameScoreCardView: View? = null
    private var gameHeadingView: View? = null
    private var gameCameraShortcutView: View? = null
    private var gameNextActionsView: View? = null
    private var manualEntryHost: LinearLayout? = null
    private var manualEntryView: LinearLayout? = null
    private var selectedMode: DartGameMode = DartGameMode.X01
    private var selectedDartMultiplier: Int = 1
    private var x01Engine: X01GameEngine? = null
    private var x01AccountParticipant: Boolean = false
    private var x01HistorySaved: Boolean = false
    private var x01StartedAt: String = java.time.Instant.now().toString()
    private var trainingTurnHandler: ((List<DartHit>) -> String)? = null
    private var availableUpdate: JSONObject? = null
    private var updateNavButton: Button? = null
    private var updateVersionLabel: TextView? = null
    private var updatePageView: LinearLayout? = null
    private var homeUpdateBanner: LinearLayout? = null
    private var updateCheckRunning = false
    private var awaitingInstallPermission = false
    private var gameInProgress = false
    private var currentPage = "home"
    private var bottomNavigationBar: View? = null
    private var profileAvatarPreview: ImageView? = null
    private var navigateHomeFromPage: (() -> Unit)? = null
    private var gameExitPrompt: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadBoardCalibration()
        startLocalGame()
        restoreX01Game()
        handleAuthCallback(intent)
        syncAccountProfile()
    }

    override fun onResume() {
        super.onResume()
        val pendingApk = downloadedUpdate
        if (awaitingInstallPermission && pendingApk != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            awaitingInstallPermission = false
            launchApkInstaller(pendingApk)
        } else {
            checkForUpdates(silent = true)
        }
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
                    accountPreferences().edit()
                        .putString("user_id", user.userId).putString("email", user.email)
                        .putString("access_token", accessToken).putString("refresh_token", values["refresh_token"]).apply()
                    Toast.makeText(this, "Adresse confirmée. Compte connecté.", Toast.LENGTH_LONG).show()
                    syncAccountProfile()
                    showAuthenticatedHome()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message ?: "Validation du compte impossible.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun buildUi() {
        val bg = 0xFFE8F5F2.toInt()
        val panelColor = 0xFFFFFFFF.toInt()
        val teal = 0xFF079BA5.toInt()
        val white = 0xFF173331.toInt()
        val muted = 0xFF687F7D.toInt()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun txt(value: String, size: Float, color: Int = white, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(2), dp(3), dp(2), dp(3))
                setTypeface(android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL))
            }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F8F6.toInt())
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFFD5E7E3.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
        }
        fun btn(row: LinearLayout, title: String, primary: Boolean = false, click: () -> Unit) {
            row.addView(Button(this).apply {
                text = title; isAllCaps = false; textSize = 12f
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else white)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (primary) 0xFFE94B4B.toInt() else 0xFFFFFFFF.toInt())
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), if (primary) 0xFFE94B4B.toInt() else 0xFFD5E7E3.toInt())
                }
                minHeight = dp(46); stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener { click() }
            })
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; setBackgroundColor(0xFF031016.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4)); setBackgroundColor(0xFF031016.toInt())
        }
        scroll.addView(root)
        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val gamePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gamePageView = gamePage
        val playersPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val morePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accountPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val discussionsPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val updatePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        updatePageView = updatePage
        val hasAccountAtStart = accountPreferences().getString("user_id", null) != null
        val navItems = mutableMapOf<String, View>()
        fun showPage(page: String) {
            currentPage = page
            home.visibility = if (page == "home") View.VISIBLE else View.GONE
            gamePage.visibility = if (page == "game") View.VISIBLE else View.GONE
            playersPage.visibility = if (page == "players") View.VISIBLE else View.GONE
            morePage.visibility = if (page == "more") View.VISIBLE else View.GONE
            accountPage.visibility = if (page == "account") View.VISIBLE else View.GONE
            discussionsPage.visibility = if (page == "discussions") View.VISIBLE else View.GONE
            updatePage.visibility = if (page == "update") View.VISIBLE else View.GONE
            bottomNavigationBar?.visibility = if (page == "game" && gameInProgress) View.GONE else View.VISIBLE
            navItems["home"]?.let { v ->
                v.background = if (page == "home") android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF092B35.toInt()); cornerRadius = dp(13).toFloat(); setStroke(dp(1), 0xFF00E5F2.toInt())
                } else android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            }
            val enteringPage = when (page) {
                "home" -> home
                "game" -> gamePage
                "players" -> playersPage
                "more" -> morePage
                "account" -> accountPage
                "discussions" -> discussionsPage
                "update" -> updatePage
                else -> home
            }
            enteringPage.animate().cancel()
            enteringPage.alpha = 0.0f
            enteringPage.translationY = dp(10).toFloat()
            enteringPage.animate().alpha(1.0f).translationY(0f).setDuration(220L).start()
        }
        navigateToMorePage = { showPage("more") }
        navigateHomeFromPage = { showPage("home") }
        // En-tête de l'accueil — reproduit uniquement la bande supérieure de la maquette validée.
        window.statusBarColor = 0xFF031016.toInt()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        val header = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xFF031016.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(108)).apply {
                bottomMargin = dp(1)
            }
            clipChildren = false
            clipToPadding = false
        }
        val menuAction = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(42), dp(46), Gravity.CENTER_VERTICAL or Gravity.START)
            isClickable = true
            isFocusable = true
            contentDescription = "Menu"
            setOnClickListener { showPage("more") }
        }
        menuAction.addView(TextView(this).apply {
            text = "☰"
            textSize = 27f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(0xFFF4FCFF.toInt())
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        })
        menuAction.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFF9D16.toInt())
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(5)
                rightMargin = dp(3)
            }
        })
        header.addView(menuAction)

        // Le fichier logo complet occupe seul le centre : aucun texte DART VISION en doublon.
        val brand = ImageView(this).apply {
            setImageResource(R.drawable.dart_vision_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Logo officiel DART VISION"
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(226), dp(98), Gravity.CENTER)
        }
        header.addView(brand)

        val notificationAction = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(42), dp(46), Gravity.CENTER_VERTICAL or Gravity.END)
            isClickable = true
            isFocusable = true
            contentDescription = "Notifications"
            setOnClickListener { showPage("update") }
        }
        notificationAction.addView(object : View(this) {
            private val bellPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF4FCFF.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val bell = android.graphics.Path().apply {
                    moveTo(w * .50f, h * .08f)
                    cubicTo(w * .28f, h * .08f, w * .20f, h * .27f, w * .20f, h * .48f)
                    cubicTo(w * .20f, h * .66f, w * .13f, h * .75f, w * .08f, h * .81f)
                    quadTo(w * .05f, h * .88f, w * .15f, h * .88f)
                    lineTo(w * .85f, h * .88f)
                    quadTo(w * .95f, h * .88f, w * .92f, h * .81f)
                    cubicTo(w * .87f, h * .75f, w * .80f, h * .66f, w * .80f, h * .48f)
                    cubicTo(w * .80f, h * .27f, w * .72f, h * .08f, w * .50f, h * .08f)
                    close()
                }
                canvas.drawPath(bell, bellPaint)
                canvas.drawRoundRect(w * .39f, h * .90f, w * .61f, h, w * .11f, h * .11f, bellPaint)
            }
        }.apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(25), dp(28), Gravity.CENTER)
        })
        notificationAction.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFF3045.toInt())
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(9), dp(9), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(5)
                rightMargin = dp(4)
            }
        })
        header.addView(notificationAction)
        root.addView(header)
        root.addView(View(this).apply {
            setBackgroundColor(0xFF00EAF4.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                bottomMargin = dp(5)
            }
        })

        // ÉCRAN 1 — ACCUEIL : première étape de la refonte fidèle à la maquette validée.
        home.setBackgroundColor(0xFF031016.toInt())
        home.setPadding(dp(8), dp(2), dp(8), dp(5))

        val profileStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF102B35.toInt(), 0xFF06151D.toInt())
            ).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(2), 0xFF00EAF4.toInt())
            }
            elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, dp(100)).apply {
                setMargins(dp(2), dp(4), dp(2), dp(10))
            }
        }
        profileStrip.addView(DartAvatarPortraitView(this, 0).apply {
            layoutParams = LinearLayout.LayoutParams(dp(90), dp(90))
            contentDescription = "Avatar du joueur"
        })
        val profileInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        profileInfo.addView(TextView(this).apply {
            text = accountPreferences().getString("display_name", "Benjamin Philippot")
            textSize = 19f
            maxLines = 1
            setTextColor(0xFFF4FCFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        })
        profileInfo.addView(TextView(this).apply {
            text = "▮▮▮  1 245  ⭐"
            textSize = 15f
            setTextColor(0xFFFFD34E.toInt())
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(4))
        })
        profileInfo.addView(TextView(this).apply {
            text = "🟢 10 836 en ligne"
            textSize = 11f
            setTextColor(0xFF32E58C.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        profileStrip.addView(profileInfo)
        val profileButton = TextView(this).apply {
            text = "♟  Mon profil"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF008DFF.toInt(), 0xFF064DCE.toInt())
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(2), 0xFF00EAF4.toInt())
            }
            elevation = dp(8).toFloat()
            isClickable = true
            setOnClickListener { showPage("account") }
        }
        profileStrip.addView(profileButton)
        home.addView(profileStrip)

        val primaryMenuRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun addPrimaryMenu(title: String, subtitle: String, accent: Int, artworkKind: Int, action: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    if (title == "PARTIE LOCALE")
                        intArrayOf(0xFFE92335.toInt(), 0xFF8E1425.toInt(), 0xFF170D17.toInt())
                    else
                        intArrayOf(0xFF00AAB8.toInt(), 0xFF07515E.toInt(), 0xFF07151D.toInt())
                ).apply {
                    cornerRadius = dp(23).toFloat()
                    setStroke(dp(2), accent)
                }
                elevation = dp(9).toFloat()
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(122), 1f).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(8))
                }
                setOnClickListener { action() }
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            labels.addView(TextView(this).apply {
                text = if (title == "PARTIE LOCALE") "PARTIE\nLOCALE" else "PARTIE\nEN LIGNE"
                textSize = 20f
                gravity = Gravity.START
                setTextColor(0xFFFFFFFF.toInt())
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD_ITALIC)
            })
            labels.addView(TextView(this).apply {
                text = if (title == "PARTIE LOCALE") "Jouer sur un seul\ntéléphone ou en\nréseau local." else "Défier des joueurs\nà distance et suivre\ndes matchs en direct."
                textSize = 12f
                gravity = Gravity.START
                setTextColor(0xFFF1F7FA.toInt())
                setPadding(0, dp(4), 0, 0)
            })
            tile.addView(labels)
            tile.addView(DartNeonArtworkView(this, artworkKind).apply {
                layoutParams = LinearLayout.LayoutParams(dp(66), dp(76))
                contentDescription = title
            })
            primaryMenuRow.addView(tile)
        }
        addPrimaryMenu("PARTIE LOCALE", "Jouer sur cet appareil\nou en réseau local", 0xFFFF394A.toInt(), 1) {
            if (restoreX01Game()) {
                gameInProgress = true
                showPage("game")
                showRestoredGameScreen()
            } else {
                showPage("game"); showGameModes()
            }
        }
        addPrimaryMenu("PARTIE EN LIGNE", "Défier des joueurs\nà distance", 0xFF00E5F2.toInt(), 3) {
            showPage("players")
        }
        home.addView(primaryMenuRow)

        fun addWideMenu(title: String, subtitle: String, accent: Int, icon: String, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF102C36.toInt(), 0xFF061018.toInt())
                ).apply {
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(2), accent)
                }
                elevation = dp(8).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1, dp(78)).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(7))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            row.addView(TextView(this).apply {
                text = icon
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(accent)
                layoutParams = LinearLayout.LayoutParams(dp(64), -1)
            })
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 21f
                setTextColor(0xFFF5FCFF.toInt())
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD_ITALIC)
            })
            labels.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(0xFFE4F3F6.toInt())
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(labels)
            row.addView(TextView(this).apply {
                text = when (title) {
                    "ENTRAÎNEMENT" -> "🎯"
                    "MODE CLASSÉ" -> "🏆"
                    else -> "🎯"
                }
                textSize = 34f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(66), -1)
            })
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
            })
            home.addView(row)
        }
        addWideMenu("ENTRAÎNEMENT", "Travailler les simples, doubles, triples et finitions", 0xFF00E5F2.toInt(), "◎") {
            showPage("game"); showTrainingModes()
        }
        addWideMenu("MODE CLASSÉ", "Compétition, classement et progression", 0xFFFFC247.toInt(), "🏆") {
            showPage("game"); showGameModes()
        }
        addWideMenu("TOURNOIS", "Participer à des tournois en ligne ou locaux", 0xFFB45BFF.toInt(), "♜") {
            showInsightScreen("TOURNOIS", "COMPÉTITIONS DART VISION", emptyList(),
                listOf("Tournois" to "L'espace tournois sera disponible ici."))
        }


        val quickLinks = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }
        fun quick(label: String, icon: String, action: () -> Unit) {
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF101D25.toInt(), 0xFF03090E.toInt())
                ).apply {
                    cornerRadius = dp(19).toFloat()
                    setStroke(dp(2), if (label == "AMIS") 0xFF00F0FF.toInt() else 0xFFFF1E1E.toInt())
                }
                elevation = dp(6).toFloat()
                layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                isClickable = true
                setOnClickListener { action() }
            }
            b.addView(TextView(this).apply { text = icon; textSize = 25f; gravity = Gravity.CENTER; setTextColor(0xFFF2FBFF.toInt()) })
            b.addView(TextView(this).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(0xFFF2FBFF.toInt()) })
            quickLinks.addView(b)
        }
        quick("STATISTIQUES", "▥") { showAccountHistory() }
        quick("AMIS", "♟") { showPage("discussions") }
        quick("SUCCÈS", "★") {
            showInsightScreen("SUCCÈS", "TES OBJECTIFS DART VISION", emptyList(),
                listOf("Progression" to "Tes performances et tes réussites apparaîtront ici."))
        }
        home.addView(quickLinks)

        homeUpdateBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(12), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF241116.toInt()); cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFFE32636.toInt())
            }
            visibility = View.GONE
        }
        val updateBannerTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val updateBannerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        updateBannerText.addView(txt("MISE À JOUR DISPONIBLE", 13f, 0xFFFF1E1E.toInt(), true))
        updateBannerText.addView(txt("Une nouvelle version de DART VISION est prête.", 10f, muted))
        updateBannerTop.addView(updateBannerText)
        val closeUpdateBanner = TextView(this).apply {
            text = "×"; textSize = 25f; setTextColor(white); gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(4), 0); setOnClickListener { homeUpdateBanner?.visibility = View.GONE }
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

        // L’inscription et la connexion disposent de leur propre onglet.
        accountPage.addView(buildAccountPanel())
        discussionsPage.addView(txt("DISCUSSIONS", 20f, white, true))
        discussionsPage.addView(txt("Retrouve bientôt ici tes échanges avec les autres joueurs.", 13f, muted))
        val discussionCard = card()
        discussionCard.addView(txt("Un espace pour partager tes parties, échanger des conseils et discuter avec tes amis.", 12f, white))
        discussionCard.addView(txt("Les discussions seront activées dans une prochaine version.", 10f, teal, true))
        discussionsPage.addView(discussionCard)
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
        gameHeading.visibility = View.GONE
        gameHeadingView = gameHeading
        gamePage.addView(gameHeading)

        val scoreCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF121D27.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFF8F2633.toInt())
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
        playerStatsView = txt("", 10f, white, true).apply {
            setPadding(0, dp(9), 0, 0)
            maxLines = 3
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        activePlayerPanel.addView(playerStatsView)
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
        scoreCard.visibility = View.GONE
        gameScoreCardView = scoreCard
        gamePage.addView(scoreCard)

        manualEntryHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gamePage.addView(manualEntryHost)
        showManualEntryPrompt()
        val cameraShortcut = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btn(cameraShortcut, "📹 CAMÉRA", false) {
            cameraPanelView?.let { panel ->
                (panel.parent as? android.view.ViewGroup)?.removeView(panel)
                panel.visibility = View.VISIBLE
                gamePage.addView(panel)
            }
            setCameraMode(true)
        }
        gamePage.addView(cameraShortcut)
        gameCameraShortcutView = cameraShortcut
        val next = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(next, "TOUR SUIVANT", true) { action("next_turn") }
        btn(next, "NOUVELLE PARTIE") { if (x01Engine != null) configureX01() else showGameModes() }
        gamePage.addView(next)
        gameNextActionsView = next

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
        morePage.addView(TextView(this).apply {
            text = "PARAMÈTRES"
            textSize = 24f
            gravity = Gravity.CENTER
            letterSpacing = .05f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD_ITALIC)
            setTextColor(white)
            setPadding(0, dp(7), 0, dp(2))
        })
        morePage.addView(TextView(this).apply {
            text = "—  PERSONNALISE TON EXPÉRIENCE  —"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = .12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(0xFF079BA5.toInt())
            setPadding(0, 0, 0, dp(14))
        })
        morePage.addView(txt("CAMÉRA & INSTALLATION", 11f, 0xFF00F0FF.toInt(), true))
        val cameraPanel = card().apply { elevation = dp(5).toFloat() }
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
        val settings = card().apply { elevation = dp(5).toFloat() }
        settings.addView(txt("GESTION DE LA PARTIE", 12f, 0xFFFF1E1E.toInt(), true))
        btn(settings, "■  ARRÊTER LA PARTIE", false) {
            if (!gameInProgress) {
                AlertDialog.Builder(this).setTitle("Aucune partie en cours")
                    .setMessage("Aucune partie active à arrêter.")
                    .setPositiveButton("OK", null).show()
            } else {
                AlertDialog.Builder(this).setTitle("Arrêter la partie ?")
                    .setMessage("Veux-tu sauvegarder cette partie pour la reprendre plus tard ?")
                    .setPositiveButton("SAUVEGARDER") { _, _ ->
                        x01Engine?.let { saveX01Game() }
                        saveLocalState(state.get())
                        gameInProgress = false; x01Engine = null; trainingTurnHandler = null
                        bottomNavigationBar?.visibility = View.VISIBLE
                        status.text = "PARTIE SAUVEGARDÉE • Reprise possible depuis JOUER."
                        showPage("home")
                    }
                    .setNegativeButton("ARRÊTER SANS SAUVEGARDER") { _, _ ->
                        getSharedPreferences("dart_vision_local", MODE_PRIVATE).edit()
                            .remove("x01_state").remove("game_state").apply()
                        gameInProgress = false; x01Engine = null; trainingTurnHandler = null
                        bottomNavigationBar?.visibility = View.VISIBLE
                        status.text = "PARTIE ARRÊTÉE SANS SAUVEGARDE."
                        showPage("home")
                    }
                    .setNeutralButton("ANNULER", null).show()
            }
        }
        status = txt("PRÊT À JOUER", 12f, teal, true)
        settings.addView(txt("ÉTAT DE L’APPLICATION", 10f, muted, true)); settings.addView(status)
        val settingButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btn(settingButtons, "MISES À JOUR") { checkForUpdates(silent = false) }
        btn(settingButtons, "RÉGLAGES") { showAppSettings() }
        settings.addView(settingButtons); morePage.addView(settings)

        gameExitPrompt = {
            AlertDialog.Builder(this).setTitle("Quitter la partie ?")
                .setMessage("Tu peux sauvegarder la partie pour la reprendre plus tard, l'abandonner sans sauvegarde ou continuer à jouer.")
                .setPositiveButton("SAUVEGARDER ET QUITTER") { _, _ ->
                    x01Engine?.let { saveX01Game() }
                    saveLocalState(state.get())
                    gameInProgress = false
                    trainingTurnHandler = null
                    bottomNavigationBar?.visibility = View.VISIBLE
                    showPage("home")
                    Toast.makeText(this, "Partie sauvegardée.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("ABANDONNER") { _, _ ->
                    getSharedPreferences("dart_vision_local", MODE_PRIVATE).edit()
                        .remove("x01_state").remove("game_state").apply()
                    gameInProgress = false
                    x01Engine = null
                    trainingTurnHandler = null
                    bottomNavigationBar?.visibility = View.VISIBLE
                    showPage("home")
                    Toast.makeText(this, "Partie abandonnée sans sauvegarde.", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("CONTINUER", null)
                .show()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPage != "home") {
                    if (currentPage == "game" && gameInProgress) gameExitPrompt?.invoke()
                    else navigateHomeFromPage?.invoke()
                } else if (gameInProgress) {
                    gameExitPrompt?.invoke()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        root.addView(home); root.addView(gamePage); root.addView(playersPage); root.addView(morePage); root.addView(accountPage); root.addView(discussionsPage); root.addView(updatePage)

        // Navigation principale : même ordre et mêmes intitulés que la maquette validée.
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF041016.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(2), 0xFF00DDEB.toInt())
            }
            elevation = dp(8).toFloat()
        }
        fun addNavItem(key: String, label: String, symbol: String, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            item.addView(TextView(this).apply {
                text = symbol
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(0xFFEAFBFF.toInt())
            })
            item.addView(TextView(this).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(0xFFEAFBFF.toInt())
            })
            navItems[key] = item
            navBar.addView(item)
        }
        addNavItem("home", "Accueil", "⌂") { showPage("home") }
        addNavItem("training", "Entraînement", "◎") { showPage("game"); showTrainingModes() }
        addNavItem("players", "En ligne", "◎") { showPage("players") }
        addNavItem("tournaments", "Tournois", "♜") {
            showInsightScreen("TOURNOIS", "COMPÉTITIONS DART VISION", emptyList(),
                listOf("Tournois" to "L'espace tournois sera disponible ici."))
        }
        addNavItem("stats", "Stats", "▥") { showAccountHistory() }
        bottomNavigationBar = navBar

        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(navBar, LinearLayout.LayoutParams(-1, dp(68)))
        showPage("home")
        setContentView(shell)
        // Android 15+ dessine l'application derrière la barre système :
        // réserver sa hauteur sous la navigation pour garder les onglets entièrement visibles.
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val statusInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // Keep the app header below Android's status bar and the content above navigation.
            view.setPadding(0, statusInset, 0, navigationInset)
            insets
        }
        ViewCompat.requestApplyInsets(shell)
    }

    private fun buildAccountPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(16))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F8F6.toInt())
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFFD5E7E3.toInt())
            }
            elevation = dp(4).toFloat()
        }
        fun sectionTitle(value: String) {
            panel.addView(TextView(this).apply {
                text = value
                textSize = 11f
                letterSpacing = .12f
                setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
                setTextColor(0xFF079BA5.toInt())
                setPadding(0, dp(13), 0, dp(7))
            })
        }
        fun field(hintText: String, inputTypeValue: Int, secret: Boolean = false): EditText {
            return EditText(this).apply {
                hint = hintText
                inputType = inputTypeValue
                setSingleLine(true)
                textSize = 14f
                setTextColor(0xFF173331.toInt())
                setHintTextColor(0xFF819390.toInt())
                if (secret) transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                setPadding(dp(13), dp(11), dp(13), dp(11))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF080F17.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), 0xFFD5E7E3.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-1, dp(49)).apply {
                    bottomMargin = dp(9)
                }
            }
        }
        fun actionButton(label: String, primary: Boolean = false, action: () -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                setTextColor(android.graphics.Color.WHITE)
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (primary) 0xFFE94B4B.toInt() else 0xFFFFFFFF.toInt())
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), if (primary) 0xFFE94B4B.toInt() else 0xFFD5E7E3.toInt())
                }
                stateListAnimator = null
                minHeight = dp(48)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener { action() }
            }
        }

        panel.addView(TextView(this).apply {
            text = "MON COMPTE"
            textSize = 23f
            gravity = Gravity.CENTER
            letterSpacing = .04f
            setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD_ITALIC))
            setTextColor(0xFF173331.toInt())
            setPadding(0, dp(2), 0, dp(3))
        })
        panel.addView(TextView(this).apply {
            text = "—  TON ESPACE JOUEUR  —"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = .15f
            setTextColor(0xFF079BA5.toInt())
            setPadding(0, 0, 0, dp(13))
        })

        // Carte profil responsive : les actions occupent une ligne dédiée
        // afin que leurs libellés ne soient jamais rognés sur les petits écrans.
        val avatarCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFF4EF.toInt(), 0xFFFFE8DD.toInt())
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), 0xFFFF1E1E.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
        }
        val avatarIdentityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        profileAvatarPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Photo de profil ou avatar"
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFFE8DD.toInt())
                setStroke(dp(2), 0xFFE94B4B.toInt())
            }
            clipToOutline = true
        }
        avatarIdentityRow.addView(profileAvatarPreview)
        val avatarInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        avatarInfo.addView(TextView(this).apply {
            text = "PROFIL JOUEUR"
            textSize = 15f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(0xFF173331.toInt())
        })
        avatarInfo.addView(TextView(this).apply {
            text = "Personnalise ton identité sur DART VISION"
            textSize = 11f
            setTextColor(0xFF687F7D.toInt())
            setPadding(0, dp(4), 0, 0)
            maxLines = 2
        })
        avatarIdentityRow.addView(avatarInfo, LinearLayout.LayoutParams(0, -2, 1f))
        avatarIdentityRow.addView(DartNeonArtworkView(this, 0).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            alpha = .92f
            contentDescription = "Cible de fléchettes lumineuse"
        })
        avatarCard.addView(avatarIdentityRow)

        val avatarActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        fun compactAvatarButton(label: String, action: () -> Unit): Button =
            actionButton(label, false, action).apply {
                textSize = 11f
                maxLines = 2
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = 0
                minHeight = dp(44)
                setPadding(dp(4), dp(5), dp(4), dp(5))
            }
        avatarActions.addView(compactAvatarButton("AJOUTER UNE PHOTO") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 741)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
        avatarActions.addView(compactAvatarButton("CHOISIR UN AVATAR") {
            showAvatarGallery()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(5) })
        avatarCard.addView(avatarActions)
        panel.addView(avatarCard)
        refreshProfileAvatar()

        sectionTitle("IDENTIFIANTS")
        val email = field("Adresse e-mail", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        email.setText(accountPreferences().getString("email", "").orEmpty())
        val password = field("Mot de passe (8 caractères minimum)", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, true)
        val phone = field("Téléphone au format +33612345678", android.text.InputType.TYPE_CLASS_PHONE)
        val profileNameInput = field("Pseudo utilisé pendant les parties", android.text.InputType.TYPE_CLASS_TEXT)
        profileNameInput.setText(accountPreferences().getString("display_name", ""))
        panel.addView(email)
        panel.addView(password)
        panel.addView(phone)
        panel.addView(profileNameInput)

        panel.addView(actionButton("ENREGISTRER MON PROFIL", true) {
            saveAccountProfile(profileNameInput.text.toString())
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(2); bottomMargin = dp(9) })
        panel.addView(actionButton("MES STATISTIQUES ET MON HISTORIQUE") {
            showAccountHistory()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })

        sectionTitle("ACCÈS AU COMPTE")
        val accountActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accountActions.addView(actionButton("CRÉER UN COMPTE") {
            runAccountRequest { SupabaseAuthClient().signUp(email.text.toString().trim(), password.text.toString()) }
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { rightMargin = dp(5) })
        accountActions.addView(actionButton("CONNEXION") {
            runAccountRequest { SupabaseAuthClient().signIn(email.text.toString().trim(), password.text.toString()) }
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { leftMargin = dp(5) })
        panel.addView(accountActions)

        val phoneActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        phoneActions.addView(actionButton("CODE SMS") {
            val number = phone.text.toString().trim()
            if (!number.startsWith("+") || number.length < 8) {
                Toast.makeText(this@MainActivity, "Saisis le numéro au format international (+33...).", Toast.LENGTH_LONG).show()
            } else runAccountRequest { SupabaseAuthClient().requestPhoneOtp(number) }
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { rightMargin = dp(5) })
        phoneActions.addView(actionButton("VALIDER SMS") {
            val number = phone.text.toString().trim()
            val code = EditText(this@MainActivity).apply {
                hint = "Code reçu par SMS"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            AlertDialog.Builder(this@MainActivity).setTitle("Vérification du téléphone").setView(code)
                .setPositiveButton("Valider") { _, _ ->
                    runAccountRequest { SupabaseAuthClient().verifyPhoneOtp(number, code.text.toString().trim()) }
                }.setNegativeButton("Annuler", null).show()
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { leftMargin = dp(5) })
        panel.addView(phoneActions)
        return panel
    }

    private fun showAvatarGallery() {
        val options = listOf(
            "ace", "shadow", "viper", "wolf", "fox", "royal",
            "cyber", "bull", "phantom", "storm", "raven", "blaze"
        )
        val labels = listOf(
            "ACE", "SHADOW", "VIPER", "WOLF", "FOX", "ROYAL",
            "CYBER", "BULL", "PHANTOM", "STORM", "RAVEN", "BLAZE"
        )
        val selected = accountPreferences().getString("avatar_emoji", "ace") ?: "ace"
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 3
            setPadding(dp(12), dp(8), dp(12), dp(12))
            useDefaultMargins = false
        }
        val dialog = android.app.Dialog(this)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF111D29.toInt(), 0xFF070D14.toInt())
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0xFF20D9D2.toInt())
            }
        }
        shell.addView(TextView(this).apply {
            text = "CHOISIS TON AVATAR"
            textSize = 19f
            gravity = Gravity.CENTER
            letterSpacing = .08f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, dp(4), 0, dp(4))
        })
        shell.addView(TextView(this).apply {
            text = "PORTRAITS EXCLUSIFS  •  DART VISION"
            textSize = 9f
            gravity = Gravity.CENTER
            letterSpacing = .12f
            setTextColor(0xFF27E4DE.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        options.forEachIndexed { index, key ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (key == selected) 0xFF1B3039.toInt() else 0xFF101923.toInt())
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(if (key == selected) 2 else 1),
                        if (key == selected) 0xFF20E5DE.toInt() else 0xFF344956.toInt())
                }
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(104)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            tile.addView(DartAvatarPortraitView(this, index).apply {
                layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            })
            tile.addView(TextView(this).apply {
                text = labels[index]
                textSize = 9f
                gravity = Gravity.CENTER
                letterSpacing = .06f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(if (key == selected) 0xFF35E7E0.toInt() else 0xFFEAF1F5.toInt())
                setPadding(0, dp(5), 0, 0)
            })
            tile.setOnClickListener {
                accountPreferences().edit().putString("avatar_emoji", key).remove("avatar_uri").apply()
                refreshProfileAvatar()
                dialog.dismiss()
            }
            grid.addView(tile)
        }
        shell.addView(ScrollView(this).apply { addView(grid) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(TextView(this).apply {
            text = "ANNULER"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = .12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFF4058.toInt())
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(shell)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val availableHeight = resources.displayMetrics.heightPixels
        val dialogHeight = minOf(dp(540), (availableHeight * .76f).toInt())
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), dialogHeight)
    }

    private fun refreshProfileAvatar() {
        val prefs = accountPreferences()
        val image = profileAvatarPreview ?: return
        val uri = prefs.getString("avatar_uri", null)
        if (!uri.isNullOrBlank()) runCatching { image.setImageURI(Uri.parse(uri)) }
        else {
            val key = prefs.getString("avatar_emoji", "ace") ?: "ace"
            val keys = listOf("ace","shadow","viper","wolf","fox","royal","cyber","bull","phantom","storm","raven","blaze")
            val style = keys.indexOf(key).let { if (it < 0) 0 else it }
            val size = dp(68)
            val portrait = DartAvatarPortraitView(this, style)
            portrait.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
            portrait.layout(0, 0, size, size)
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            portrait.draw(android.graphics.Canvas(bitmap))
            image.setImageBitmap(bitmap)
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 741 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            accountPreferences().edit().putString("avatar_uri", uri.toString()).apply()
            refreshProfileAvatar()
            Toast.makeText(this, "Photo de profil enregistrée sur cet appareil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Store account credentials in Android Keystore-backed encrypted preferences.
     * Migrate legacy preferences once, then remove the unencrypted copy.
     */
    private fun accountPreferences(): SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(this)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val secure = androidx.security.crypto.EncryptedSharedPreferences.create(
            this,
            "dart_vision_account_secure",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val legacy = getSharedPreferences("dart_vision_account", MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            val editor = secure.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is String -> if (!secure.contains(key)) editor.putString(key, value)
                    is Boolean -> if (!secure.contains(key)) editor.putBoolean(key, value)
                    is Int -> if (!secure.contains(key)) editor.putInt(key, value)
                    is Long -> if (!secure.contains(key)) editor.putLong(key, value)
                    is Float -> if (!secure.contains(key)) editor.putFloat(key, value)
                }
            }
            editor.commit()
            legacy.edit().clear().commit()
        }
        return secure
    }

    private fun accountId(): String? = accountPreferences().getString("user_id", null)
    private fun accountToken(): String? = accountPreferences().getString("access_token", null)
    private fun accountDisplayName(): String = accountPreferences()
        .getString("display_name", null)?.takeIf { it.isNotBlank() }
        ?: accountPreferences().getString("email", null)?.substringBefore("@")
            ?.takeIf { it.isNotBlank() } ?: "Mon profil"

    private fun syncAccountProfile() {
        val id = accountId() ?: return
        val token = accountToken() ?: return
        worker.execute {
            try {
                val client = SupabaseAuthClient()
                val existing = client.loadProfile(token, id)
                val name = existing?.optString("display_name")?.takeIf { it.isNotBlank() } ?: accountDisplayName()
                if (existing == null) client.saveProfile(token, id, name)
                accountPreferences().edit().putString("display_name", name).apply()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Profil conservé localement : ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun saveAccountProfile(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Saisis un nom de joueur.", Toast.LENGTH_SHORT).show()
            return
        }
        val id = accountId()
        val token = accountToken()
        if (id == null || token == null) {
            accountPreferences().edit().putString("display_name", name).apply()
            Toast.makeText(this, "Nom enregistré sur cet appareil. Connecte-toi pour synchroniser ton profil.", Toast.LENGTH_LONG).show()
            return
        }
        worker.execute {
            try {
                SupabaseAuthClient().saveProfile(token, id, name)
                accountPreferences().edit().putString("display_name", name).apply()
                runOnUiThread { Toast.makeText(this, "Profil joueur synchronisé.", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message ?: "Enregistrement du profil impossible.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showAccountHistory() {
        val id = accountId()
        val token = accountToken()
        if (id == null || token == null) {
            showInsightScreen("PROFIL JOUEUR", "CONNECTE-TOI POUR RETROUVER TES STATISTIQUES", emptyList(),
                listOf("Compte non connecté" to "Les statistiques de partie restent disponibles localement pendant la partie."))
            return
        }
        Toast.makeText(this, "Chargement de tes statistiques…", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val games = SupabaseAuthClient().loadPlayerGames(token, id)
                var wins = 0
                var darts = 0
                var points = 0
                var best = 0
                var max180 = 0
                val rows = mutableListOf<Pair<String, String>>()
                for (i in 0 until games.length()) {
                    val game = games.getJSONObject(i)
                    val summary = game.optJSONObject("summary") ?: JSONObject()
                    wins += if (game.optString("result") == "won") 1 else 0
                    darts += summary.optInt("darts")
                    points += summary.optInt("points")
                    best = maxOf(best, summary.optInt("best_visit"))
                    max180 += summary.optInt("180s")
                    val date = game.optString("finished_at").take(10)
                    rows.add("${game.optString("game_mode")} • $date" to
                        "${summary.optString("outcome", game.optString("result"))} • ${summary.optInt("points")} pts • ${summary.optInt("darts")} fléchettes • meilleure volée ${summary.optInt("best_visit")}")
                }
                val avg = if (darts == 0) 0.0 else points * 3.0 / darts
                val metrics = listOf(
                    "PARTIES" to games.length().toString(),
                    "VICTOIRES" to wins.toString(),
                    "MOYENNE / 3 FLÉCHETTES" to String.format("%.2f", avg),
                    "MEILLEURE VOLÉE" to best.toString(),
                    "180 RÉALISÉS" to max180.toString()
                )
                runOnUiThread { showInsightScreen("MON PROFIL", "STATISTIQUES CUMULÉES • ${accountDisplayName()}", metrics, rows.ifEmpty { mutableListOf("Aucune partie" to "Tes parties terminées apparaîtront ici.") }) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message ?: "Historique indisponible.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun saveFinishedAccountMatch() {
        if (!x01AccountParticipant || x01HistorySaved) return
        val id = accountId() ?: return
        val token = accountToken() ?: return
        val snapshot = x01Engine?.state ?: return
        if (!snapshot.finished) return
        val turns = snapshot.history
        val darts = turns.sumOf { it.hits.size }
        val points = turns.sumOf { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) }
        val best = turns.maxOfOrNull { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) } ?: 0
        val count180 = turns.count { !it.bust && it.hits.sumOf { hit -> hit.points } == 180 }
        val players = JSONArray()
        snapshot.players.forEach { p -> players.put(JSONObject().put("name", p.name).put("score", p.score).put("legs", p.legsWon).put("sets", p.setsWon)) }
        val history = JSONArray()
        turns.forEach { turn ->
            val hits = JSONArray()
            turn.hits.forEach { hit -> hits.put(JSONObject().put("number", hit.number ?: JSONObject.NULL).put("multiplier", hit.multiplier).put("points", hit.points)) }
            history.put(JSONObject().put("player", snapshot.players.getOrNull(turn.playerIndex)?.name ?: "Joueur")
                .put("score_before", turn.scoreBefore).put("score_after", turn.scoreAfter).put("bust", turn.bust).put("hits", hits))
        }
        val accountName = accountDisplayName()
        val userPlayed = snapshot.players.any { it.name.equals(accountName, true) }
        if (!userPlayed) return
        val winner = snapshot.players.maxByOrNull { it.setsWon * 100 + it.legsWon }
        val outcome = if (winner?.name.equals(accountName, true)) "won" else "lost"
        val game = JSONObject()
            .put("game_mode", "X01")
            .put("result", outcome)
            .put("started_at", x01StartedAt)
            .put("finished_at", java.time.Instant.now().toString())
            .put("summary", JSONObject().put("points", points).put("darts", darts).put("best_visit", best)
                .put("180s", count180).put("outcome", if (outcome == "won") "Victoire" else "Défaite"))
            .put("game_data", JSONObject().put("starting_score", snapshot.settings.startingScore)
                .put("players", players).put("turns", history))
        x01HistorySaved = true
        worker.execute {
            try {
                SupabaseAuthClient().savePlayerGame(token, id, game)
                runOnUiThread { Toast.makeText(this, "Partie enregistrée dans ton historique.", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                x01HistorySaved = false
                runOnUiThread { Toast.makeText(this, "Partie terminée, synchronisation à réessayer : ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
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
                        accountPreferences().edit()
                            .putString("user_id", result.userId).putString("email", result.email)
                            .putString("access_token", result.accessToken)
                            .putString("refresh_token", result.refreshToken).apply()
                        syncAccountProfile()
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
        val prefs = getSharedPreferences("dart_vision_players", MODE_PRIVATE)
        val names = prefs.getString("names", "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 8) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun render() {
            list.removeAllViews()
            if (names.isEmpty()) list.addView(TextView(this).apply { text = "Aucun joueur enregistré. Ajoute un pseudo ci-dessous."; textSize = 14f; setTextColor(0xFF91A4B2.toInt()); setPadding(8, 14, 8, 14) })
            names.forEachIndexed { index, name ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 8, 8, 8)
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF172630.toInt()); cornerRadius = 14f; setStroke(1, 0xFF314653.toInt()) }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
                }
                row.addView(TextView(this).apply { text = "♙"; textSize = 20f; setTextColor(0xFFFF3045.toInt()); layoutParams = LinearLayout.LayoutParams(38, -2) })
                row.addView(TextView(this).apply { text = name; textSize = 15f; setTextColor(0xFFF4F8FA.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                row.addView(TextView(this).apply { text = "✎"; textSize = 20f; setTextColor(0xFFFF3045.toInt()); setPadding(12, 8, 12, 8); setOnClickListener {
                    val field = EditText(this@MainActivity).apply { setText(name); setSelection(text.length); setSingleLine(true) }
                    AlertDialog.Builder(this@MainActivity).setTitle("Modifier le pseudo").setView(field)
                        .setNegativeButton("ANNULER", null).setPositiveButton("ENREGISTRER") { _, _ ->
                            val updated = field.text.toString().trim()
                            if (updated.isNotBlank() && names.indices.none { it != index && names[it].equals(updated, true) }) {
                                names[index] = updated
                                prefs.edit().putString("names", names.joinToString("\n")).apply()
                                render()
                            } else Toast.makeText(this@MainActivity, "Pseudo vide ou déjà utilisé.", Toast.LENGTH_SHORT).show()
                        }.show()
                } })
                row.addView(TextView(this).apply { text = "×"; textSize = 22f; setTextColor(0xFFFF756D.toInt()); setPadding(12, 8, 8, 8); setOnClickListener { names.removeAt(index); prefs.edit().putString("names", names.joinToString("\n")).apply(); render() } })
                list.addView(row)
            }
        }
        render(); container.addView(list)
        val addInput = EditText(this).apply { hint = "Nouveau pseudo"; setSingleLine(true); textSize = 16f; setPadding(14, 12, 14, 12); background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFF7FAF9.toInt()); cornerRadius = 12f; setStroke(1, 0xFFFF3045.toInt()) } }
        container.addView(addInput)
        val dialog = AlertDialog.Builder(this).setTitle("MES JOUEURS").setMessage("Enregistre tes pseudos pour les retrouver à chaque nouvelle partie.").setView(container).setPositiveButton("AJOUTER", null).setNegativeButton("FERMER", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = addInput.text.toString().trim()
                if (name.isBlank()) addInput.error = "Saisis un pseudo"
                else if (names.any { it.equals(name, true) }) addInput.error = "Ce pseudo existe déjà"
                else { names.add(name); prefs.edit().putString("names", names.joinToString("\n")).apply(); addInput.text.clear(); render() }
            }
        }
        dialog.show()
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
        val backgroundColor = 0xFFE8F5F2.toInt()
        val cardColor = 0xFFFFFFFF.toInt()
        val accentColor = 0xFFE94B4B.toInt()
        val whiteColor = 0xFF173331.toInt()
        val mutedColor = 0xFF687F7D.toInt()
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
        if (trainingOnly) {
            showModeFolder("ENTRAÎNEMENT", DartCounterModeCatalog.default.modes.filter {
                it.category == GameModeCategory.TRAINING
            })
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val accent = 0xFFFF3045.toInt()
        val ink = 0xFF173331.toInt()
        val muted = 0xFF687F7D.toInt()
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(12), dp(8), dp(12), dp(8))
            useDefaultMargins = false
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("CHOISIR UN UNIVERS")
            .setMessage("Retrouve chaque type de partie dans son dossier.")
            .setView(ScrollView(this).apply { addView(grid) })
            .setNegativeButton("FERMER", null)
            .create()

        fun addFolder(icon: String, title: String, subtitle: String, action: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(15), dp(14), dp(15))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF172630.toInt())
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), 0xFF1D5960.toInt())
                }
                isClickable = true
                isFocusable = true
                elevation = dp(3).toFloat()
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(142)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
            }
            tile.addView(TextView(this).apply {
                text = icon
                textSize = 27f
                setTextColor(accent)
                gravity = Gravity.CENTER_VERTICAL
            })
            tile.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(ink)
                setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
                setPadding(0, dp(5), 0, dp(3))
            })
            tile.addView(TextView(this).apply {
                text = subtitle
                textSize = 10f
                setTextColor(muted)
                maxLines = 2
            })
            tile.setOnClickListener {
                dialog.dismiss()
                action()
            }
            grid.addView(tile)
        }

        val allModes = DartCounterModeCatalog.default.modes
        addFolder("⌂", "PARTIE LOCALE", "Jouer sur cet appareil ou en réseau local") {
            showModeFolder("PARTIE LOCALE", allModes.filter {
                it.category == GameModeCategory.CLASSIC || it.category == GameModeCategory.PARTY
            })
        }
        addFolder("◎", "PARTIE EN LIGNE", "Défier des joueurs à distance") {
            showModeFolderUnavailable(
                "PARTIE EN LIGNE",
                "Le dossier est prêt. Les salons, invitations et matchs à distance seront activés lorsque le service multijoueur en ligne sera finalisé."
            )
        }
        addFolder("🎯", "ENTRAÎNEMENT", "Travailler les simples, doubles et finishes") {
            showModeFolder("ENTRAÎNEMENT", allModes.filter { it.category == GameModeCategory.TRAINING })
        }
        addFolder("🏆", "MODE CLASSÉ", "Jouer pour progresser dans le classement") {
            showModeFolderUnavailable(
                "MODE CLASSÉ",
                "Le mode classé sera activé avec le système de classement et de validation des résultats. Aucun rang fictif ne sera affiché."
            )
        }
        dialog.show()
    }

    private fun showModeFolderUnavailable(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("COMPRIS", null)
            .show()
    }

    private fun showModeFolder(title: String, modes: List<DartGameMode>) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val accent = 0xFFFF3045.toInt()
        val ink = 0xFF173331.toInt()
        val muted = 0xFF687F7D.toInt()
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(12), dp(8), dp(12), dp(8))
            useDefaultMargins = false
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(grid) })
            .setNegativeButton("RETOUR", null)
            .create()

        modes.forEach { mode ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(14), dp(13), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), 0xFFD5E7E3.toInt())
                }
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(124)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
            }
            tile.addView(TextView(this).apply {
                text = if (mode.category == GameModeCategory.PARTY) "MULTIJOUEUR LOCAL"
                    else if (mode.category == GameModeCategory.TRAINING) "ENTRAÎNEMENT" else "CLASSIQUE"
                textSize = 9f
                setTextColor(accent)
                setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
            })
            tile.addView(TextView(this).apply {
                text = mode.title
                textSize = 16f
                setTextColor(ink)
                setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
                setPadding(0, dp(5), 0, dp(3))
            })
            tile.addView(TextView(this).apply {
                text = mode.description
                textSize = 10f
                setTextColor(muted)
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
                setStroke(1, 0xFFB8B8C2.toInt())
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
        val savedPlayerNames = getSharedPreferences("dart_vision_players", MODE_PRIVATE).getString("names", "").orEmpty().split("\n", "\\n").map { it.trim() }.filter { it.isNotBlank() }
        val existingPlayers = state.get().optJSONArray("players") ?: JSONArray()
        val playerNameInputs = (0 until 4).map { index ->
            EditText(this).apply {
                hint = "Pseudo du joueur ${index + 1}"
                setSingleLine(true); textSize = 16f
                setTextColor(0xFF183B36.toInt()); setHintTextColor(0xFF788A86.toInt())
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setPadding(14, 12, 14, 12); minimumHeight = 52
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt()); cornerRadius = 12f; setStroke(1, 0xFFB8B8C2.toInt())
                }
                existingPlayers.optJSONObject(index)?.optString("name", "")?.takeIf { it.isNotBlank() }?.let { setText(it) }
                    ?: setText(savedPlayerNames.getOrNull(index).orEmpty())
            }
        }
        playerNameInputs.forEachIndexed { index, input -> input.visibility = if (index < playerSpinner.selectedItemPosition + 1) View.VISIBLE else View.GONE }
        playerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                playerNameInputs.forEachIndexed { index, input -> input.visibility = if (index <= position) View.VISIBLE else View.GONE }
            }
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
                setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
                setPadding(0, 0, 0, 5)
            })
            if (subtitle != null) card.addView(TextView(this).apply {
                text = subtitle
                textSize = 11f
                setTextColor(0xFF8E929B.toInt())
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
            text = "Participants • un pseudo par ligne"
            textSize = 13f; setTextColor(0xFF263D39.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, 10, 0, 5)
        })
        playerNameInputs.forEachIndexed { index, input ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 3, 0, 3) }
            row.addView(TextView(this).apply { text = "0${index + 1}"; textSize = 13f; setTextColor(0xFFE32636.toInt()); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(34, 48) })
            row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            playerSection.addView(row)
        }
        playerSection.addView(Button(this).apply { text = "GÉRER MES JOUEURS"; isAllCaps = false; setOnClickListener { managePlayers() } })
        val includeAccountPlayer = CheckBox(this).apply {
            text = "Ajouter automatiquement mon profil à cette partie"
            textSize = 13f
            setTextColor(0xFF263D39.toInt())
            isChecked = accountId() != null
            isEnabled = accountId() != null
        }
        if (accountId() == null) {
            includeAccountPlayer.text = "Connecte-toi pour ajouter automatiquement ton profil"
        }
        playerSection.addView(includeAccountPlayer)
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
                val enteredNames = playerNameInputs.take(count).mapIndexed { i, input -> input.text.toString().trim().ifBlank { "Joueur ${i + 1}" } }.toMutableList()
                if (includeAccountPlayer.isChecked) enteredNames[0] = accountDisplayName()
                val names = enteredNames.distinctBy { it.lowercase() }
                getSharedPreferences("dart_vision_players", MODE_PRIVATE).edit().putString("names", names.joinToString("\n")).apply()
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
                gameInProgress = true
                bottomNavigationBar?.visibility = if (currentPage == "game") View.GONE else View.VISIBLE
                x01AccountParticipant = includeAccountPlayer.isChecked
                x01HistorySaved = false
                x01StartedAt = java.time.Instant.now().toString()
                trainingTurnHandler = null
                selectedMode = DartGameMode.X01
                saveX01Game()
                refreshX01()
                enterManualTurn()
            }
            .show()
    }

    /**
     * Rebuilds the live match screen after returning from the home page.
     * A saved match must never land on the empty/manual-entry placeholder screen.
     */
    private fun showRestoredGameScreen() {
        gameHeadingView?.visibility = View.VISIBLE
        gameScoreCardView?.visibility = View.VISIBLE
        gameCameraShortcutView?.visibility = View.GONE
        gameNextActionsView?.visibility = View.GONE
        refreshX01()
        enterManualTurn()
    }

    private fun showManualEntryPrompt() {
        val host = manualEntryHost ?: return
        host.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val prompt = TextView(this).apply {
            text = "✦  SAISIR LES 3 FLÉCHETTES"
            textSize = 15f
            setTextColor(0xFFFF3045.toInt())
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(17), dp(12), dp(17))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1C2A35.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFFF3045.toInt())
            }
            setOnClickListener { enterManualTurn() }
        }
        host.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun enterManualTurn(initialHit: DartHit? = null) {
        gameInProgress = true
        bottomNavigationBar?.visibility = if (currentPage == "game") View.GONE else View.VISIBLE
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
        val teal = 0xFFFF3045.toInt()
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
                setTypeface(android.graphics.Typeface.create("sans-serif",
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL))
                setPadding(dp(2), dp(3), dp(2), dp(3))
            }
        val gamePlayers = engine?.state?.players
        val activeIndex = engine?.state?.activePlayer ?: 0
        val activeName = gamePlayers?.getOrNull(activeIndex)?.name ?: "Joueur"
        val title = text("AU TOUR DE $activeName", 14f, coral, true).apply { gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false }
        val playerScoreViews = mutableListOf<TextView>()
        val scoreboard = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(0xFF1C2A35.toInt(), teal, 18); setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val activePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(coral, coral, 14); layoutParams = LinearLayout.LayoutParams(0, dp(210), 1.15f) }
        activePanel.addView(text(activeName, 13f, white, true).apply { gravity = Gravity.START; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false })
        val activeScore = text(gamePlayers?.getOrNull(activeIndex)?.score?.toString() ?: "301", 34f, white, true).apply { gravity = Gravity.START; includeFontPadding = false }
        activePanel.addView(activeScore)
        activePanel.addView(text("TOUR " + (engine?.state?.turnNumber ?: 1) + " • 3 FLÉCHETTES", 9f, white, true).apply { gravity = Gravity.START; includeFontPadding = false })
        val activeStats = text("", 8f, white, true).apply { gravity = Gravity.START; setPadding(0, dp(4), 0, 0); maxLines = 3; includeFontPadding = false; setLineSpacing(0f, 1f) }
        activePanel.addView(activeStats)
        scoreboard.addView(activePanel)
        val opponents = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, dp(210), 1f) }
        gamePlayers?.forEachIndexed { index, player ->
            if (index == activeIndex) playerScoreViews.add(activeScore)
            else {
                val scoreLine = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(8), dp(8)); background = rounded(0xFF173E49.toInt(), teal, 12); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(3), 0, dp(3)) } }
                scoreLine.addView(text(player.name, 14f, ink, true).apply { gravity = Gravity.START; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                val score = text(player.score.toString(), 25f, ink, true).apply { gravity = Gravity.START }
                playerScoreViews.add(score)
                scoreLine.addView(score)
                opponents.addView(scoreLine)
            }
        }
        scoreboard.addView(opponents)
        content.addView(scoreboard, LinearLayout.LayoutParams(-1, dp(230)).apply { bottomMargin = dp(8) })
        content.addView(title, LinearLayout.LayoutParams(-1, dp(42)))
        val entry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 30); setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        entry.addView(text("▦", 22f, ink, true), LinearLayout.LayoutParams(dp(34), dp(46)))
        val slots = mutableListOf<TextView>()
        repeat(3) {
            val slot = text("—", 19f, ink, true).apply { background = rounded(0xFF172630.toInt(), 0xFF3A4D59.toInt(), 6) }
            slots.add(slot)
            entry.addView(slot, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        val submit = Button(this).apply {
            text = "SAISIR"; isAllCaps = false; textSize = 13f; setTextColor(0xFF062A27.toInt())
            background = rounded(teal, teal, 28)
            stateListAnimator = null; minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(42)).apply { setMargins(dp(3), 0, 0, 0) }
        }
        entry.addView(submit)
        content.addView(entry, LinearLayout.LayoutParams(-1, dp(52)))
        val count = text("0 / 3 fléchettes", 11f, 0xFF9AADB8.toInt(), true)
        content.addView(count, LinearLayout.LayoutParams(-1, dp(18)))
        val tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabs = mutableListOf<TextView>()
        val tabNames = listOf("Simple", "Double", "Triple", "Bull 50", "Outer 25")
        tabNames.forEachIndexed { i, name ->
            val tab = text(name, 11f, ink, true).apply {
                maxLines = 1
                includeFontPadding = false
                setPadding(dp(1), 0, dp(1), 0)
                layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
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
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                    background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 9)
                    setOnClickListener { if (selectedTab < 3) addHit(DartHit(n, multiplier)) }
                }
                cells.add(cell); row.addView(cell)
            }
            grid.addView(row)
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        fun iconAction(glyph: String, description: String, click: () -> Unit) =
            TextView(this).apply {
                text = glyph
                contentDescription = description
                gravity = Gravity.CENTER
                textSize = 21f
                setTextColor(ink)
                background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 50)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                setOnClickListener { click() }
            }
        bottom.addView(iconAction("↶", "Retour / annuler la dernière fléchette") {
            if (hits.isNotEmpty()) { hits.removeAt(hits.lastIndex); render() }
        })
        bottom.addView(iconAction("⚙", "Paramètres") {
            navigateToMorePage?.invoke()
        })
        val missed = TextView(this).apply {
            text = "MANQUÉ"; contentDescription = "Fléchette manquée"
            gravity = Gravity.CENTER; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ink); background = rounded(0xFF1C2A35.toInt(), 0xFF3A4D59.toInt(), 14)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(5), 0, dp(5), 0)
            }
            setOnClickListener { addHit(DartHit.MISS) }
        }
        bottom.addView(missed)
        bottom.addView(iconAction("📹", "Activer le mode caméra") {
            (content.parent as? android.view.ViewGroup)?.removeView(content)
            manualEntryView = null
            showManualEntryPrompt()
            navigateToMorePage?.invoke()
            setCameraMode(true)
        })
        content.addView(bottom, LinearLayout.LayoutParams(-1, dp(48)))
        render = {
            slots.forEachIndexed { i, slot ->
                val hit = hits.getOrNull(i)
                // Affiche la valeur réellement comptabilisée pour la fléchette :
                // Simple 20 = 20, Double 20 = 40, Triple 20 = 60.
                slot.text = hit?.let { if (it.number == null) "MISS" else it.points.toString() } ?: "—"
                slot.background = rounded(if (i == hits.size && hits.size < 3) coral else 0xFF1C2A35.toInt(), if (i == hits.size && hits.size < 3) coral else 0xFF3A4D59.toInt(), 2)
            }
            count.text = "${hits.size} / 3 fléchettes"
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
                val history = engine.state.history.filter { it.playerIndex == engine.state.activePlayer }
                val dartsThrown = history.sumOf { it.hits.size }
                val pointsScored = history.sumOf { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) }
                val average = if (dartsThrown == 0) 0.0 else pointsScored * 3.0 / dartsThrown
                val previous = history.lastOrNull()?.scoreBefore?.toString() ?: engine.state.settings.startingScore.toString()
                activeStats.text = "MOYENNE  " + String.format(java.util.Locale.FRANCE, "%.1f", average) +
                    "\nSCORE PRÉCÉDENT  " + previous +
                    "\nFLÈCHES LANCÉES  " + dartsThrown
            } else {
                activeStats.text = "MOYENNE  —\nSCORE PRÉCÉDENT  —\nFLÈCHES LANCÉES  0"
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
                    refreshX01(); saveX01Game(); saveFinishedAccountMatch()
                    val message = if (result.bust) "Bust : score restauré à ${result.scoreBefore}. Tour terminé." else "Leg gagné !"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    (content.parent as? android.view.ViewGroup)?.removeView(content)
                    manualEntryView = null
                    enterManualTurn()
                } else render()
            }
        }
        submit.setOnClickListener {
            if (hits.size != 3) return@setOnClickListener
            try {
                val message = if (engine != null) {
                    val result = engine.throwTurn(hits.toList())
                    refreshX01(); saveX01Game(); saveFinishedAccountMatch()
                    when {
                        result.bust -> "Bust : le score de la volée est annulé."
                        result.legWon -> "Leg gagné !"
                        else -> "Volée enregistrée : ${result.scoreBefore} → ${result.scoreAfter}"
                    }
                } else trainingTurnHandler!!.invoke(hits.toList())
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                (content.parent as? android.view.ViewGroup)?.removeView(content)
                manualEntryView = null
                enterManualTurn()
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
        val activeHistory = s.history.filter { it.playerIndex == s.activePlayer }
        val darts = activeHistory.sumOf { it.hits.size }
        val scored = activeHistory.sumOf { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) }
        val average = if (darts == 0) 0.0 else scored * 3.0 / darts
        val previous = activeHistory.lastOrNull()?.scoreBefore?.toString() ?: "—"
        playerStatsView.text = "MOYENNE  " + String.format(java.util.Locale.FRANCE, "%.1f", average) + "\nSCORE PRÉCÉDENT  " + previous + "\nFLÉCHETTES JETÉES  " + darts
        val opponentJson = JSONArray()
        s.players.forEach { p -> opponentJson.put(JSONObject().put("name", p.name).put("score", p.score)) }
        refreshOpponentScores(opponentJson, s.activePlayer, 0xFF9AADB8.toInt(), 0xFFF2F6F8.toInt(), 0xFFFF3045.toInt())
        status.text = "MODE X01 • ${s.settings.startingScore} • hors ligne"
    }

    private fun addButton(row: LinearLayout, title: String, click: () -> Unit) {
        val b = Button(this).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(0xFF173331.toInt())
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
        val previousPlayers = s.optJSONArray("history")?.let { h ->
            if (h.length() > 0) h.optJSONObject(h.length() - 1)?.optJSONArray("players") else null
        }
        val previousScore = previousPlayers?.optJSONObject(active)?.optInt("score", player?.optInt("score", 301) ?: 301)
            ?: (x01Engine?.state?.settings?.startingScore ?: 301)
        val history = x01Engine?.state?.history.orEmpty().filter { it.playerIndex == active }
        val darts = history.sumOf { it.hits.size }
        val scored = history.sumOf { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) }
        val average = if (darts == 0) 0.0 else scored * 3.0 / darts
        playerStatsView.text = "MOYENNE  " + String.format(java.util.Locale.FRANCE, "%.1f", average) +
            "\nSCORE PRÉCÉDENT  " + previousScore +
            "\nFLÉCHETTES JETÉES  " + darts
        playerStatsView.visibility = View.VISIBLE
        refreshOpponentScores(players, active, 0xFF91A4B2.toInt(), 0xFFF4F8FA.toInt(), 0xFFFF3045.toInt())
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
            val history = x01Engine?.state?.history.orEmpty().filter { it.playerIndex == i }
            val darts = history.sumOf { it.hits.size }
            val scored = history.sumOf { if (it.bust) 0 else (it.scoreBefore - it.scoreAfter).coerceAtLeast(0) }
            val average = if (darts == 0) 0.0 else scored * 3.0 / darts
            val previous = history.lastOrNull()?.scoreBefore?.toString() ?: "—"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(7), dp(4), dp(5), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF1B2A35.toInt()); cornerRadius = dp(8).toFloat() }
                layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(4) }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(this).apply {
                text = p.optString("name", "Joueur " + (i + 1)); textSize = 10f; setTextColor(muted)
                setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            top.addView(TextView(this).apply { text = p.optInt("score", 301).toString(); textSize = 22f; setTextColor(white); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            row.addView(top)
            row.addView(TextView(this).apply { text = "MOY. " + String.format(java.util.Locale.FRANCE, "%.1f", average) + "  •  PRÉC. " + previous; textSize = 8f; setTextColor(muted); maxLines = 1 })
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
                val trustedReleasePrefix = "https://github.com/benjaminphilippot34-star/-DART-VISION/releases/download/"
                if (!apkUrl.startsWith(trustedReleasePrefix) || remoteCode <= 0 || versionName.isBlank() || sha256.length != 64 || !sha256.matches(Regex("[a-f0-9]{64}"))) {
                    throw SecurityException("Manifeste de mise à jour invalide ou non vérifiable")
                }
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
                val declaredLength = connection.contentLengthLong
                if (declaredLength > 100L * 1024L * 1024L) throw SecurityException("Fichier de mise à jour trop volumineux")
                val apk = File(cacheDir, "dart-vision-update.apk")
                if (apk.exists()) apk.delete()
                connection.inputStream.use { input -> FileOutputStream(apk).use { output -> input.copyTo(output) } }
                connection.disconnect()
                if (expectedSha256.length != 64 || !expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) {
                    apk.delete()
                    throw SecurityException("Empreinte de sécurité absente ou invalide")
                }
                val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (!digest.equals(expectedSha256, ignoreCase = true)) {
                    apk.delete(); throw SecurityException("La vérification d'intégrité du fichier a échoué")
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
            awaitingInstallPermission = true
            AlertDialog.Builder(this)
                .setTitle("Autoriser l'installation")
                .setMessage("Autorise DART VISION à installer sa mise à jour. Le téléchargement est déjà terminé : l'installation reprendra automatiquement à ton retour.")
                .setNegativeButton("Plus tard") { _, _ -> awaitingInstallPermission = false }
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