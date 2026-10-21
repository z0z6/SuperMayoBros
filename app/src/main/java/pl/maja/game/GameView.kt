package pl.maja.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Pal(
    val top: Int, val dirt: Int, val dirtDark: Int, val brick: Int, val brickLine: Int,
    val leaf: Int, val leafDark: Int, val hard: Int
)

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    private val game = Game()
    private var thread: Thread? = null
    @Volatile private var running = false
    private var scale = 1f

    private val P = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rf = RectF()
    private val path = Path()
    private val hexPath = Path()
    private val PI_F = Math.PI.toFloat()

    private fun rgb(v: Long): Int = v.toInt()

    private val pals = arrayOf(
        Pal(rgb(0xFF4CAF3A), rgb(0xFF8B5A2B), rgb(0xFF6B4220), rgb(0xFFC8743C), rgb(0xFF8A4A22), rgb(0xFF58B83F), rgb(0xFF3F8F2D), rgb(0xFFB08A5A)),
        Pal(rgb(0xFF6B8A2A), rgb(0xFF5A3A24), rgb(0xFF3F2616), rgb(0xFF7A5A48), rgb(0xFF4A3426), rgb(0xFF3E7A3A), rgb(0xFF2B5A2A), rgb(0xFF6A5A50)),
        Pal(rgb(0xFF3E9A3A), rgb(0xFF6B4A2A), rgb(0xFF4B3018), rgb(0xFFB06A3A), rgb(0xFF7A4020), rgb(0xFF4CAF50), rgb(0xFF2E7D32), rgb(0xFF9A7A4A)),
        Pal(rgb(0xFFE0A820), rgb(0xFFB07A10), rgb(0xFF7A5008), rgb(0xFFD9A03A), rgb(0xFF8F5F10), rgb(0xFFE8B83A), rgb(0xFFB88510), rgb(0xFF8F5F10))
    )
    private val sky = arrayOf(
        LinearGradient(0f, 0f, 0f, 224f, rgb(0xFF5AB8F5), rgb(0xFFCDEFFF), Shader.TileMode.CLAMP),
        LinearGradient(0f, 0f, 0f, 224f, rgb(0xFF140A06), rgb(0xFF33201A), Shader.TileMode.CLAMP),
        LinearGradient(0f, 0f, 0f, 224f, rgb(0xFF6A4FB8), rgb(0xFFFFB46B), Shader.TileMode.CLAMP),
        LinearGradient(0f, 0f, 0f, 224f, rgb(0xFF2A1238), rgb(0xFF7A4510), Shader.TileMode.CLAMP)
    )

    init {
        holder.addCallback(this)
        isFocusable = true
        for (i in 0 until 6) {
            val a = (i * 60f - 30f) * PI_F / 180f
            val x = cos(a) * 10f
            val y = sin(a) * 10f
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()
    }

    // ------------------------------------------------------------ lifecycle
    override fun surfaceCreated(h: SurfaceHolder) {}
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) {}

    fun resumeLoop() {
        if (running) return
        running = true
        thread = Thread(this).also { it.start() }
    }

    fun pauseLoop() {
        running = false
        try { thread?.join() } catch (e: InterruptedException) {}
        thread = null
        synchronized(game) { if (game.state == Game.PLAY) game.paused = true }
    }

    override fun run() {
        var last = System.nanoTime()
        var acc = 0L
        val step = 16_666_667L
        while (running) {
            val now = System.nanoTime()
            acc += now - last
            last = now
            if (acc > 100_000_000L) acc = 100_000_000L
            synchronized(game) {
                while (acc >= step) { game.update(); acc -= step }
                val c: Canvas? = try { holder.lockCanvas() } catch (e: Exception) { null }
                if (c != null) {
                    try { render(c) } finally {
                        try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}
                    }
                }
            }
            val el = (System.nanoTime() - now) / 1_000_000L
            if (el < 14L) try { Thread.sleep(14L - el) } catch (e: InterruptedException) {}
        }
    }

    // ---------------------------------------------------------------- input
    private fun buttons(): Array<FloatArray> {
        val s = height / 360f
        val w = width.toFloat()
        val h = height.toFloat()
        return arrayOf(
            floatArrayOf(70f * s, h - 62f * s, 40f * s),        // 0 lewo
            floatArrayOf(165f * s, h - 62f * s, 40f * s),       // 1 prawo
            floatArrayOf(w - 62f * s, h - 62f * s, 46f * s),    // 2 skok
            floatArrayOf(w - 165f * s, h - 48f * s, 40f * s),   // 3 bieg / strzał
            floatArrayOf(w - 28f * s, 28f * s, 18f * s),        // 4 pauza
            floatArrayOf(w - 72f * s, 28f * s, 18f * s)         // 5 dźwięk
        )
    }

    private fun hit(x: Float, y: Float): Int {
        val b = buttons()
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in b.indices) {
            val dx = x - b[i][0]; val dy = y - b[i][1]
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (d < b[i][2] * 1.35f && d < bd) { bd = d; best = i }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        synchronized(game) {
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
                val i = e.actionIndex
                val x = e.getX(i); val y = e.getY(i)
                val st = game.state
                if (st == Game.TITLE || st == Game.OVER || st == Game.WIN) {
                    game.tap(x / scale, y / scale)
                } else {
                    when (hit(x, y)) {
                        4 -> if (st == Game.PLAY) game.paused = !game.paused
                        5 -> Sfx.enabled = !Sfx.enabled
                        -1 -> if (game.paused) game.paused = false
                    }
                }
            }
            var l = false; var r = false; var j = false; var b = false
            if (act != MotionEvent.ACTION_CANCEL) {
                for (i in 0 until e.pointerCount) {
                    if ((act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) && i == e.actionIndex) continue
                    when (hit(e.getX(i), e.getY(i))) {
                        0 -> l = true
                        1 -> r = true
                        2 -> j = true
                        3 -> b = true
                    }
                }
            }
            game.inLeft = l; game.inRight = r; game.inJump = j; game.inRun = b
        }
        return true
    }

    // ------------------------------------------------------------- drawing utils
    private fun fill(c: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int) {
        P.style = Paint.Style.FILL; P.color = col
        c.drawRect(x, y, x + w, y + h, P)
    }

    private fun oval(c: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int) {
        P.style = Paint.Style.FILL; P.color = col
        rf.set(x, y, x + w, y + h)
        c.drawOval(rf, P)
    }

    private fun circ(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        P.style = Paint.Style.FILL; P.color = col
        c.drawCircle(cx, cy, r, P)
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, col: Int, sw: Float) {
        P.style = Paint.Style.STROKE; P.color = col; P.strokeWidth = sw
        c.drawCircle(cx, cy, r, P)
        P.style = Paint.Style.FILL
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, col: Int, sw: Float) {
        P.style = Paint.Style.STROKE; P.color = col; P.strokeWidth = sw; P.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1, y1, x2, y2, P)
        P.style = Paint.Style.FILL
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int,
                    align: Paint.Align = Paint.Align.LEFT, shadow: Boolean = true) {
        tp.textSize = size
        tp.textAlign = align
        if (shadow) {
            tp.color = rgb(0xFF000000)
            c.drawText(s, x + size * 0.07f + 0.5f, y + size * 0.07f + 0.5f, tp)
        }
        tp.color = col
        c.drawText(s, x, y, tp)
    }

    private fun hash(n: Int): Int {
        var x = n * 374761393 + 668265263
        x = (x xor (x shr 13)) * 1274126177
        return (x xor (x shr 16)) and 0x7fffffff
    }

    // --------------------------------------------------------------- render
    private fun render(c: Canvas) {
        val W = c.width.toFloat()
        val H = c.height.toFloat()
        scale = H / 224f
        game.viewW = W / scale
        c.drawColor(Color.BLACK)
        c.save()
        c.scale(scale, scale)
        when (game.state) {
            Game.TITLE -> drawTitle(c)
            Game.INTRO -> drawIntro(c)
            else -> {
                drawWorld(c)
                drawHud(c)
                if (game.state == Game.OVER) {
                    fill(c, 0f, 0f, game.viewW, 224f, 0xAA000000.toInt())
                    txt(c, "KONIEC GRY", game.viewW / 2f, 100f, 26f, rgb(0xFFFF5252), Paint.Align.CENTER)
                    txt(c, "Wynik: " + game.score, game.viewW / 2f, 124f, 12f, Color.WHITE, Paint.Align.CENTER)
                    txt(c, "Dotknij ekranu, aby wrócić do menu", game.viewW / 2f, 150f, 9f, Color.WHITE, Paint.Align.CENTER)
                } else if (game.state == Game.WIN) {
                    drawWin(c)
                } else if (game.paused) {
                    fill(c, 0f, 0f, game.viewW, 224f, 0x99000000.toInt())
                    txt(c, "PAUZA", game.viewW / 2f, 110f, 24f, Color.WHITE, Paint.Align.CENTER)
                    txt(c, "Dotknij ekranu, aby kontynuować", game.viewW / 2f, 132f, 9f, Color.WHITE, Paint.Align.CENTER)
                }
            }
        }
        c.restore()
        val st = game.state
        if (st == Game.PLAY || st == Game.DYING || st == Game.CLEAR) drawControls(c)
    }

    private fun drawControls(c: Canvas) {
        val b = buttons()
        val g = game
        val pressed = booleanArrayOf(g.inLeft, g.inRight, g.inJump, g.inRun, g.paused, !Sfx.enabled)
        val labels = arrayOf("", "", "SKOK", "BIEG", "", "")
        for (i in b.indices) {
            val cx = b[i][0]; val cy = b[i][1]; val r = b[i][2]
            P.style = Paint.Style.FILL
            P.color = if (pressed[i]) 0x77FFFFFF else 0x33000000
            c.drawCircle(cx, cy, r, P)
            P.style = Paint.Style.STROKE; P.strokeWidth = r * 0.06f; P.color = 0x99FFFFFF.toInt()
            c.drawCircle(cx, cy, r, P)
            P.style = Paint.Style.FILL; P.color = 0xCCFFFFFF.toInt()
            when (i) {
                0 -> { path.reset(); path.moveTo(cx - r * 0.4f, cy); path.lineTo(cx + r * 0.25f, cy - r * 0.45f); path.lineTo(cx + r * 0.25f, cy + r * 0.45f); path.close(); c.drawPath(path, P) }
                1 -> { path.reset(); path.moveTo(cx + r * 0.4f, cy); path.lineTo(cx - r * 0.25f, cy - r * 0.45f); path.lineTo(cx - r * 0.25f, cy + r * 0.45f); path.close(); c.drawPath(path, P) }
                4 -> { c.drawRect(cx - r * 0.35f, cy - r * 0.4f, cx - r * 0.1f, cy + r * 0.4f, P); c.drawRect(cx + r * 0.1f, cy - r * 0.4f, cx + r * 0.35f, cy + r * 0.4f, P) }
                5 -> {
                    c.drawRect(cx - r * 0.45f, cy - r * 0.2f, cx - r * 0.15f, cy + r * 0.2f, P)
                    path.reset(); path.moveTo(cx - r * 0.15f, cy - r * 0.2f); path.lineTo(cx + r * 0.25f, cy - r * 0.5f); path.lineTo(cx + r * 0.25f, cy + r * 0.5f); path.lineTo(cx - r * 0.15f, cy + r * 0.2f); path.close(); c.drawPath(path, P)
                    if (!Sfx.enabled) { P.color = 0xFFFF5252.toInt(); P.style = Paint.Style.STROKE; P.strokeWidth = r * 0.12f; c.drawLine(cx - r * 0.5f, cy + r * 0.5f, cx + r * 0.5f, cy - r * 0.5f, P); P.style = Paint.Style.FILL }
                }
                else -> {
                    tp.textSize = r * 0.42f; tp.textAlign = Paint.Align.CENTER; tp.color = 0xDDFFFFFF.toInt()
                    c.drawText(labels[i], cx, cy + r * 0.15f, tp)
                }
            }
        }
    }

    // ---------------------------------------------------------------- screens
    private fun drawTitle(c: Canvas) {
        val vw = game.viewW
        val f = game.frame
        drawBackground(c, 0, f * 0.6f)
        fill(c, 0f, 196f, vw, 28f, pals[0].dirt)
        fill(c, 0f, 196f, vw, 5f, pals[0].top)
        txt(c, "PSZCZÓŁKA MAJA", vw / 2f, 38f, 26f, rgb(0xFFFFD21F), Paint.Align.CENTER)
        txt(c, "Łąkowa Przygoda", vw / 2f, 58f, 14f, Color.WHITE, Paint.Align.CENTER)
        for (i in 0..1) {
            val cx = vw * (if (i == 0) 0.27f else 0.73f)
            P.style = Paint.Style.FILL; P.color = 0x66000000
            rf.set(cx - 66f, 72f, cx + 66f, 178f)
            c.drawRoundRect(rf, 8f, 8f, P)
            c.save()
            c.translate(cx, 128f + sin(f * 0.1f + i * 2f) * 2f)
            c.scale(3.2f, 3.2f)
            drawHero(c, 0f, 0f, 12f, 14f, 1, f * 0.15f, i, true)
            c.restore()
            txt(c, if (i == 0) "MAJA" else "GUCIO", cx, 150f, 13f, Color.WHITE, Paint.Align.CENTER)
            txt(c, if (i == 0) "pszczółka – zwinna" else "konik polny – skacze wyżej", cx, 163f, 7.5f, rgb(0xFFFFE082), Paint.Align.CENTER)
            txt(c, if (i == 0) "" else "ale się ślizga", cx, 172f, 7.5f, rgb(0xFFFFE082), Paint.Align.CENTER)
        }
        if ((f / 30) % 2 == 0) txt(c, "Dotknij bohatera, aby zacząć", vw / 2f, 190f, 10f, Color.WHITE, Paint.Align.CENTER)
        txt(c, "Gra fanowska • postacie z serialu „Pszczółka Maja”", vw / 2f, 218f, 6.5f, 0xCCFFFFFF.toInt(), Paint.Align.CENTER, false)
    }

    private fun drawIntro(c: Canvas) {
        val vw = game.viewW
        fill(c, 0f, 0f, vw, 224f, Color.BLACK)
        txt(c, "ŚWIAT " + game.lv.code, vw / 2f, 80f, 20f, Color.WHITE, Paint.Align.CENTER)
        txt(c, game.lv.name, vw / 2f, 100f, 12f, rgb(0xFFFFD21F), Paint.Align.CENTER)
        c.save(); c.translate(vw / 2f - 24f, 140f); c.scale(2f, 2f)
        drawHero(c, 0f, 0f, 12f, 14f, 1, 0f, game.hero, false)
        c.restore()
        txt(c, "×  " + game.lives, vw / 2f + 6f, 136f, 16f, Color.WHITE, Paint.Align.LEFT)
    }

    private fun drawWin(c: Canvas) {
        val vw = game.viewW
        fill(c, 0f, 0f, vw, 224f, 0xBB000000.toInt())
        txt(c, "GRATULACJE!", vw / 2f, 80f, 26f, rgb(0xFFFFD21F), Paint.Align.CENTER)
        txt(c, "Tekla pokonana – łąka jest bezpieczna,", vw / 2f, 104f, 11f, Color.WHITE, Paint.Align.CENTER)
        txt(c, "a " + (if (game.hero == 0) "Maja" else "Gucio") + " wraca do ula!", vw / 2f, 118f, 11f, Color.WHITE, Paint.Align.CENTER)
        txt(c, "Wynik: " + game.score, vw / 2f, 142f, 14f, rgb(0xFFFFE082), Paint.Align.CENTER)
        txt(c, "Dotknij ekranu, aby wrócić do menu", vw / 2f, 170f, 9f, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawHud(c: Canvas) {
        val g = game
        val vw = g.viewW
        txt(c, if (g.hero == 0) "MAJA" else "GUCIO", 8f, 11f, 8f, Color.WHITE)
        txt(c, g.score.toString().padStart(6, '0'), 8f, 20f, 8f, Color.WHITE)
        val cx = vw * 0.23f
        drawDrop(c, cx, 14f, 1f)
        txt(c, "×" + g.coins.toString().padStart(2, '0'), cx + 6f, 20f, 8f, Color.WHITE)
        txt(c, "ŚWIAT", vw * 0.38f, 11f, 8f, Color.WHITE)
        txt(c, g.lv.code, vw * 0.38f, 20f, 8f, Color.WHITE)
        txt(c, "CZAS", vw * 0.50f, 11f, 8f, Color.WHITE)
        txt(c, ceil(max(g.time, 0f)).toInt().toString(), vw * 0.50f, 20f, 8f, Color.WHITE)
        c.save(); c.translate(vw * 0.62f, 20f); c.scale(0.6f, 0.6f)
        drawHero(c, 0f, 0f, 12f, 14f, 1, 0f, g.hero, false)
        c.restore()
        txt(c, "×" + g.lives, vw * 0.62f + 8f, 20f, 8f, Color.WHITE)
    }

    private fun drawDrop(c: Canvas, cx: Float, cy: Float, k: Float) {
        oval(c, cx - 3.5f * k, cy - 4.5f, 7f * k, 9f, rgb(0xFFFFB300))
        oval(c, cx - 1.8f * k, cy - 3f, 2f * k, 4f, rgb(0xFFFFE082))
    }

    // ------------------------------------------------------------ background
    private fun drawBackground(c: Canvas, theme: Int, cam: Float) {
        val vw = game.viewW
        P.style = Paint.Style.FILL
        P.shader = sky[theme]
        c.drawRect(0f, 0f, vw + 1f, 224f, P)
        P.shader = null
        when (theme) {
            0 -> {
                circ(c, vw * 0.82f, 40f, 24f, 0x33FFF59D)
                circ(c, vw * 0.82f, 40f, 15f, rgb(0xFFFFF176))
                layerHills(c, cam, 0.2f, 170f, 50f, 25f, rgb(0xFF9AD98F), vw)
                layerHills(c, cam, 0.4f, 120f, 32f, 20f, rgb(0xFF6DBB63), vw)
                clouds(c, cam, 0xCCFFFFFF.toInt(), vw)
            }
            1 -> {
                val sp = 40f; val off = cam * 0.5f; val i0 = floor(off / sp).toInt()
                for (i in i0..i0 + (vw / sp).toInt() + 2) {
                    val h = hash(i)
                    val x = i * sp - off
                    path.reset()
                    path.moveTo(x, 192f); path.lineTo(x + 18f, 192f - 20f - (h % 40)); path.lineTo(x + 36f, 192f)
                    path.close()
                    P.color = rgb(0xFF24150E); c.drawPath(path, P)
                    if (h % 3 == 0) {
                        val fx = x + (h % 30); val fy = 70f + (h / 7 % 100)
                        circ(c, fx, fy, 4f, 0x2280DEEA)
                        circ(c, fx, fy, 1.8f, 0xAA80DEEA.toInt())
                    }
                }
            }
            2 -> {
                circ(c, vw * 0.7f, 150f, 34f, 0x44FFD180)
                circ(c, vw * 0.7f, 150f, 24f, rgb(0xFFFFD180))
                layerHills(c, cam, 0.2f, 200f, 60f, 30f, rgb(0xFF7E57C2), vw)
                clouds(c, cam, 0x99FFCCBC.toInt(), vw)
                val sp = 110f; val off = cam * 0.3f; val i0 = floor(off / sp).toInt()
                for (i in i0..i0 + (vw / sp).toInt() + 2) {
                    val h = hash(i)
                    val x = i * sp - off + (h % 30)
                    fill(c, x, 20f, 14f, 204f, 0x884A2C1A.toInt())
                    oval(c, x - 22f, 4f, 58f, 34f, 0x882E6B34.toInt())
                }
                val sp2 = 70f; val off2 = cam * 0.55f; val j0 = floor(off2 / sp2).toInt()
                for (i in j0..j0 + (vw / sp2).toInt() + 2) {
                    val h = hash(i + 77)
                    val x = i * sp2 - off2 + (h % 20)
                    fill(c, x, 0f, 10f, 224f, 0xAA3A2212.toInt())
                }
            }
            else -> {
                val off = cam * 0.4f
                P.style = Paint.Style.STROKE; P.strokeWidth = 1.2f; P.color = 0x33FFC107
                val cw = 17.5f; val rh = 15f
                val c0 = floor(off / cw).toInt()
                for (cx in c0..c0 + (vw / cw).toInt() + 2) {
                    for (ry in 0..14) {
                        val x = cx * cw - off
                        val y = ry * rh + (if (cx % 2 == 0) 0f else rh / 2f)
                        c.save(); c.translate(x, y); c.drawPath(hexPath, P); c.restore()
                    }
                }
                P.style = Paint.Style.FILL
                oval(c, -20f, 180f, vw + 40f, 80f, 0x33FF8F00)
            }
        }
    }

    private fun layerHills(c: Canvas, cam: Float, par: Float, sp: Float, hBase: Float, hVar: Float, col: Int, vw: Float) {
        val off = cam * par
        val i0 = floor(off / sp).toInt()
        for (i in i0..i0 + (vw / sp).toInt() + 2) {
            val h = hBase + (hash(i) % hVar.toInt())
            oval(c, i * sp - off - 10f, 192f - h, sp * 1.15f, h * 2f, col)
        }
    }

    private fun clouds(c: Canvas, cam: Float, col: Int, vw: Float) {
        val sp = 140f; val off = cam * 0.1f; val i0 = floor(off / sp).toInt()
        for (i in i0..i0 + (vw / sp).toInt() + 2) {
            val h = hash(i + 5)
            val x = i * sp - off + (h % 40)
            val y = 18f + (h / 3 % 50)
            oval(c, x, y, 34f, 13f, col); oval(c, x + 9f, y - 7f, 22f, 16f, col); oval(c, x + 20f, y - 2f, 26f, 12f, col)
        }
    }

    // ----------------------------------------------------------------- world
    private fun drawWorld(c: Canvas) {
        val g = game
        val lv = g.lv
        val pal = pals[lv.theme]
        drawBackground(c, lv.theme, g.camX)
        c.save()
        c.translate(-floor(g.camX), 0f)
        val cam = floor(g.camX)
        val x0 = max(0, (cam / TS).toInt() - 1)
        val x1 = min(lv.w - 1, ((cam + g.viewW) / TS).toInt() + 1)

        drawGoal(c, pal)

        for (ty in 0 until LH) for (tx in x0..x1) {
            val t = lv.tiles[ty][tx]
            if (t == T.AIR) continue
            var dy = 0f
            if (g.bumps.isNotEmpty()) {
                for (b in g.bumps) if (b.tx == tx && b.ty == ty) dy = -sin(b.t / 8f * PI_F) * 4f
            }
            drawTile(c, t, tx, ty, tx * TS, ty * TS + dy, pal, lv)
        }

        for (e in g.ents) {
            if (e.x + e.w < cam - 40f || e.x > cam + g.viewW + 40f) continue
            drawEnt(c, e)
        }

        if (!g.playerHidden && !(g.invuln > 0 && g.frame % 4 < 2)) {
            val air = !g.p.onGround
            if (g.starT > 0) {
                val hue = floatArrayOf((g.frame * 12 % 360).toFloat(), 0.8f, 1f)
                circ(c, g.p.x + g.p.w / 2f, g.p.y + g.p.h / 2f, g.p.h * 0.75f, (Color.HSVToColor(110, hue)))
            }
            drawHero(c, g.p.x + g.p.w / 2f, g.p.y + g.p.h, g.p.w, g.p.h, g.facing, g.anim, g.hero, air)
        }

        for (pt in g.parts) {
            if (pt.kind == 0) fill(c, pt.x, pt.y, 5f, 5f, pt.color)
            else drawDrop(c, pt.x + 4f, pt.y, abs(cos(pt.life * 0.5f)))
        }
        c.restore()
    }

    private fun drawGoal(c: Canvas, pal: Pal) {
        val g = game
        val x = g.lv.goalX * TS
        fill(c, x + 7f, 3f * TS, 2f, 9f * TS, rgb(0xFF2E7D32))
        // kwiat na szczycie
        for (i in 0 until 6) {
            val a = i * PI_F / 3f
            circ(c, x + 8f + cos(a) * 4.5f, 3f * TS - 2f + sin(a) * 4.5f, 3.2f, rgb(0xFFFF80AB))
        }
        circ(c, x + 8f, 3f * TS - 2f, 3.2f, rgb(0xFFFFEB3B))
        // ul
        val hx = (g.lv.goalX + 5) * TS
        val by = 12f * TS
        oval(c, hx, by - 22f, 44f, 22f, rgb(0xFFC68A1A))
        oval(c, hx + 4f, by - 38f, 36f, 22f, rgb(0xFFD9A027))
        oval(c, hx + 9f, by - 50f, 26f, 18f, rgb(0xFFE8B83A))
        oval(c, hx + 15f, by - 56f, 14f, 10f, rgb(0xFFF2C94C))
        oval(c, hx + 16f, by - 12f, 12f, 12f, rgb(0xFF3E2200))
        for (i in 0 until 3) fill(c, hx + 4f + i * 2f, by - 28f + i * 2f, 36f - i * 4f, 1.5f, rgb(0xFF9A6410))
    }

    private fun drawTile(c: Canvas, t: Int, tx: Int, ty: Int, x: Float, y: Float, pal: Pal, lv: Level) {
        val h = hash(tx * 31 + ty * 7)
        when (t) {
            T.GROUND -> {
                fill(c, x, y, TS, TS, pal.dirt)
                val above = lv.get(tx, ty - 1)
                if (!lv.isSolid(above) || above == T.PIPE_L) {
                    fill(c, x, y, TS, 4f, pal.top)
                    for (i in 0 until 4) fill(c, x + i * 4f + (h % 2), y + 4f, 2f, 2f, pal.top)
                    if (lv.theme != 1 && lv.theme != 3 && h % 3 == 0) {
                        val fx = x + 3f + (h / 3 % 9)
                        fill(c, fx, y - 5f, 1f, 5f, rgb(0xFF2E7D32))
                        val colr = when ((h / 11) % 4) { 0 -> 0xFFFF80AB; 1 -> 0xFFFFF176; 2 -> 0xFFFFFFFF; else -> 0xFFB388FF }
                        circ(c, fx + 0.5f, y - 6f, 2.4f, rgb(colr))
                        circ(c, fx + 0.5f, y - 6f, 0.9f, rgb(0xFFFFB300))
                    }
                } else {
                    fill(c, x + 3f + (h % 6), y + 4f + (h / 5 % 6), 3f, 2f, pal.dirtDark)
                    fill(c, x + 9f, y + 10f, 2f, 2f, pal.dirtDark)
                }
                if (lv.theme == 1 && lv.get(tx, ty + 1) == T.AIR) {
                    fill(c, x, y + 13f, TS, 3f, pal.top)
                    if (h % 2 == 0) fill(c, x + 4f + (h % 6), y + 16f, 2f, 5f + (h % 5), pal.top)
                }
            }
            T.BRICK -> {
                fill(c, x, y, TS, TS, pal.brick)
                fill(c, x, y + 7.5f, TS, 1f, pal.brickLine)
                fill(c, x, y, TS, 1f, pal.brickLine)
                fill(c, x, y + 15f, TS, 1f, pal.brickLine)
                fill(c, x + 7.5f, y, 1f, 8f, pal.brickLine)
                fill(c, x + 3.5f, y + 8f, 1f, 8f, pal.brickLine)
                fill(c, x + 11.5f, y + 8f, 1f, 8f, pal.brickLine)
            }
            T.QBLOCK -> {
                val glow = if ((game.frame / 14) % 3 == 0) rgb(0xFFFFE082) else rgb(0xFFFFC107)
                fill(c, x, y, TS, TS, rgb(0xFF9A6700))
                fill(c, x + 1f, y + 1f, 14f, 14f, glow)
                fill(c, x + 2f, y + 2f, 1.5f, 1.5f, rgb(0xFF9A6700)); fill(c, x + 12.5f, y + 2f, 1.5f, 1.5f, rgb(0xFF9A6700))
                fill(c, x + 2f, y + 12.5f, 1.5f, 1.5f, rgb(0xFF9A6700)); fill(c, x + 12.5f, y + 12.5f, 1.5f, 1.5f, rgb(0xFF9A6700))
                txt(c, "?", x + 8f, y + 12.5f, 12f, Color.WHITE, Paint.Align.CENTER)
            }
            T.USED -> {
                fill(c, x, y, TS, TS, rgb(0xFF5D4037))
                fill(c, x + 1f, y + 1f, 14f, 14f, rgb(0xFF8D6E63))
                fill(c, x + 2f, y + 2f, 1.5f, 1.5f, rgb(0xFF5D4037)); fill(c, x + 12.5f, y + 2f, 1.5f, 1.5f, rgb(0xFF5D4037))
            }
            T.HARD -> {
                fill(c, x, y, TS, TS, pal.hard)
                fill(c, x, y, TS, 2f, 0x55FFFFFF); fill(c, x, y, 2f, TS, 0x55FFFFFF)
                fill(c, x, y + 14f, TS, 2f, 0x55000000); fill(c, x + 14f, y, 2f, TS, 0x55000000)
            }
            T.PIPE_L, T.PIPE_R -> {
                val top = lv.get(tx, ty - 1) != T.PIPE_L && lv.get(tx, ty - 1) != T.PIPE_R
                val left = t == T.PIPE_L
                fill(c, x, y, TS, TS, rgb(0xFF3FA535))
                fill(c, if (left) x + 3f else x + 10f, y, 2.5f, TS, rgb(0xFF7BD86F))
                fill(c, if (left) x else x + 14f, y, 2f, TS, rgb(0xFF2A7A24))
                if (top) {
                    val cx = if (left) x - 2f else x
                    fill(c, cx, y, 18f, 8f, rgb(0xFF55C14A))
                    fill(c, cx, y, 18f, 1.5f, rgb(0xFF2A7A24)); fill(c, cx, y + 6.5f, 18f, 1.5f, rgb(0xFF2A7A24))
                    fill(c, if (left) cx else cx + 16f, y, 2f, 8f, rgb(0xFF2A7A24))
                    fill(c, if (left) cx + 3f else cx + 5f, y + 1.5f, 2.5f, 5f, rgb(0xFF9BEA90))
                }
            }
            T.COIN -> {
                val k = abs(cos(game.frame * 0.08f + tx * 0.7f))
                drawDrop(c, x + 8f, y + 8f, max(0.25f, k))
            }
            T.LEAF -> {
                fill(c, x, y, TS, 8f, pal.leaf)
                fill(c, x, y + 8f, TS, 3f, pal.leafDark)
                fill(c, x, y, TS, 1.5f, 0x55FFFFFF)
                fill(c, x + 7.5f, y + 1f, 1f, 7f, pal.leafDark)
                path.reset(); path.moveTo(x, y + 11f); path.lineTo(x + 4f, y + 15f); path.lineTo(x + 8f, y + 11f)
                path.lineTo(x + 12f, y + 15f); path.lineTo(x + 16f, y + 11f); path.close()
                P.color = pal.leafDark; c.drawPath(path, P)
            }
            T.GATE -> {
                fill(c, x, y, TS, TS, rgb(0xFF6B3A00))
                fill(c, x + 1f, y, 6f, TS, rgb(0xFFFFB300)); fill(c, x + 9f, y, 6f, TS, rgb(0xFFFFB300))
                fill(c, x + 2f, y, 1.5f, TS, 0x66FFFFFF); fill(c, x + 10f, y, 1.5f, TS, 0x66FFFFFF)
            }
        }
    }

    // --------------------------------------------------------------- sprites
    private fun drawEnt(c: Canvas, e: Ent) {
        val cx = e.x + e.w / 2f
        val by = e.y + e.h
        c.save()
        if (e.knocked) {
            c.translate(cx, e.y + e.h / 2f); c.scale(1f, -1f); c.translate(-cx, -(e.y + e.h / 2f))
        }
        when (e.type) {
            K.BEETLE -> drawBeetle(c, cx, by, e)
            K.SNAIL -> drawSnail(c, cx, by, e)
            K.HORNET -> drawHornet(c, cx, by, e)
            K.SPIDER -> drawSpider(c, cx, by, e, 1f, 1f)
            K.BOSS -> {
                if (e.timer > 0 && (game.frame / 3) % 2 == 0) { /* miganie */ } else drawSpider(c, cx, by, e, 2f, 1.7f)
            }
            K.ALEK -> drawAlek(c, cx, by, e)
            K.HONEY -> {
                fill(c, cx - 5f, by - 11f, 10f, 11f, rgb(0xFFFFA000))
                fill(c, cx - 5f, by - 14f, 10f, 3.5f, rgb(0xFF6D4C41))
                fill(c, cx - 3f, by - 8f, 6f, 4f, rgb(0xFFFFF3E0))
                fill(c, cx - 4f, by - 10f, 1.5f, 8f, 0x66FFFFFF)
            }
            K.FLOWER -> {
                fill(c, cx - 0.8f, by - 8f, 1.6f, 8f, rgb(0xFF2E7D32))
                oval(c, cx + 0.5f, by - 5f, 5f, 2.5f, rgb(0xFF43A047))
                for (i in 0 until 5) {
                    val a = i * PI_F * 2f / 5f + game.frame * 0.03f
                    circ(c, cx + cos(a) * 4f, by - 10f + sin(a) * 4f, 3f, rgb(0xFFFF5252))
                }
                circ(c, cx, by - 10f, 2.6f, rgb(0xFFFFEB3B))
            }
            K.ONEUP -> {
                circ(c, cx, by - 6f, 6.2f, rgb(0xFFE53935))
                circ(c, cx + 4f, by - 7f, 3.6f, rgb(0xFF212121))
                line(c, cx - 1f, by - 12f, cx - 1f, by - 0.5f, rgb(0xFF212121), 1f)
                circ(c, cx - 3f, by - 8f, 1.2f, rgb(0xFF212121)); circ(c, cx - 3.5f, by - 3.5f, 1.2f, rgb(0xFF212121))
                circ(c, cx + 2.5f, by - 3f, 1.2f, rgb(0xFF212121))
            }
            K.STAR -> {
                val hue = floatArrayOf((game.frame * 9 % 360).toFloat(), 0.5f, 1f)
                circ(c, cx, by - 7f, 8f, Color.HSVToColor(90, hue))
                oval(c, cx - 5f, by - 10f, 10f, 6f, rgb(0xFFFFEE58))
                oval(c, cx - 3f, by - 13f, 6f, 5f, 0x99E1F5FE.toInt())
                circ(c, cx + 3f, by - 7f, 1.2f, rgb(0xFF212121))
            }
            K.SHOT -> {
                circ(c, cx, e.y + e.h / 2f, 3.6f, rgb(0xFFFF8F00))
                circ(c, cx, e.y + e.h / 2f, 2.6f, rgb(0xFFFFD54F))
            }
        }
        c.restore()
    }

    private fun drawBeetle(c: Canvas, cx: Float, by: Float, e: Ent) {
        c.save(); c.translate(cx, by)
        if (e.state == -1) {
            oval(c, -7f, -5f, 14f, 5f, rgb(0xFF7A4A24))
            c.restore(); return
        }
        val lg = if ((game.frame / 6) % 2 == 0) 1f else -1f
        fill(c, -6f, -2f + lg * 0.5f, 3f, 2f, rgb(0xFF3E2410)); fill(c, 3f, -2f - lg * 0.5f, 3f, 2f, rgb(0xFF3E2410))
        oval(c, -7f, -11f, 14f, 10f, rgb(0xFF7A4A24))
        oval(c, -5f, -11f, 10f, 5f, rgb(0xFF9A6234))
        line(c, 0f, -11f, 0f, -4f, rgb(0xFF4A2A10), 0.8f)
        circ(c, -2.6f, -6f, 2.3f, Color.WHITE); circ(c, 2.6f, -6f, 2.3f, Color.WHITE)
        circ(c, -2.1f, -5.6f, 1.1f, Color.BLACK); circ(c, 3.1f, -5.6f, 1.1f, Color.BLACK)
        line(c, -5.5f, -9.8f, -1.2f, -7.8f, Color.BLACK, 1.2f); line(c, 5.5f, -9.8f, 1.2f, -7.8f, Color.BLACK, 1.2f)
        c.restore()
    }

    private fun drawSnail(c: Canvas, cx: Float, by: Float, e: Ent) {
        c.save(); c.translate(cx, by); c.scale(e.dir.toFloat(), 1f)
        if (e.state == 0) {
            oval(c, -8f, -6f, 16f, 6f, rgb(0xFFE8D3A0))
            circ(c, 6f, -8f, 3.2f, rgb(0xFFE8D3A0))
            line(c, 5f, -10f, 5f, -14f, rgb(0xFFE8D3A0), 1f); line(c, 7.5f, -10f, 9f, -13.5f, rgb(0xFFE8D3A0), 1f)
            circ(c, 5f, -14f, 1.2f, Color.BLACK); circ(c, 9f, -13.5f, 1.2f, Color.BLACK)
            circ(c, -1f, -8f, 6.5f, rgb(0xFFB5651D))
            ring(c, -1f, -8f, 4f, rgb(0xFF7A3E0D), 1f); ring(c, -1f, -8f, 1.8f, rgb(0xFF7A3E0D), 1f)
        } else {
            c.translate(0f, -6f)
            if (e.state == 2) c.rotate(game.frame * 35f)
            circ(c, 0f, 0f, 6.5f, rgb(0xFFB5651D))
            ring(c, 0f, 0f, 4f, rgb(0xFF7A3E0D), 1f); ring(c, 0f, 0f, 1.8f, rgb(0xFF7A3E0D), 1f)
            oval(c, 3f, -2f, 4f, 4f, rgb(0xFF3E2200))
        }
        c.restore()
    }

    private fun drawHornet(c: Canvas, cx: Float, by: Float, e: Ent) {
        c.save(); c.translate(cx, by); c.scale(e.dir.toFloat() * -1f, 1f)
        val fl = sin(game.frame * 0.9f) * 22f
        c.save(); c.rotate(-25f + fl, -1f, -9f); oval(c, -8f, -17f, 8f, 11f, 0xAAE6F6FF.toInt()); c.restore()
        c.save(); c.rotate(-5f - fl, 1f, -9f); oval(c, -2f, -16f, 8f, 10f, 0x88E6F6FF.toInt()); c.restore()
        // ciało
        path.reset(); rf.set(-7f, -10f, 5f, -1f); path.addOval(rf, Path.Direction.CW)
        P.style = Paint.Style.FILL; P.color = rgb(0xFFFFC107); c.drawPath(path, P)
        c.save(); c.clipPath(path)
        fill(c, -4f, -10f, 2.2f, 10f, rgb(0xFF212121)); fill(c, 0f, -10f, 2.2f, 10f, rgb(0xFF212121))
        c.restore()
        // żądło
        path.reset(); path.moveTo(5f, -7f); path.lineTo(9f, -5f); path.lineTo(5f, -3f); path.close()
        P.color = rgb(0xFF212121); c.drawPath(path, P)
        // głowa
        circ(c, -6f, -6.5f, 3.6f, rgb(0xFFE65100))
        circ(c, -7.2f, -7.5f, 1.5f, rgb(0xFFFF1744)); circ(c, -7.5f, -7.5f, 0.6f, Color.BLACK)
        line(c, -9f, -10f, -5.5f, -8.8f, Color.BLACK, 1f)
        c.restore()
    }

    private fun drawSpider(c: Canvas, cx: Float, by: Float, e: Ent, sx: Float, sy: Float) {
        // nitka
        val anchorLen = by - e.baseY
        if (e.type == K.SPIDER && anchorLen > 0f) line(c, cx, e.baseY, cx, by - 8f, 0xCCFFFFFF.toInt(), 0.8f)
        c.save(); c.translate(cx, by); c.scale(sx, sy)
        val w = sin(game.frame * 0.25f) * 1.5f
        for (i in 0 until 4) {
            val yy = -3f - i * 1.6f
            line(c, -3f, yy, -8f, yy + 2f + w * (if (i % 2 == 0) 1f else -1f), rgb(0xFF1A0E26), 1f)
            line(c, 3f, yy, 8f, yy + 2f - w * (if (i % 2 == 0) 1f else -1f), rgb(0xFF1A0E26), 1f)
        }
        circ(c, 0f, -6f, 6f, rgb(0xFF4B2A63))
        circ(c, 0f, -4f, 3f, rgb(0xFF7B3F98))
        circ(c, -2.3f, -7f, 2f, Color.WHITE); circ(c, 2.3f, -7f, 2f, Color.WHITE)
        circ(c, -2.3f, -6.8f, 1f, rgb(0xFFD50000)); circ(c, 2.3f, -6.8f, 1f, rgb(0xFFD50000))
        line(c, -4.5f, -10f, -1f, -8.5f, Color.BLACK, 1f); line(c, 4.5f, -10f, 1f, -8.5f, Color.BLACK, 1f)
        if (e.type == K.BOSS) {
            // korona
            path.reset(); path.moveTo(-4f, -11f); path.lineTo(-4f, -15f); path.lineTo(-2f, -12.5f); path.lineTo(0f, -15.5f)
            path.lineTo(2f, -12.5f); path.lineTo(4f, -15f); path.lineTo(4f, -11f); path.close()
            P.style = Paint.Style.FILL; P.color = rgb(0xFFFFD600); c.drawPath(path, P)
        } else {
            circ(c, 3.5f, -11f, 1.8f, rgb(0xFFFF80AB)); circ(c, 6f, -10f, 1.8f, rgb(0xFFFF80AB))
        }
        c.restore()
    }

    private fun drawAlek(c: Canvas, cx: Float, by: Float, e: Ent) {
        c.save(); c.translate(cx, by)
        oval(c, -7f, -15f, 14f, 15f, rgb(0xFFF2B33D))
        fill(c, -6f, -9f, 12f, 1.8f, rgb(0xFF8D5A00)); fill(c, -6f, -5f, 12f, 1.8f, rgb(0xFF8D5A00))
        circ(c, 0f, -14f, 4.5f, rgb(0xFFFFD98A))
        line(c, -3.5f, -14.5f, -1f, -14.5f, Color.BLACK, 1f); line(c, 1f, -14.5f, 3.5f, -14.5f, Color.BLACK, 1f)
        line(c, -1.5f, -11.5f, 1.5f, -11.5f, rgb(0xFF8D3A00), 0.8f)
        oval(c, -10f, -16f, 6f, 9f, 0x99E6F6FF.toInt())
        c.restore()
        val dx = abs((game.p.x + game.p.w / 2f) - cx)
        val msgs = arrayOf("Zzz... Skacz po wrogach!", "Tekla czyha na korzeniach!", "Ale wysoko... ja zostaję tu.", "Pokonaj Teklę, wróć do ula!")
        if (dx < 70f) {
            val s = msgs[min(game.levelIdx, 3)]
            tp.textSize = 6.5f
            val tw = tp.measureText(s)
            P.style = Paint.Style.FILL; P.color = 0xEEFFFFFF.toInt()
            rf.set(cx - tw / 2f - 4f, by - 38f, cx + tw / 2f + 4f, by - 26f)
            c.drawRoundRect(rf, 4f, 4f, P)
            txt(c, s, cx, by - 29.5f, 6.5f, Color.BLACK, Paint.Align.CENTER, false)
        } else {
            txt(c, if ((game.frame / 40) % 2 == 0) "z" else "Z", cx + 8f, by - 20f, 7f, Color.WHITE, Paint.Align.LEFT, false)
        }
    }

    private fun drawHero(c: Canvas, cx: Float, by: Float, w: Float, h: Float, face: Int, anim: Float, hero: Int, air: Boolean) {
        c.save()
        c.translate(cx, by)
        val sx = if (h > 20f) 1.5f else 1f
        c.scale(face * sx, h / 14f)
        val flap = sin(anim * 1.8f + game.frame * 0.0f) * (if (air) 28f else 12f)
        val leg = if (air) 1.5f else sin(anim * 1.6f) * 1.6f
        if (hero == 0) {
            // skrzydła
            c.save(); c.rotate(-30f + flap, -2f, -9f); oval(c, -9f, -18f, 8f, 11f, 0xAAE6F6FF.toInt()); c.restore()
            c.save(); c.rotate(-10f - flap, 0f, -9f); oval(c, -5f, -17f, 7f, 10f, 0x88E6F6FF.toInt()); c.restore()
            // nóżki
            line(c, -2f, -2f, -2f + leg, 0f, Color.BLACK, 1.1f); line(c, 2f, -2f, 2f - leg, 0f, Color.BLACK, 1.1f)
            // tułów w paski
            path.reset(); rf.set(-5.5f, -9f, 5f, -1.5f); path.addOval(rf, Path.Direction.CW)
            P.style = Paint.Style.FILL; P.color = rgb(0xFFFFD21F); c.drawPath(path, P)
            c.save(); c.clipPath(path)
            fill(c, -3f, -9f, 1.8f, 8f, rgb(0xFF222222)); fill(c, 0.5f, -9f, 1.8f, 8f, rgb(0xFF222222))
            c.restore()
            path.reset(); path.moveTo(-5f, -6f); path.lineTo(-8f, -4.5f); path.lineTo(-5f, -3f); path.close()
            P.color = rgb(0xFF222222); c.drawPath(path, P)
            // głowa
            circ(c, 2f, -9.5f, 4.8f, rgb(0xFFFFE066))
            oval(c, -2.6f, -15f, 8f, 5f, rgb(0xFFE0A800))      // włosy
            line(c, 0.5f, -14.5f, -1.5f, -18f, Color.BLACK, 0.8f); line(c, 3.5f, -14.8f, 5.5f, -18f, Color.BLACK, 0.8f)
            circ(c, -1.5f, -18.2f, 1f, Color.BLACK); circ(c, 5.8f, -18.2f, 1f, Color.BLACK)
            circ(c, 3.8f, -10f, 2f, Color.WHITE); circ(c, 4.3f, -10f, 1.2f, rgb(0xFF2B6CB0)); circ(c, 4.5f, -10f, 0.6f, Color.BLACK)
            circ(c, 1.3f, -7.8f, 0.9f, 0x88FF80AB.toInt())
            line(c, 3f, -7.4f, 5f, -7.6f, rgb(0xFFC62828), 0.8f)
        } else {
            c.save(); c.rotate(-30f + flap, -2f, -8f); oval(c, -9f, -17f, 8f, 10f, 0xAAC8E6C9.toInt()); c.restore()
            // tylna noga (zgięta)
            line(c, -3f, -4f, -6.5f, -8.5f, rgb(0xFF2E7D32), 1.8f); line(c, -6.5f, -8.5f, -7.5f - leg * 0.3f, -0.5f, rgb(0xFF2E7D32), 1.6f)
            line(c, 2f, -2f, 2f + leg, 0f, rgb(0xFF2E7D32), 1.2f)
            oval(c, -5.5f, -8.5f, 10.5f, 7f, rgb(0xFF5DBB35))
            oval(c, -3.5f, -5f, 7f, 3f, rgb(0xFFA6E07A))
            circ(c, 3.2f, -9.3f, 4.1f, rgb(0xFF6CCB3F))
            circ(c, 4.4f, -10.6f, 2.4f, Color.WHITE); circ(c, 5f, -10.5f, 1.2f, Color.BLACK)
            line(c, 3f, -7.5f, 5.6f, -7.8f, rgb(0xFF1B5E20), 0.8f)
            line(c, 2.5f, -12.8f, 0.5f, -18f, rgb(0xFF2E7D32), 0.9f); line(c, 4.5f, -13f, 7.5f, -18f, rgb(0xFF2E7D32), 0.9f)
            circ(c, 0.5f, -18f, 0.9f, rgb(0xFF1B5E20)); circ(c, 7.5f, -18f, 0.9f, rgb(0xFF1B5E20))
        }
        c.restore()
    }
}
