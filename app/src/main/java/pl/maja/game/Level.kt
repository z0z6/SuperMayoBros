package pl.maja.game

const val TS = 16f   // rozmiar kafelka
const val LH = 14    // wysokość poziomu w kafelkach (14 * 16 = 224)

object T {
    const val AIR = 0
    const val GROUND = 1
    const val BRICK = 2
    const val QBLOCK = 3
    const val USED = 4
    const val HARD = 5
    const val PIPE_L = 6
    const val PIPE_R = 7
    const val COIN = 8
    const val LEAF = 9
    const val GATE = 10
}

object K {
    const val BEETLE = 1   // chrząszcz  (goomba)
    const val SNAIL = 2    // ślimak     (koopa)
    const val HORNET = 3   // szerszeń   (latający koopa)
    const val SPIDER = 4   // pająk Tekla na nitce (roślina-pułapka)
    const val BOSS = 5     // Tekla - boss
    const val ALEK = 6     // Alek - przyjazny truteń (NPC)
    const val HONEY = 10   // słoik miodu (grzyb)
    const val FLOWER = 11  // kwiat (ognisty kwiat)
    const val ONEUP = 12   // biedronka (1UP)
    const val STAR = 13    // świetlik (gwiazdka)
    const val SHOT = 20    // kulka pyłku
}

class Spawn(val type: Int, val x: Float, val y: Float)

class Level(val code: String, val name: String, val w: Int, val theme: Int) {
    val tiles = Array(LH) { IntArray(w) }
    val contents = HashMap<Int, Int>()   // zawartość bloków ?  (0 pyłek, 1 miód/kwiat, 2 biedronka, 3 świetlik)
    val spawns = ArrayList<Spawn>()
    var goalX = w - 12
    var time = 300
    var hasBoss = false
    var bossMin = 0
    var bossMax = 0

    fun get(x: Int, y: Int): Int {
        if (x < 0 || x >= w) return T.HARD
        if (y < 0 || y >= LH) return T.AIR
        return tiles[y][x]
    }

    fun isSolid(t: Int): Boolean = (t in 1..7) || t == T.LEAF || t == T.GATE

    fun set(x: Int, y: Int, t: Int) {
        if (x in 0 until w && y in 0 until LH) tiles[y][x] = t
    }

    fun ground(x1: Int, x2: Int, top: Int = 12) {
        for (x in x1..x2) for (y in top until LH) tiles[y][x] = T.GROUND
    }

    fun ceiling(rows: Int) {
        for (x in 0 until w) for (y in 0 until rows) tiles[y][x] = T.GROUND
    }

    fun brick(x: Int, y: Int) = set(x, y, T.BRICK)
    fun brickRow(x: Int, y: Int, n: Int) { for (i in 0 until n) set(x + i, y, T.BRICK) }
    fun q(x: Int, y: Int, content: Int) { set(x, y, T.QBLOCK); contents[y * w + x] = content }
    fun hard(x: Int, y: Int) = set(x, y, T.HARD)
    fun leaf(x: Int, y: Int, n: Int) { for (i in 0 until n) set(x + i, y, T.LEAF) }

    /** łodyga (rura) o szerokości 2 kafelków i wysokości h, stoi na ziemi (wiersz 12) */
    fun pipe(x: Int, h: Int) {
        for (y in (12 - h) until 12) { set(x, y, T.PIPE_L); set(x + 1, y, T.PIPE_R) }
    }

    fun stairsUp(x: Int, n: Int) {
        for (i in 0 until n) for (y in (11 - i)..11) set(x + i, y, T.HARD)
    }

    fun stairsDown(x: Int, n: Int) {
        for (i in 0 until n) for (y in (12 - n + i)..11) set(x + i, y, T.HARD)
    }

    fun coin(x: Int, y: Int) { if (get(x, y) == T.AIR) set(x, y, T.COIN) }
    fun coinRow(x: Int, y: Int, n: Int) { for (i in 0 until n) coin(x + i, y) }

    fun gate(x: Int) { for (y in 2..11) set(x, y, T.GATE) }

    /** przeciwnicy stojący na ziemi (row = wiersz powierzchni, na której stoją) */
    fun mob(type: Int, row: Int, vararg xs: Int) {
        for (x in xs) spawns.add(Spawn(type, x.toFloat(), row.toFloat()))
    }

    fun mobF(type: Int, row: Float, vararg xs: Float) {
        for (x in xs) spawns.add(Spawn(type, x, row))
    }
}

object Levels {
    const val COUNT = 4

    fun make(i: Int): Level = when (i) {
        0 -> level1()
        1 -> level2()
        2 -> level3()
        else -> level4()
    }

    // ---------------------------------------------------------------- 1-1
    private fun level1(): Level {
        val l = Level("1-1", "Słoneczna Łąka", 212, 0)
        l.ground(0, 68); l.ground(71, 85); l.ground(89, 153); l.ground(157, 211)

        l.q(16, 8, 0)
        l.brick(20, 8); l.q(21, 8, 1); l.brick(22, 8); l.q(23, 8, 0); l.brick(24, 8)
        l.q(22, 4, 0)
        l.pipe(28, 2); l.pipe(38, 3); l.pipe(46, 4); l.pipe(57, 4)
        l.coinRow(31, 9, 4)
        l.q(64, 8, 2)
        l.coinRow(66, 7, 3)

        l.brickRow(77, 8, 3); l.q(80, 8, 0); l.brickRow(81, 8, 4)
        l.q(81, 4, 3)
        l.coinRow(70, 9, 2)

        l.brick(94, 8); l.q(95, 8, 0); l.brick(96, 8)
        l.pipe(100, 2); l.pipe(108, 3)
        l.brickRow(112, 8, 3); l.q(115, 8, 0); l.brickRow(116, 8, 3)
        l.q(115, 4, 1)
        l.coinRow(120, 9, 5)
        l.coinRow(130, 7, 6)
        l.coinRow(153, 8, 4)

        l.stairsUp(184, 8)
        l.goalX = 200

        l.mob(K.BEETLE, 12, 22, 34, 41, 51, 53, 62, 80, 82, 98, 104, 113, 118, 126, 140, 146, 165, 170, 175)
        l.mob(K.SNAIL, 12, 63, 124, 160)
        l.mobF(K.HORNET, 9.5f, 69.5f, 87f, 133f, 155f)
        l.mob(K.ALEK, 12, 6)
        return l
    }

    // ---------------------------------------------------------------- 1-2
    private fun level2(): Level {
        val l = Level("1-2", "Pod Korzeniami", 190, 1)
        l.ground(0, 60); l.ground(64, 120); l.ground(124, 189)
        l.ceiling(3)

        l.q(8, 8, 1); l.brickRow(10, 8, 3); l.q(13, 8, 0)
        l.pipe(20, 2); l.pipe(30, 3); l.pipe(40, 2)
        l.brickRow(50, 8, 9); l.coinRow(51, 11, 7)
        l.q(54, 4, 1)

        l.stairsUp(66, 4); l.stairsDown(70, 4)
        l.q(75, 8, 0); l.q(76, 8, 3); l.q(77, 8, 0)
        l.brickRow(88, 8, 3); l.q(91, 8, 1)
        l.pipe(96, 3); l.pipe(104, 4); l.pipe(114, 2)
        l.coinRow(130, 8, 5)
        l.brickRow(140, 8, 4); l.q(144, 8, 0); l.brickRow(145, 8, 3)

        l.stairsUp(172, 6)
        l.goalX = 182

        l.mob(K.BEETLE, 12, 15, 24, 34, 44, 54, 56, 80, 84, 95, 100, 107, 128, 134, 142, 150, 158, 164)
        l.mob(K.SNAIL, 12, 90, 110, 138)
        l.mobF(K.SPIDER, 3f, 25f, 35f, 46f, 80f, 99f, 120f, 127f, 150f, 160f)
        l.mobF(K.HORNET, 9.5f, 62.5f, 122f)
        l.mob(K.ALEK, 12, 4)
        l.time = 320
        return l
    }

    // ---------------------------------------------------------------- 1-3
    private fun level3(): Level {
        val l = Level("1-3", "Korony Drzew", 200, 2)
        l.ground(0, 8); l.ground(186, 199)
        val leaves = listOf(
            intArrayOf(10, 10, 5), intArrayOf(18, 10, 4), intArrayOf(25, 9, 6), intArrayOf(34, 8, 4),
            intArrayOf(41, 9, 3), intArrayOf(47, 8, 6), intArrayOf(57, 9, 4), intArrayOf(64, 10, 3),
            intArrayOf(70, 9, 5), intArrayOf(78, 8, 4), intArrayOf(85, 7, 5), intArrayOf(93, 8, 4),
            intArrayOf(100, 9, 3), intArrayOf(106, 8, 6), intArrayOf(115, 9, 4), intArrayOf(122, 10, 3),
            intArrayOf(128, 9, 5), intArrayOf(136, 8, 3), intArrayOf(142, 7, 4), intArrayOf(149, 8, 6),
            intArrayOf(158, 9, 4), intArrayOf(165, 10, 3), intArrayOf(171, 9, 4), intArrayOf(178, 10, 6)
        )
        for (lf in leaves) {
            l.leaf(lf[0], lf[1], lf[2])
            if (lf[2] >= 4) l.coinRow(lf[0] + 1, lf[1] - 2, lf[2] - 2)
        }
        l.q(27, 5, 1); l.q(50, 4, 0); l.q(88, 3, 1); l.q(131, 5, 3); l.q(180, 6, 2)
        l.q(71, 5, 0); l.q(72, 5, 0)

        l.stairsUp(188, 4)
        l.goalX = 194

        l.mob(K.BEETLE, 9, 28)
        l.mob(K.BEETLE, 8, 49, 52, 108, 150)
        l.mob(K.BEETLE, 9, 72, 130)
        l.mob(K.BEETLE, 7, 88)
        l.mob(K.SNAIL, 8, 111, 153)
        l.mob(K.SNAIL, 9, 118)
        l.mobF(K.HORNET, 8f, 16f, 38f, 68f, 97f, 126f, 140f, 156f, 169f, 176f)
        l.mobF(K.HORNET, 6.5f, 23.5f, 54f, 90f, 104f, 134f, 147f, 162f)
        l.mobF(K.SPIDER, 9f, 49f, 81f)
        l.mobF(K.SPIDER, 10f, 131f)
        l.mobF(K.SPIDER, 11f, 180f)
        l.mob(K.ALEK, 12, 4)
        l.time = 340
        return l
    }

    // ---------------------------------------------------------------- 1-4
    private fun level4(): Level {
        val l = Level("1-4", "Gniazdo Szerszeni", 150, 3)
        l.ground(0, 40); l.ground(44, 70); l.ground(73, 149)

        l.q(8, 8, 1); l.brickRow(10, 8, 3); l.q(13, 8, 0)
        l.pipe(22, 3); l.pipe(30, 4)
        l.stairsUp(46, 3); l.stairsDown(50, 3)
        l.brickRow(56, 8, 6); l.q(59, 4, 1)
        l.coinRow(14, 6, 4); l.coinRow(62, 9, 5)
        l.brickRow(98, 8, 4); l.brickRow(120, 8, 4)
        l.q(100, 4, 1)
        l.coinRow(112, 8, 3)

        l.hasBoss = true
        l.bossMin = 92; l.bossMax = 130
        l.gate(132)
        l.goalX = 140

        l.mob(K.BEETLE, 12, 14, 25, 36, 60, 66, 80, 84)
        l.mob(K.SNAIL, 12, 28, 62, 78)
        l.mobF(K.HORNET, 9.5f, 20f, 41.5f, 33f, 55f, 64f, 76f, 86f)
        l.mobF(K.SPIDER, 9f, 58f)
        l.mob(K.BOSS, 12, 112)
        l.mob(K.ALEK, 12, 4)
        l.time = 360
        return l
    }
}
