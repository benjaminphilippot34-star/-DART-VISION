package com.dartvision.mobile

import android.app.Activity
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Écrans de la maquette 5 écrans : Entraînement, Statistiques, Partie en ligne, Tournois.
 * Le fond reste celui de l'appli (0xFF031016). Les éléments clairs de la maquette sont
 * traduits en cartes bleu nuit.
 *
 * onNavigate reçoit une clé : "home", "training", "online", "tournaments", "stats",
 * "menu", "notifications", "history", "training:<mode>", "online:<mode>", "tournament:<nom>".
 */
class MockupScreens(
    private val activity: Activity,
    private val onNavigate: (String) -> Unit
) {
    private val bg = 0xFF031016.toInt()
    private val card = 0xFF10232D.toInt()
    private val cardStroke = 0xFF24505C.toInt()
    private val teal = 0xFF00E5F2.toInt()
    private val red = 0xFFE92335.toInt()
    private val white = 0xFFF4FCFF.toInt()
    private val muted = 0xFF9BB4BE.toInt()
    private val green = 0xFF22C55E.toInt()
    private val blue = 0xFF1E7BFF.toInt()

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int = 0, strokeDp: Int = 0, radiusDp: Int = 14) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    private fun gradient(c1: Int, c2: Int, stroke: Int, radiusDp: Int = 14) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun tv(
        value: String, sizeSp: Float, color: Int = white,
        bold: Boolean = false, italic: Boolean = false, condensed: Boolean = false
    ): TextView = TextView(activity).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        typeface = Typeface.create(if (condensed) "sans-serif-condensed" else "sans-serif", style)
    }

    private fun drawableId(name: String): Int =
        activity.resources.getIdentifier(name, "drawable", activity.packageName)

    /** Image du dossier drawable si elle existe, sinon l'emoji de secours. */
    private fun icon(name: String, glyph: String, sizeDp: Int, glyphSp: Float = 26f): View {
        val res = drawableId(name)
        return if (res != 0) ImageView(activity).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        } else TextView(activity).apply {
            text = glyph; textSize = glyphSp; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    // ------------------------------------------------------------------ point d'entrée

    fun show(screen: String) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(16))
        }
        when (screen) {
            "training" -> buildTraining(dialog, content)
            "stats" -> buildStats(dialog, content)
            "online" -> buildOnline(dialog, content)
            "tournaments" -> buildTournaments(dialog, content)
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(buildHeader(dialog))
            addView(View(activity).apply { setBackgroundColor(0xFF00EAF4.toInt()) },
                LinearLayout.LayoutParams(-1, dp(1)))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(buildNav(dialog, screen), LinearLayout.LayoutParams(-1, dp(68)).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(bg))
        dialog.window?.setLayout(-1, -1)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        dialog.show()
        ViewCompat.requestApplyInsets(root)
    }

    private fun go(dialog: Dialog, key: String) {
        dialog.dismiss()
        onNavigate(key)
    }

    // ------------------------------------------------------------------ en-tête commun

    private fun buildHeader(dialog: Dialog): View {
        val header = FrameLayout(activity).apply { setBackgroundColor(bg) }
        header.layoutParams = LinearLayout.LayoutParams(-1, dp(96))

        header.addView(TextView(activity).apply {
            text = "☰"; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener { go(dialog, "menu") }
        }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.START))

        val logo = drawableId("dart_vision_logo")
        if (logo != 0) {
            header.addView(ImageView(activity).apply {
                setImageResource(logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }, FrameLayout.LayoutParams(dp(230), dp(88), Gravity.CENTER))
        }

        header.addView(TextView(activity).apply {
            text = "🔔"; textSize = 22f; gravity = Gravity.CENTER
            setOnClickListener { go(dialog, "notifications") }
        }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.END))
        return header
    }

    private fun buildTitleRow(dialog: Dialog, title: String): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(activity).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(white)
            setOnClickListener { go(dialog, "home") }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(tv(title, 28f, white, bold = true, italic = true, condensed = true).apply {
            setPadding(dp(8), 0, 0, 0)
        })
        return row
    }

    // ------------------------------------------------------------------ navigation basse

    private fun buildNav(dialog: Dialog, current: String): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = shape(0xFF041016.toInt(), 0xFF00DDEB.toInt(), 2, 22)
        }
        fun item(key: String, label: String, iconRes: String, glyph: String) {
            val selected = key == current
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(5))
                if (selected) background = shape(0xFF092B35.toInt(), teal, 1, 13)
                setOnClickListener { if (!selected) go(dialog, key) }
            }
            val res = drawableId(iconRes)
            if (res != 0) {
                cell.addView(ImageView(activity).apply { setImageResource(res) },
                    LinearLayout.LayoutParams(dp(26), dp(26)))
            } else {
                cell.addView(TextView(activity).apply {
                    text = glyph; textSize = 20f; gravity = Gravity.CENTER
                    setTextColor(white)
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
            }
            cell.addView(TextView(activity).apply {
                text = label; textSize = 9f; gravity = Gravity.CENTER
                setTextColor(white)
            })
            bar.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        item("home", "Accueil", "ic_nav_home", "⌂")
        item("training", "Entraînement", "ic_nav_training", "◎")
        item("online", "En ligne", "ic_nav_players", "🌐")
        item("tournaments", "Tournois", "ic_nav_tournaments", "🏆")
        item("stats", "Stats", "ic_nav_stats", "▥")
        return bar
    }

    // ------------------------------------------------------------------ composants

    private fun banner(drawableNames: List<String>, glyph: String, c1: Int, c2: Int): View {
        val frame = FrameLayout(activity).apply {
            background = gradient(c1, c2, cardStroke, 14)
            clipToOutline = true
        }
        val res = drawableNames.map { drawableId(it) }.firstOrNull { it != 0 } ?: 0
        if (res != 0) {
            frame.addView(ImageView(activity).apply {
                setImageResource(res)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            frame.addView(TextView(activity).apply {
                text = glyph; textSize = 54f; gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        frame.layoutParams = LinearLayout.LayoutParams(-1, dp(112)).apply { bottomMargin = dp(10) }
        return frame
    }

    private fun menuRow(
        iconName: String, glyph: String, title: String, subtitle: String,
        c1: Int, c2: Int, stroke: Int, key: String, dialog: Dialog
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = gradient(c1, c2, stroke, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { go(dialog, key) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        row.addView(icon(iconName, glyph, 46), LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(tv(title, 20f, white, bold = true, italic = true, condensed = true))
        labels.addView(tv(subtitle, 12f, 0xFFE4F3F6.toInt()).apply { maxLines = 3 })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(activity).apply {
            text = "›"; textSize = 30f; gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(white)
        }, LinearLayout.LayoutParams(dp(28), dp(36)))
        return row
    }

    private fun pills(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(10))
        }
        labels.forEachIndexed { i, label ->
            row.addView(TextView(activity).apply {
                text = label; textSize = 14f; gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(white)
                background = if (i == selectedIndex) shape(red, 0, 0, 20)
                else shape(card, cardStroke, 1, 20)
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setOnClickListener { onSelect(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        return row
    }

    // ------------------------------------------------------------------ ENTRAÎNEMENT

    private fun buildTraining(dialog: Dialog, content: LinearLayout) {
        content.addView(buildTitleRow(dialog, "ENTRAÎNEMENT"))
        content.addView(banner(listOf("banner_training", "home_training_bg"), "🎯", 0xFF0B2A36.toInt(), 0xFF05121A.toInt()))
        val dark1 = 0xFF0E1B24.toInt()
        val dark2 = 0xFF050C12.toInt()
        content.addView(menuRow("ic_row_simple", "🎯", "Simple", "Travailler la précision.", dark1, dark2, cardStroke, "training:simple", dialog))
        content.addView(menuRow("ic_row_double", "◎", "Double", "S'entraîner sur les doubles.", dark1, dark2, cardStroke, "training:double", dialog))
        content.addView(menuRow("ic_row_triple", "🎯", "Triple", "S'entraîner sur les triples.", dark1, dark2, cardStroke, "training:triple", dialog))
        content.addView(menuRow("ic_row_finish", "🏁", "Finitions", "Pratiquer les combinaisons.", dark1, dark2, cardStroke, "training:finish", dialog))
        content.addView(menuRow("ic_row_series", "▥", "Séries", "Enchaîner les lancers.", dark1, dark2, cardStroke, "training:series", dialog))
    }

    // ------------------------------------------------------------------ PARTIE EN LIGNE

    private fun buildOnline(dialog: Dialog, content: LinearLayout) {
        content.addView(buildTitleRow(dialog, "PARTIE EN LIGNE"))
        content.addView(banner(listOf("banner_online"), "🌐", 0xFF08303A.toInt(), 0xFF05121A.toInt()))
        content.addView(menuRow("ic_row_quick", "⚡", "Match rapide", "Jouer immédiatement contre un adversaire.",
            0xFFE92335.toInt(), 0xFF8E1425.toInt(), 0xFFFF394A.toInt(), "online:quick", dialog))
        content.addView(menuRow("ic_row_room", "👥", "Salle privée", "Créer une partie et inviter un ami.",
            0xFF00AAB8.toInt(), 0xFF07515E.toInt(), 0xFF00E5F2.toInt(), "online:room", dialog))
        val dark1 = 0xFF0E1B24.toInt()
        val dark2 = 0xFF050C12.toInt()
        content.addView(menuRow("ic_row_tournament", "🏆", "Tournoi en ligne", "Participer à un tournoi en ligne.",
            dark1, dark2, cardStroke, "online:tournament", dialog))
        content.addView(menuRow("ic_row_ranking", "🥇", "Classement mondial", "Voir le classement des joueurs.",
            dark1, dark2, cardStroke, "online:ranking", dialog))
    }

    // ------------------------------------------------------------------ STATISTIQUES

    /** Valeurs par défaut = celles de la maquette. À remplacer par les vraies stats du compte. */
    data class StatsData(
        val games: String = "125",
        val wins: String = "78",
        val winRate: String = "62,4 %",
        val avg3: String = "83,72",
        val avg1: String = "27,91",
        val bestTurn: String = "140",
        val bestFinish: String = "170",
        val progression: List<Float> = listOf(61f, 68f, 74f, 71f, 80f, 86f, 97f)
    )

    var statsData: StatsData = StatsData()

    private fun buildStats(dialog: Dialog, content: LinearLayout) {
        val d = statsData
        content.addView(buildTitleRow(dialog, "STATISTIQUES"))
        content.addView(pills(listOf("Général", "Parties", "Entraînement"), 0) { i ->
            if (i != 0) go(dialog, "history")
        })

        val tiles = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun tile(iconName: String, glyph: String, value: String, label: String) {
            val t = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = shape(card, cardStroke, 1, 14)
            }
            t.addView(icon(iconName, glyph, 34, 24f))
            t.addView(tv(value, 24f, white, bold = true).apply { gravity = Gravity.CENTER })
            t.addView(tv(label, 12f, muted).apply { gravity = Gravity.CENTER })
            tiles.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        tile("ic_stat_games", "🏆", d.games, "Parties")
        tile("ic_stat_wins", "🎯", d.wins, "Victoires")
        tile("ic_stat_rate", "🎯", d.winRate, "Taux de victoire")
        content.addView(tiles)

        content.addView(tv("MOYENNES", 20f, white, bold = true, italic = true, condensed = true).apply {
            setPadding(dp(4), dp(16), 0, dp(6))
        })
        val averages = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = shape(card, cardStroke, 1, 14)
        }
        fun avgRow(label: String, value: String) {
            val r = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }
            r.addView(tv(label, 15f, white), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(tv(value, 15f, white, bold = true))
            averages.addView(r)
        }
        avgRow("Moyenne 3 fléchettes", d.avg3)
        avgRow("Moyenne 1 fléchette", d.avg1)
        avgRow("Meilleur tour (3 fl.)", d.bestTurn)
        avgRow("Meilleure finition", d.bestFinish)
        content.addView(averages)

        content.addView(tv("PROGRESSION", 20f, white, bold = true, italic = true, condensed = true).apply {
            setPadding(dp(4), dp(16), 0, dp(6))
        })
        val chartBox = FrameLayout(activity).apply {
            setPadding(dp(8), dp(12), dp(12), dp(8))
            background = shape(card, cardStroke, 1, 14)
        }
        chartBox.addView(ProgressChartView(activity, d.progression, teal, muted),
            FrameLayout.LayoutParams(-1, dp(150)))
        content.addView(chartBox)
    }

    // ------------------------------------------------------------------ TOURNOIS

    private data class TournamentItem(val name: String, val info: String, val badge: String, val status: Int)

    private fun buildTournaments(dialog: Dialog, content: LinearLayout) {
        content.addView(buildTitleRow(dialog, "TOURNOIS"))
        content.addView(banner(listOf("banner_tournaments", "home_ranked_bg"), "🏆", 0xFF5A0F18.toInt(), 0xFF120508.toInt()))

        // Exemple d'affichage : à remplacer par les vrais tournois quand le serveur existera.
        val items = listOf(
            TournamentItem("Open DART VISION", "512 joueurs • Fin dans 2 j", "En cours", 0),
            TournamentItem("Master 501", "128 joueurs • Débute dans 5 j", "Inscription", 1),
            TournamentItem("Challenge Double", "64 joueurs • Débute dans 7 j", "Inscription", 1)
        )
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var tab = 0

        fun render() {
            list.removeAllViews()
            val shown = items.filter { it.status == tab }
            if (shown.isEmpty()) {
                list.addView(tv("Aucun tournoi pour le moment.", 14f, muted).apply {
                    setPadding(dp(6), dp(16), dp(6), dp(16))
                })
            }
            shown.forEach { t ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = shape(card, cardStroke, 1, 14)
                    isClickable = true
                    setOnClickListener { go(dialog, "tournament:" + t.name) }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                }
                row.addView(icon("ic_trophy_small", "🏆", 48),
                    LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
                val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(tv(t.name, 18f, white, bold = true, italic = true, condensed = true))
                labels.addView(tv(t.info, 12f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(activity).apply {
                    text = t.badge; textSize = 11f; gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = shape(if (t.status == 0) green else blue, 0, 0, 12)
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
                row.addView(TextView(activity).apply {
                    text = "›"; textSize = 26f; includeFontPadding = false
                    setTextColor(white)
                })
                list.addView(row)
            }
        }

        val tabs = LinearLayout(activity)
        fun renderTabs() {
            tabs.removeAllViews()
            tabs.addView(pills(listOf("En cours", "À venir", "Terminés"), tab) { i ->
                tab = i
                renderTabs()
                render()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        // "En cours" = statut 0, "À venir" = statut 1, "Terminés" = statut 2
        renderTabs()
        render()
        content.addView(tabs)
        content.addView(list)
    }
}

/** Courbe de progression : grille, aire translucide, ligne et points. */
private class ProgressChartView(
    context: android.content.Context,
    private val values: List<Float>,
    private val lineColor: Int,
    private val textColor: Int
) : View(context) {
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF; strokeWidth = 1f * resources.displayMetrics.density
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor; style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; textSize = 11f * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return
        val left = 34f * resources.displayMetrics.density
        val top = 6f * resources.displayMetrics.density
        val right = width.toFloat() - 6f * resources.displayMetrics.density
        val bottom = height.toFloat() - 6f * resources.displayMetrics.density
        val min = Math.floor(((values.minOrNull() ?: 0f) - 5f).toDouble() / 10.0).toFloat() * 10f
        val max = Math.ceil(((values.maxOrNull() ?: 0f) + 2f).toDouble() / 10.0).toFloat() * 10f
        val span = (max - min).coerceAtLeast(1f)

        var g = min
        while (g <= max + 0.1f) {
            val y = bottom - (g - min) / span * (bottom - top)
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText(g.toInt().toString(), 0f, y + label.textSize / 3f, label)
            g += 10f
        }

        val stepX = (right - left) / (values.size - 1)
        val path = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = left + stepX * i
            val y = bottom - (v - min) / span * (bottom - top)
            if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) }
            else { path.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(right, bottom)
        area.close()
        fill.shader = LinearGradient(0f, top, 0f, bottom, 0x6600E5F2, 0x0000E5F2, Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        values.forEachIndexed { i, v ->
            val x = left + stepX * i
            val y = bottom - (v - min) / span * (bottom - top)
            canvas.drawCircle(x, y, 4f * resources.displayMetrics.density, dot)
        }
    }
}
