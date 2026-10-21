package pl.maja.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Ent(var type: Int, var x: Float, var y: Float, var w: Float, var h: Float) {
    var vx = 0f
    var vy = 0f
    var dir = -1
    var alive = true
    var onGround = false
    var knocked = false
    var state = 0
    var timer = 0
    var hp = 1
    var baseX = 0f
    var baseY = 0f
    var range = 0f
    var phase = 0f
    var spin = 0f
    val isEnemy: Boolean get() = type in 1..5
}

class Part(var kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, var color: Int)

class Bump(val tx: Int, val ty: Int) { var t = 0 }

class Game {
    companion object {
        const val TITLE = 0
        const val INTRO = 1
        const val PLAY = 2
        const val DYING = 3
        const val CLEAR = 4
        const val OVER = 5
        const val WIN = 6
    }

    var state = TITLE
    var hero = 0                 // 0 = Maja, 1 = Gucio
    var levelIdx = 0
    var lv: Level = Levels.make(0)
    val ents = ArrayList<Ent>()
    val parts = ArrayList<Part>()
    val bumps = ArrayList<Bump>()
    val p = Ent(0, 0f, 0f, 12f, 14f)
    var big = 0                  // 0 mała, 1 duża, 2 duża + strzela pyłkiem
    var facing = 1
    var invuln = 0
    var starT = 0
    var anim = 0f
    var camX = 0f
    var viewW = 400f
    var score = 0
    var coins = 0
    var lives = 3
    var time = 300f
    var tState = 0
    var clearPhase = 0
    var clearWait = 0
    var playerHidden = false
    var paused = false
    var bossDead = false
    var frame = 0
    private var coyote = 0
    private var jbuf = 0

    // wejście (ustawiane z wątku UI)
    @Volatile var inLeft = false
    @Volatile var inRight = false
    @Volatile var inJump = false
    @Volatile var inRun = false
    private var prevJump = false
    private var prevRun = false
    private var jumpEdge = false
    private var runEdge = false

    // ------------------------------------------------------------------ flow
    fun tap(x: Float, @Suppress("UNUSED_PARAMETER") y: Float) {
        when (state) {
            TITLE -> {
                hero = if (x < viewW / 2f) 0 else 1
                score = 0; coins = 0; lives = 3; big = 0
                loadLevel(0)
            }
            OVER, WIN -> state = TITLE
        }
    }

    private fun loadLevel(idx: Int) {
        levelIdx = idx
        lv = Levels.make(idx)
        ents.clear(); parts.clear(); bumps.clear()
        bossDead = false
        for (s in lv.spawns) ents.add(makeEnt(s))
        p.h = if (big > 0) 28f else 14f
        p.x = 3f * TS
        p.y = 12f * TS - p.h
        p.vx = 0f; p.vy = 0f
        camX = 0f
        time = lv.time.toFloat()
        invuln = 0; starT = 0
        playerHidden = false
        facing = 1
        state = INTRO
        tState = 120
    }

    private fun makeEnt(s: Spawn): Ent {
        val e: Ent = when (s.type) {
            K.BEETLE -> Ent(K.BEETLE, s.x * TS + 1f, s.y * TS - 14f, 14f, 14f)
            K.SNAIL -> Ent(K.SNAIL, s.x * TS + 1f, s.y * TS - 14f, 14f, 14f)
            K.HORNET -> Ent(K.HORNET, s.x * TS, s.y * TS, 14f, 12f)
            K.SPIDER -> Ent(K.SPIDER, s.x * TS + 1f, s.y * TS, 14f, 14f)
            K.BOSS -> Ent(K.BOSS, s.x * TS, s.y * TS - 24f, 28f, 24f)
            else -> Ent(K.ALEK, s.x * TS, s.y * TS - 16f, 14f, 16f)
        }
        e.dir = -1
        when (s.type) {
            K.HORNET -> { e.baseX = e.x; e.baseY = e.y; e.range = 48f; e.phase = s.x }
            K.SPIDER -> { e.baseY = e.y; e.range = if (lv.theme == 1) 116f else 44f; e.phase = s.x * 0.7f }
            K.BOSS -> e.hp = 3
        }
        return e
    }

    fun update() {
        frame++
        jumpEdge = inJump && !prevJump; prevJump = inJump
        runEdge = inRun && !prevRun; prevRun = inRun
        when (state) {
            INTRO -> { tState--; if (tState <= 0) state = PLAY }
            PLAY -> if (!paused) updatePlay()
            DYING -> updateDying()
            CLEAR -> updateClear()
        }
    }

    // ------------------------------------------------------------------ play
    private fun updatePlay() {
        time -= 1f / 24f
        if (time <= 0f) { die(); return }
        if (invuln > 0) invuln--
        if (starT > 0) starT--
        updatePlayer()
        if (state != PLAY) return
        for (i in 0 until ents.size) updateEnt(ents[i])
        playerVsEnts()
        ents.removeAll { !it.alive }
        for (pt in parts) {
            pt.x += pt.vx; pt.y += pt.vy
            if (pt.kind == 0) pt.vy += 0.3f
            pt.life--
        }
        parts.removeAll { it.life <= 0 }
        for (b in bumps) b.t++
        bumps.removeAll { it.t > 8 }
        updateCamera()
        if (state == PLAY && p.x + p.w >= lv.goalX * TS + 6f) startClear()
    }

    private fun updateCamera() {
        val target = p.x + p.w / 2f - viewW * 0.45f
        if (target > camX) camX = target
        camX = camX.coerceIn(0f, max(0f, lv.w * TS - viewW))
    }

    private fun approach(v: Float, target: Float, a: Float): Float =
        if (v < target) min(v + a, target) else max(v - a, target)

    private fun updatePlayer() {
        val dirIn = (if (inRight) 1 else 0) - (if (inLeft) 1 else 0)
        val gucio = hero == 1
        val maxS = if (inRun) 2.5f else 1.5f
        val acc = if (inRun) 0.07f else 0.05f
        val fric = if (gucio) 0.03f else 0.07f
        val skid = if (gucio) 0.09f else 0.16f
        if (dirIn != 0) {
            facing = dirIn
            if (p.vx * dirIn < 0f) {
                p.vx += dirIn * (if (p.onGround) skid else 0.07f)
            } else if (abs(p.vx) > maxS) {
                p.vx = approach(p.vx, dirIn * maxS, 0.03f)
            } else {
                p.vx = approach(p.vx, dirIn * maxS, if (p.onGround) acc else 0.05f)
            }
        } else {
            p.vx = approach(p.vx, 0f, if (p.onGround) fric else 0.01f)
        }

        // skok (z krótkim "coyote time" i buforem)
        if (jumpEdge) jbuf = 5 else if (jbuf > 0) jbuf--
        if (p.onGround) coyote = 4 else if (coyote > 0) coyote--
        if (jbuf > 0 && coyote > 0) {
            val boost = if (gucio) 1.12f else 1f
            p.vy = -(4.0f + abs(p.vx) * 0.3f) * boost
            p.onGround = false
            coyote = 0; jbuf = 0
            Sfx.play("jump")
        }
        val g = if (p.vy < 0f && inJump) 0.15f else 0.44f
        p.vy = min(p.vy + g, 4.8f)

        if (moveX(p)) p.vx = 0f
        if (p.x < camX) { p.x = camX; if (p.vx < 0f) p.vx = 0f }
        val maxX = lv.w * TS - p.w
        if (p.x > maxX) p.x = maxX
        moveY(p, true)

        if (p.y > LH * TS + 8f) { die(); return }

        // wzrost
        if (big > 0 && p.h < 20f && free(p.x, p.y - 14f, p.w, 14f)) { p.y -= 14f; p.h = 28f }

        anim += if (p.onGround) abs(p.vx) * 0.22f else 0.25f

        collectCoins()
        if (runEdge && big == 2) shoot()
    }

    private fun free(x: Float, y: Float, w: Float, h: Float): Boolean {
        val l = floor(x / TS).toInt(); val r = floor((x + w - 0.01f) / TS).toInt()
        val t = floor(y / TS).toInt(); val b = floor((y + h - 0.01f) / TS).toInt()
        for (ty in t..b) for (tx in l..r) if (lv.isSolid(lv.get(tx, ty))) return false
        return true
    }

    private fun collectCoins() {
        val l = floor(p.x / TS).toInt(); val r = floor((p.x + p.w - 0.01f) / TS).toInt()
        val t = floor(p.y / TS).toInt(); val b = floor((p.y + p.h - 0.01f) / TS).toInt()
        for (ty in t..b) for (tx in l..r) {
            if (lv.get(tx, ty) == T.COIN) {
                lv.set(tx, ty, T.AIR)
                addCoin()
            }
        }
    }

    private fun addCoin() {
        coins++; score += 200
        Sfx.play("coin")
        if (coins >= 100) { coins -= 100; lives++; Sfx.play("oneup") }
    }

    private fun shoot() {
        var n = 0
        for (e in ents) if (e.type == K.SHOT && e.alive) n++
        if (n >= 2) return
        val s = Ent(K.SHOT, if (facing > 0) p.x + p.w else p.x - 6f, p.y + p.h * 0.4f, 6f, 6f)
        s.dir = facing
        ents.add(s)
        Sfx.play("shoot")
    }

    // ------------------------------------------------------------ collisions
    private fun moveX(e: Ent): Boolean {
        if (e.vx == 0f) return false
        e.x += e.vx
        val l = floor(e.x / TS).toInt(); val r = floor((e.x + e.w - 0.01f) / TS).toInt()
        val t = floor(e.y / TS).toInt(); val b = floor((e.y + e.h - 0.01f) / TS).toInt()
        val tx = if (e.vx > 0f) r else l
        for (ty in t..b) {
            if (lv.isSolid(lv.get(tx, ty))) {
                e.x = if (e.vx > 0f) tx * TS - e.w else (tx + 1) * TS
                return true
            }
        }
        return false
    }

    private fun moveY(e: Ent, player: Boolean): Boolean {
        e.onGround = false
        e.y += e.vy
        val l = floor(e.x / TS).toInt(); val r = floor((e.x + e.w - 0.01f) / TS).toInt()
        val t = floor(e.y / TS).toInt(); val b = floor((e.y + e.h - 0.01f) / TS).toInt()
        if (e.vy > 0f) {
            for (tx in l..r) {
                if (lv.isSolid(lv.get(tx, b))) {
                    e.y = b * TS - e.h
                    e.vy = 0f
                    e.onGround = true
                    return true
                }
            }
        } else if (e.vy < 0f) {
            var hit = -1
            val cx = floor((e.x + e.w / 2f) / TS).toInt()
            if (lv.isSolid(lv.get(cx, t))) hit = cx
            else for (tx in l..r) if (lv.isSolid(lv.get(tx, t))) { hit = tx; break }
            if (hit >= 0) {
                e.y = (t + 1) * TS
                e.vy = 0f
                if (player) bumpBlock(hit, t)
                return true
            }
        }
        return false
    }

    private fun bumpBlock(tx: Int, ty: Int) {
        when (lv.get(tx, ty)) {
            T.QBLOCK -> {
                val c = lv.contents[ty * lv.w + tx] ?: 0
                lv.set(tx, ty, T.USED)
                bumps.add(Bump(tx, ty))
                spawnContent(tx, ty, c)
            }
            T.BRICK -> {
                if (big > 0) {
                    lv.set(tx, ty, T.AIR)
                    breakParts(tx, ty, lv.theme)
                    score += 50
                    Sfx.play("break")
                } else {
                    bumps.add(Bump(tx, ty))
                    Sfx.play("bump")
                }
            }
            else -> {}
        }
        // przeciwnicy stojący na bloku zostają podrzuceni
        for (e in ents) {
            if (e.alive && !e.knocked && e.isEnemy && e.type != K.BOSS && e.type != K.SPIDER &&
                abs(e.x + e.w / 2f - (tx * TS + 8f)) < 12f && abs(e.y + e.h - ty * TS) < 3f
            ) knock(e)
        }
    }

    private fun spawnContent(tx: Int, ty: Int, c: Int) {
        if (c == 0) {
            addCoin()
            parts.add(Part(1, tx * TS + 4f, ty * TS - 8f, 0f, -4f, 28, 0))
            return
        }
        val type = when (c) {
            1 -> if (big == 0) K.HONEY else K.FLOWER
            2 -> K.ONEUP
            else -> K.STAR
        }
        val item = Ent(type, tx * TS + 1f, ty * TS + 2f, 14f, 14f)
        item.state = 1; item.timer = 16; item.dir = 1
        ents.add(item)
        Sfx.play("item")
    }

    private fun breakParts(tx: Int, ty: Int, theme: Int) {
        val col = when (theme) {
            1 -> 0xFF7A5A48.toInt()
            3 -> 0xFFD9A03A.toInt()
            else -> 0xFFC8743C.toInt()
        }
        val xs = floatArrayOf(-1.5f, 1.5f, -1f, 1f)
        val ys = floatArrayOf(-4.5f, -4.5f, -3f, -3f)
        for (i in 0 until 4) {
            parts.add(Part(0, tx * TS + (if (i % 2 == 0) 2f else 9f), ty * TS + (if (i < 2) 2f else 9f), xs[i], ys[i], 70, col))
        }
    }

    // --------------------------------------------------------------- enemies
    private fun overlapP(e: Ent, m: Float): Boolean =
        p.x < e.x + e.w - m && p.x + p.w > e.x + m && p.y < e.y + e.h - m && p.y + p.h > e.y + m

    private fun overlapEE(a: Ent, b: Ent): Boolean =
        a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y

    private fun knock(o: Ent) {
        o.knocked = true
        o.vy = -3f
        o.vx = if (p.x < o.x) 1f else -1f
        score += 100
        Sfx.play("kick")
    }

    private fun damageBoss(e: Ent) {
        if (e.timer > 0 || e.knocked) return
        e.hp--
        e.timer = 80
        Sfx.play("boss")
        if (e.hp <= 0) {
            e.knocked = true
            e.vy = -4f; e.vx = 0.5f
            bossDead = true
            score += 5000
            openGate()
        }
    }

    private fun openGate() {
        for (y in 0 until LH) for (x in 0 until lv.w) {
            if (lv.tiles[y][x] == T.GATE) {
                lv.tiles[y][x] = T.AIR
                breakParts(x, y, 3)
            }
        }
    }

    private fun updateEnt(e: Ent) {
        if (!e.alive) return
        if (e.x + e.w < camX - 150f) { e.alive = false; return }
        if (e.x > camX + viewW + 24f && e.type < K.HONEY) return
        if (e.knocked) {
            e.vy += 0.3f; e.y += e.vy; e.x += e.vx; e.spin += 0.3f
            if (e.y > LH * TS + 40f) e.alive = false
            return
        }
        when (e.type) {
            K.BEETLE -> {
                if (e.state == -1) { e.timer--; if (e.timer <= 0) e.alive = false; return }
                e.vx = e.dir * 0.5f
                e.vy = min(e.vy + 0.3f, 4f)
                if (moveX(e)) e.dir = -e.dir
                moveY(e, false)
                if (e.y > LH * TS + 16f) e.alive = false
            }
            K.SNAIL -> {
                if (e.timer > 0) e.timer--
                e.vy = min(e.vy + 0.3f, 4f)
                e.vx = when (e.state) { 0 -> e.dir * 0.45f; 1 -> 0f; else -> e.dir * 3f }
                if (moveX(e)) { e.dir = -e.dir; if (e.state == 2) Sfx.play("bump") }
                moveY(e, false)
                if (e.state == 2) {
                    for (o in ents) {
                        if (o !== e && o.alive && !o.knocked && o.isEnemy && o.type != K.BOSS && overlapEE(e, o)) knock(o)
                    }
                }
                if (e.y > LH * TS + 16f) e.alive = false
            }
            K.HORNET -> {
                e.phase += 0.08f
                e.x += e.dir * 0.7f
                if (e.x - e.baseX > e.range) e.dir = -1 else if (e.x - e.baseX < -e.range) e.dir = 1
                e.y = e.baseY + sin(e.phase) * 18f
            }
            K.SPIDER -> {
                e.phase += 0.035f
                e.y = e.baseY + (1f - cos(e.phase)) * 0.5f * e.range
            }
            K.BOSS -> {
                if (e.timer > 0) e.timer--
                e.state++
                e.vy = min(e.vy + 0.3f, 4.5f)
                val spd = 0.6f + (3 - e.hp) * 0.3f
                e.vx = e.dir * spd
                if (moveX(e)) e.dir = -e.dir
                val mn = lv.bossMin * TS
                val mx = lv.bossMax * TS
                if (e.x < mn) { e.x = mn; e.dir = 1 }
                if (e.x + e.w > mx) { e.x = mx - e.w; e.dir = -1 }
                moveY(e, false)
                if (e.onGround && e.state >= 110) { e.vy = -5.2f; e.state = 0 }
            }
            in K.HONEY..K.STAR -> {
                if (e.state == 1) {
                    e.y -= 1f
                    e.timer--
                    if (e.timer <= 0) { e.state = 0; if (e.type == K.STAR) e.vy = -3f }
                    return
                }
                if (e.type != K.FLOWER) {
                    val sp = if (e.type == K.STAR) 1.0f else 0.7f
                    e.vx = e.dir * sp
                    e.vy = min(e.vy + 0.25f, 4f)
                    if (moveX(e)) e.dir = -e.dir
                    moveY(e, false)
                    if (e.type == K.STAR && e.onGround) e.vy = -3.6f
                }
                if (e.y > LH * TS + 16f) { e.alive = false; return }
                if (overlapP(e, 0f)) pickup(e)
            }
            K.SHOT -> {
                e.timer++
                e.vx = e.dir * 3.4f
                e.vy = min(e.vy + 0.28f, 4f)
                if (moveX(e)) { e.alive = false; return }
                moveY(e, false)
                if (e.onGround) e.vy = -2.8f
                if (e.timer > 200 || e.x > camX + viewW + 20f || e.x < camX - 20f || e.y > LH * TS) {
                    e.alive = false; return
                }
                for (o in ents) {
                    if (o.alive && !o.knocked && o.isEnemy && overlapEE(e, o)) {
                        if (o.type == K.BOSS) damageBoss(o) else knock(o)
                        e.alive = false
                        break
                    }
                }
            }
        }
    }

    private fun pickup(e: Ent) {
        e.alive = false
        when (e.type) {
            K.HONEY -> { if (big == 0) { big = 1; Sfx.play("grow") } else Sfx.play("item"); score += 1000 }
            K.FLOWER -> { big = if (big == 0) 1 else 2; score += 1000; Sfx.play("grow") }
            K.ONEUP -> { lives++; Sfx.play("oneup") }
            K.STAR -> { starT = 600; score += 1000; Sfx.play("item") }
        }
    }

    private fun playerVsEnts() {
        if (state != PLAY) return
        for (e in ents) {
            if (!e.alive || e.knocked) continue
            if (!e.isEnemy) continue
            if (e.state == -1) continue
            if (e.type == K.BOSS && e.timer > 0) continue
            if (!overlapP(e, 1.5f)) continue
            val stomp = p.vy > 0f && (p.y + p.h) - e.y < 10f && e.type != K.SPIDER
            if (e.type == K.SNAIL && e.state == 1) {
                if (e.timer > 0) continue
                if (stomp) stompEnemy(e) else kickShell(e)
                continue
            }
            if (e.type == K.SNAIL && e.state == 2 && e.timer > 0) continue
            if (starT > 0) {
                if (e.type == K.BOSS) damageBoss(e) else knock(e)
                continue
            }
            if (stomp) stompEnemy(e) else hurt()
        }
    }

    private fun kickShell(e: Ent) {
        e.state = 2
        e.dir = if (p.x + p.w / 2f < e.x + e.w / 2f) 1 else -1
        e.timer = 10
        Sfx.play("kick")
    }

    private fun stompEnemy(e: Ent) {
        when (e.type) {
            K.BEETLE -> { e.state = -1; e.timer = 20; e.vx = 0f; score += 100 }
            K.SNAIL -> when (e.state) {
                0 -> { e.state = 1; e.h = 12f; e.y += 2f; e.timer = 10; score += 100 }
                1 -> { kickShell(e); score += 400 }
                else -> { e.state = 1; e.vx = 0f; e.timer = 10; score += 100 }
            }
            K.HORNET -> knock(e)
            K.BOSS -> damageBoss(e)
        }
        Sfx.play("stomp")
        p.vy = if (inJump) -4.6f else -3.2f
        p.y = min(p.y, e.y - p.h)
        p.onGround = false
        if (e.type == K.BOSS) p.vx = if (p.x < e.x) -1.6f else 1.6f
    }

    private fun hurt() {
        if (invuln > 0 || starT > 0) return
        if (big > 0) {
            big = 0
            if (p.h > 20f) { p.y += 14f; p.h = 14f }
            invuln = 100
            Sfx.play("shrink")
        } else die()
    }

    private fun die() {
        if (state != PLAY) return
        state = DYING
        tState = 0
        p.vy = -5f
        big = 0
        starT = 0
        if (p.h > 20f) { p.y += 14f; p.h = 14f }
        Sfx.play("die")
    }

    private fun updateDying() {
        tState++
        if (tState > 25) {
            p.vy = min(p.vy + 0.3f, 5f)
            p.y += p.vy
        }
        if (tState > 160) {
            lives--
            if (lives <= 0) state = OVER else loadLevel(levelIdx)
        }
    }

    // ----------------------------------------------------------------- clear
    private fun startClear() {
        state = CLEAR
        clearPhase = 0
        clearWait = 0
        val poleX = lv.goalX * TS + 7f
        p.x = poleX - p.w
        p.vx = 0f; p.vy = 0f
        facing = 1
        score += max(100, ((12f * TS - p.y) / TS * 400f).toInt())
        Sfx.play("clear")
    }

    private fun updateClear() {
        updateCamera()
        when (clearPhase) {
            0 -> {
                p.y += 1.6f
                anim += 0.2f
                if (p.y + p.h >= 12f * TS) {
                    p.y = 12f * TS - p.h
                    p.x = lv.goalX * TS + 9f
                    clearPhase = 1
                }
            }
            1 -> {
                p.x += 1.3f
                anim += 0.25f
                if (p.x > (lv.goalX + 6) * TS) { clearPhase = 2; playerHidden = true }
            }
            else -> {
                if (time > 0f) {
                    val d = min(time, 2f)
                    time -= d
                    score += (d * 50f).toInt()
                    if (frame % 3 == 0) Sfx.play("tick")
                } else {
                    clearWait++
                    if (clearWait > 70) {
                        if (levelIdx + 1 >= Levels.COUNT) state = WIN else loadLevel(levelIdx + 1)
                    }
                }
            }
        }
    }

    @Suppress("unused")
    private fun rnd() = Random.nextFloat()
}
