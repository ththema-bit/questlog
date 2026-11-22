package io.github.ththemabit.questlog.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rechnet den kompletten Spielzustand aus dem gespeicherten Protokoll nach.
 * Tag für Tag: Glut kühlt ab, Fenster rücken weiter, überfällige Quests ziehen Glut ab,
 * Serien halten oder brechen. Ereignisse bringen Punkte nach der Formel in [Scoring].
 */
class Engine(private val zone: ZoneId = ZoneId.systemDefault()) {
    fun compute(save: SaveData, now: Long): GameState = Replay(save, now, zone).execute()

    fun dayOf(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    companion object {
        fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

private class QRun(val q: Quest, var daysSince: Int) {
    var lastFullAt: Long? = null
    var oneOffDone = false
    var doneCount = 0
    val durations = ArrayList<Int>()
    var bestSec: Int? = null
    val hits = LinkedHashSet<String>()
    var hitPoints = 0
    var daysPastDue = 0
    var rageDays = 0
    val eventIds = ArrayList<String>()
}

private data class Status(val status: WindowStatus, val opensIn: Int?, val closesIn: Int?, val overdue: Int)

private class Replay(val save: SaveData, val now: Long, val zone: ZoneId) {
    val today: LocalDate = dayOf(now)
    val quests = save.quests
    val qById = quests.associateBy { it.id }
    val events = save.events.sortedWith(compareBy<Event>({ it.at }, { it.id }))
    val pauses: List<Pair<LocalDate, LocalDate>> = save.pauses.mapNotNull {
        runCatching { LocalDate.parse(it.from) to (it.to?.let(LocalDate::parse) ?: LocalDate.MAX) }.getOrNull()
    }
    val hardcoreWeeks: Set<LocalDate> = save.hardcoreWeeks.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    val chaos = save.settings.chaos

    val runs = LinkedHashMap<String, QRun>()

    var xp = 0L
    var glut = 0.0
    var glutBest = 0.0
    var glutBestDay: LocalDate? = null
    var streak = 0
    var bestStreak = 0
    var gap = 0
    var shields = 1
    var inactiveRun = 0
    var activeDays = 0
    var lastActionAt: Long? = null
    var chainIndex = 0
    var everActive = false

    // Tageswerte
    var dayXp = 0
    var dayCount = 0
    var dayMini = 0
    val dayCat = HashMap<String, Int>()
    var dayActive = false
    var dayDecay = 0.0
    var dayDrain = 0.0
    var bountyId: String? = null
    var bountyClaimed = false
    var comebackPending = false

    // Verläufe
    val days = LinkedHashMap<LocalDate, DayStat>()
    val weeks = LinkedHashMap<LocalDate, Int>()
    val months = LinkedHashMap<YearMonth, Int>()
    val weekTax = HashMap<LocalDate, DoubleArray>()
    val weekGlutStart = HashMap<LocalDate, Int>()
    val weekGlutEnd = HashMap<LocalDate, Int>()
    val weekCats = HashMap<LocalDate, MutableSet<String>>()
    val weekQuestPts = HashMap<LocalDate, MutableMap<String, Int>>()
    val weekActiveDays = HashMap<LocalDate, Int>()
    val weekDone = HashMap<LocalDate, Int>()
    val weekBosses = HashMap<LocalDate, MutableList<String>>()
    val weekRecord = HashSet<LocalDate>()
    val goals = HashMap<LocalDate, Int>()
    val goalHit = HashSet<LocalDate>()
    val seasonBonusGiven = HashSet<LocalDate>()
    var firstActiveWeek: LocalDate? = null
    val catXp = HashMap<String, Long>()
    val results = LinkedHashMap<String, DoneResult>()
    val lootLog = ArrayList<Pair<Long, Loot>>()
    var jokers = 0
    var lootCoins = 0
    val trophies = ArrayList<Pair<String, Long>>()
    val purchases = ArrayList<Buy>()
    var spent = 0

    // Wette
    var wagerEvent: Wager? = null
    var wagerStake = 0
    var lastWager: WagerState? = null

    // Hochzeiten
    var lastFull: Pair<String, Long>? = null
    val pairCounts = HashMap<Pair<String, String>, Int>()
    val marriages = LinkedHashMap<Pair<String, String>, Marriage>()

    // Gerald
    val geraldDays = HashMap<LocalDate, Int>()
    var geraldBeatenWeeks = 0

    // Meilensteine
    val ach = AchStats()
    val unlocked = LinkedHashMap<String, Long>()

    fun dayOf(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun weekStart(d: LocalDate): LocalDate = Engine.weekStart(d)
    fun isPaused(d: LocalDate): Boolean = pauses.any { !d.isBefore(it.first) && !d.isAfter(it.second) }
    fun isHardcore(d: LocalDate): Boolean = weekStart(d) in hardcoreWeeks
    fun endOfDay(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    fun hourOf(at: Long): Int = Instant.ofEpochMilli(at).atZone(zone).hour

    fun execute(): GameState {
        val firstDay = listOfNotNull(
            events.firstOrNull()?.let { dayOf(it.at) },
            quests.minOfOrNull { dayOf(it.createdAt) },
        ).minOrNull()?.let { if (it.isAfter(today)) today else it } ?: today
        val byDay = events.groupBy { val d = dayOf(it.at); if (d.isAfter(today)) today else d }
        var day = firstDay
        while (!day.isAfter(today)) {
            startDay(day, day == firstDay)
            byDay[day]?.forEach { handle(it, day) }
            if (day.isBefore(today)) endDay(day)
            day = day.plusDays(1)
        }
        resolveWagerIfExpired(now)
        return build(firstDay)
    }

    // ---------------------------------------------------------------- Tageslauf

    fun startDay(day: LocalDate, first: Boolean) {
        val paused = isPaused(day)
        if (!first && !paused) {
            for (r in runs.values) {
                if (r.oneOffDone) continue
                r.daysSince++
                val due = r.q.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (!r.q.recurring && due != null && day.isAfter(due)) r.daysPastDue++
                if (r.q.boss) r.rageDays++
            }
            val before = glut
            glut *= Rules.GLUT_DECAY
            dayDecay = before - glut
            applyFloor()
        } else {
            dayDecay = 0.0
        }
        for (q in quests) {
            if (!runs.containsKey(q.id) && !dayOf(q.createdAt).isAfter(day)) runs[q.id] = newRun(q)
        }
        dayDrain = 0.0
        val ws = weekStart(day)
        weekGlutStart.putIfAbsent(ws, glut.roundToInt())
        bountyId = if (paused) null else pickBounty(day)
        bountyClaimed = false
        dayXp = 0
        dayCount = 0
        dayMini = 0
        dayCat.clear()
        dayActive = false
        comebackPending = false
    }

    fun newRun(q: Quest) = QRun(q, q.minDays ?: 0)

    fun endDay(day: LocalDate) {
        val paused = isPaused(day)
        val ws = weekStart(day)
        if (!paused) {
            val before = glut
            for (r in runs.values) {
                if (r.q.archived) continue
                if (statusOf(r, day).status == WindowStatus.OVERDUE) {
                    glut -= r.q.importance * Rules.OVERDUE_DRAIN_PER_IMPORTANCE.toDouble()
                }
            }
            applyFloor()
            dayDrain = max(0.0, before - glut)
            if (!dayActive) {
                inactiveRun++
                gap++
                if (gap >= 2 && streak > 0) {
                    if (!isHardcore(day) && shields > 0) shields-- else streak = 0
                }
            }
            // Faulheitssteuer: Abkühlung zählt nur an Tagen ohne Quest, Säumniszuschläge immer
            val tax = weekTax.getOrPut(ws) { DoubleArray(2) }
            if (!dayActive) tax[0] += dayDecay
            tax[1] += dayDrain
        }
        days[day] = DayStat(dayXp, dayCount, dayMini, HashMap(dayCat))
        geraldDays[day] = geraldFor(day)
        weekGlutEnd[ws] = glut.roundToInt()
        if (day.dayOfWeek == DayOfWeek.SUNDAY) finalizeWeek(ws)
        checkAchievements(endOfDay(day))
    }

    fun finalizeWeek(ws: LocalDate) {
        val xpWeek = weeks[ws] ?: 0
        val goal = goalFor(ws)
        if (isHardcore(ws) && xpWeek >= goal) ach.hardcoreWins++
        val prev = weeks[ws.minusWeeks(1)] ?: 0
        if (prev > 0 && xpWeek - prev >= 500) ach.ghostBusts++
        val gWeek = (0L..6L).sumOf { geraldDays[ws.plusDays(it)] ?: 0 }
        if (xpWeek > gWeek && xpWeek > 0) {
            geraldBeatenWeeks++
            ach.geraldBeaten++
        }
        val bestBefore = weeks.filterKeys { it.isBefore(ws) }.values.maxOrNull() ?: 0
        if (xpWeek > bestBefore && bestBefore > 0) weekRecord += ws
    }

    fun applyFloor() {
        glut = max(glut, glutBest * Rules.GLUT_FLOOR_SHARE)
        if (glut < 0) glut = 0.0
    }

    // ---------------------------------------------------------------- Fenster

    fun statusOf(r: QRun, day: LocalDate): Status {
        val q = r.q
        if (!q.recurring) {
            if (r.oneOffDone) return Status(WindowStatus.DONE, null, null, 0)
            val due = q.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (due != null && day.isAfter(due)) return Status(WindowStatus.OVERDUE, null, null, max(1, r.daysPastDue))
            return Status(WindowStatus.OPEN, null, due?.let { ChronoUnit.DAYS.between(day, it).toInt() }, 0)
        }
        val ds = r.daysSince
        if (q.minDays != null && ds < q.minDays) return Status(WindowStatus.EARLY, q.minDays - ds, q.maxDays?.let { it - ds }, 0)
        if (q.maxDays != null && ds > q.maxDays) return Status(WindowStatus.OVERDUE, null, null, ds - q.maxDays)
        return Status(WindowStatus.OPEN, null, q.maxDays?.let { it - ds }, 0)
    }

    fun pickBounty(day: LocalDate): String? {
        var best: QRun? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (r in runs.values) {
            if (r.q.archived || r.q.boss) continue
            val st = statusOf(r, day)
            if (st.status != WindowStatus.OPEN && st.status != WindowStatus.OVERDUE) continue
            val tie = (Rng.hash(day.toString() + r.q.id) and 0xffff) / 65536.0
            val score = st.overdue * 3.0 + r.q.dread * 2.0 + r.q.importance + tie
            if (score > bestScore) {
                bestScore = score
                best = r
            }
        }
        return best?.q?.id
    }

    // ---------------------------------------------------------------- Ereignisse

    fun handle(e: Event, day: LocalDate) {
        resolveWagerIfExpired(e.at)
        when (e) {
            is Done -> handleDone(e, day)
            is Hit -> handleHit(e, day)
            is Buy -> {
                purchases += e
                spent += e.price
                ach.purchases++
                checkAchievements(e.at)
            }
            is Wager -> placeWager(e)
        }
    }

    fun runFor(questId: String): QRun? {
        runs[questId]?.let { return it }
        val q = qById[questId] ?: return null
        return newRun(q).also { runs[q.id] = it }
    }

    /** Erste Aktivität des Tages: Serie, Comeback, aktive Tage. */
    fun markActive(day: LocalDate) {
        if (dayActive) return
        dayActive = true
        activeDays++
        ach.activeDays = activeDays.toLong()
        comebackPending = everActive && inactiveRun >= Rules.COMEBACK_DAYS
        if (everActive && inactiveRun >= 7) ach.phoenix++
        everActive = true
        inactiveRun = 0
        streak += 1
        gap = 0
        if (streak > bestStreak) bestStreak = streak
        ach.bestStreak = bestStreak.toLong()
        if (streak % Rules.SHIELD_EVERY_STREAK_DAYS == 0 && shields < Rules.SHIELD_MAX) shields++
        if (firstActiveWeek == null) firstActiveWeek = weekStart(day)
        val ws = weekStart(day)
        weekActiveDays[ws] = (weekActiveDays[ws] ?: 0) + 1
    }

    fun takeComeback(): Boolean = comebackPending.also { comebackPending = false }

    fun seasonBonus(day: LocalDate): Int {
        val ws = weekStart(day)
        if (ws in seasonBonusGiven) return 0
        seasonBonusGiven += ws
        return (goalFor(ws) * Rules.SEASON_START_SHARE).roundToInt()
    }

    fun goalFor(ws: LocalDate): Int = goals.getOrPut(ws) {
        val first = firstActiveWeek
        val prev = (4 downTo 1).map { ws.minusWeeks(it.toLong()) }
            .filter { first != null && !it.isBefore(first) }
            .map { weeks[it] ?: 0 }
        Rules.weeklyGoal(prev)
    }

    fun nextChain(at: Long): Int {
        val last = lastActionAt
        chainIndex = if (last != null && at - last in 0..Rules.COMBO_WINDOW_MS) chainIndex + 1 else 0
        lastActionAt = at
        if (chainIndex > ach.comboMax) ach.comboMax = min(chainIndex, 3).toLong()
        return chainIndex
    }

    fun handleDone(e: Done, day: LocalDate) {
        val r = runFor(e.questId) ?: return
        val q = r.q
        r.eventIds += e.id
        if (e.backfilled) ach.backfills++
        val glutBefore = glut.roundToInt()
        if (e.mini) {
            if (isHardcore(day)) {
                results[e.id] = DoneResult(e.id, q.id, q.title, e.at, 0, listOf(Line("Hardcore-Woche", "Mini zählt nicht")),
                    mini = true, hit = false, finished = false, bossDefeated = false, loot = null,
                    glutBefore = glutBefore, glutAfter = glutBefore, wagerWon = null, newPb = false, early = false)
                return
            }
            markActive(day)
            val comeback = takeComeback()
            val sb = seasonBonus(day)
            val base = Rules.miniPoints(streak)
            val pts = base * (if (comeback) 2 else 1) + sb
            val lines = buildList {
                add(Line("Mini-Version", Rules.base(2).roundToInt().toString()))
                add(Line("Serie Tag $streak", Scoring.x(Rules.streakFactor(streak))))
                if (comeback) add(Line("Comeback", Scoring.x(Rules.COMEBACK)))
                if (sb > 0) add(Line("Saisonauftakt", "+$sb"))
            }
            award(pts, q, day, mini = true)
            ach.minis++
            results[e.id] = DoneResult(e.id, q.id, q.title, e.at, pts, lines, mini = true, hit = false, finished = false,
                bossDefeated = false, loot = null, glutBefore = glutBefore, glutAfter = glut.roundToInt(), wagerWon = null,
                newPb = false, early = false)
            checkAchievements(e.at)
            return
        }
        if (q.recurring.not() && r.oneOffDone) return
        val st = statusOf(r, day)
        markActive(day)
        val comeback = takeComeback()
        val chain = nextChain(e.at)
        val bounty = e.questId == bountyId && !bountyClaimed
        val spin = e.spinAt
        val roulette = spin != null && e.at - spin in 0..Rules.ROULETTE_WINDOW_MS
        val joker = Modifiers.JOKER in e.mods && jokers > 0
        val holiday = if (Chaos.CHAOS_HOLIDAYS in e.chaos) Holidays.matching(day, q) else null
        val wedding = Chaos.CHAOS_WEDDINGS in e.chaos && weddingApplies(q.id, e.at)
        val sb = seasonBonus(day)
        val scored = Scoring.compute(
            ScoreInput(q, st.status, st.overdue, streak, chain, bounty, roulette, comeback,
                e.mods.filter { it != Modifiers.JOKER }, e.durationSec, joker, holiday, wedding, isHardcore(day), sb)
        )
        var points = scored.points
        val lines = scored.lines.toMutableList()
        if (r.hitPoints > 0) {
            val rest = max((scored.raw * Rules.FINISH_SHARE).roundToInt(), points - r.hitPoints)
            lines += Line("Teilaufgaben schon gutgeschrieben", "−${points - rest}")
            points = rest
        }
        if (bounty) {
            bountyClaimed = true
            ach.bounties++
        }
        if (joker) jokers--
        if (st.status == WindowStatus.EARLY) ach.earlyDone++
        if (Chaos.CHAOS_CURSE in e.chaos && st.status == WindowStatus.OVERDUE && st.overdue >= Rules.curseThreshold(q)) ach.curseBroken++
        if (Modifiers.TANZ in e.mods) ach.dances++
        e.shout?.let { if (it > ach.shoutMax) ach.shoutMax = it.toLong() }
        if (q.dread >= 5) ach.dread5++
        val h = hourOf(e.at)
        if (!e.backfilled && h < 4) ach.nightOwl++
        if (!e.backfilled && h in 4..5) ach.earlyBird++

        award(points, q, day, mini = false)
        dayCount++
        val ws = weekStart(day)
        weekDone[ws] = (weekDone[ws] ?: 0) + 1
        if (dayCount >= 10) ach.tenInDay++
        ach.doneFull++

        var newPb = false
        e.durationSec?.let { d ->
            r.durations += d
            val best = r.bestSec
            if (best == null || d < best) {
                if (best != null) {
                    newPb = true
                    ach.pbs++
                }
                r.bestSec = d
            }
        }
        finishQuest(r, e.at)
        val wagerWon = resolveWagerOnDone(q.id, e.at)
        val loot = rollLoot(e.id, q, boss = false, at = e.at)
        trackPair(q.id, e.at)
        results[e.id] = DoneResult(e.id, q.id, q.title, e.at, points, lines, mini = false, hit = false, finished = true,
            bossDefeated = false, loot = loot, glutBefore = glutBefore, glutAfter = glut.roundToInt(), wagerWon = wagerWon,
            newPb = newPb, early = st.status == WindowStatus.EARLY)
        checkAchievements(e.at)
    }

    fun handleHit(e: Hit, day: LocalDate) {
        val r = runFor(e.questId) ?: return
        val q = r.q
        val sub = q.subtasks.firstOrNull { it.id == e.subtaskId } ?: return
        if (sub.id in r.hits || r.oneOffDone) return
        r.eventIds += e.id
        val glutBefore = glut.roundToInt()
        val st = statusOf(r, day)
        markActive(day)
        val comeback = takeComeback()
        val chain = nextChain(e.at)
        val holiday = if (Chaos.CHAOS_HOLIDAYS in e.chaos) Holidays.matching(day, q) else null
        val sb = seasonBonus(day)
        val scored = Scoring.compute(
            ScoreInput(q, st.status, st.overdue, streak, chain, bounty = false, roulette = false, comeback = comeback,
                mods = emptyList(), durationSec = null, joker = false, holiday = holiday, wedding = false,
                hardcore = isHardcore(day), seasonBonus = 0)
        )
        val totalW = q.subtasks.sumOf { max(1, it.weight) }
        val share = max(1, sub.weight).toDouble() / totalW
        val hitPts = max(1, (scored.raw * share * Rules.HIT_SHARE).roundToInt())
        r.hits += sub.id
        r.hitPoints += hitPts
        r.rageDays = 0
        val lines = ArrayList<Line>()
        lines += Line(if (q.boss) "Treffer: ${sub.title}" else "Teilaufgabe: ${sub.title}", "${(share * 100).roundToInt()} % von ${scored.raw.roundToInt()}")
        lines += Line("Anteil Teilaufgaben", "×0,70")
        lines.addAll(scored.lines.drop(3))
        var total = hitPts + sb
        if (sb > 0) lines += Line("Saisonauftakt", "+$sb")
        var finished = false
        var loot: Loot? = null
        var wagerWon: Int? = null
        if (q.subtasks.all { it.id in r.hits }) {
            val rage = if (q.boss) bossRage(r) else 0.0
            val finish = (scored.raw * Rules.FINISH_SHARE * (1 - rage)).roundToInt()
            lines += Line(if (q.boss) "Boss besiegt" else "Alles erledigt", "+$finish")
            if (rage > 0) lines += Line("Boss-Zorn", "−${(rage * 100).roundToInt()} %")
            total += finish
            finished = true
            dayCount++
            val ws = weekStart(day)
            weekDone[ws] = (weekDone[ws] ?: 0) + 1
            ach.doneFull++
            if (q.boss) {
                ach.bossDefeats++
                weekBosses.getOrPut(ws) { ArrayList() } += q.title
            }
            finishQuest(r, e.at)
            wagerWon = resolveWagerOnDone(q.id, e.at)
            loot = rollLoot(e.id, q, boss = q.boss, at = e.at)
            trackPair(q.id, e.at)
        }
        award(total, q, day, mini = false)
        results[e.id] = DoneResult(e.id, q.id, q.title, e.at, total, lines, mini = false, hit = true, finished = finished,
            bossDefeated = finished && q.boss, loot = loot, glutBefore = glutBefore, glutAfter = glut.roundToInt(),
            wagerWon = wagerWon, newPb = false, early = st.status == WindowStatus.EARLY)
        checkAchievements(e.at)
    }

    fun bossRage(r: QRun): Double =
        min(Rules.BOSS_RAGE_MAX, max(0, r.rageDays - Rules.BOSS_RAGE_GRACE_DAYS) * Rules.BOSS_RAGE_PER_DAY)

    fun finishQuest(r: QRun, at: Long) {
        r.lastFullAt = at
        r.daysSince = 0
        r.doneCount++
        r.hits.clear()
        r.hitPoints = 0
        r.rageDays = 0
        if (!r.q.recurring) r.oneOffDone = true
    }

    fun award(points: Int, q: Quest, day: LocalDate, mini: Boolean) {
        xp += points
        ach.xp = xp
        dayXp += points
        if (mini) dayMini++
        dayCat[q.category] = (dayCat[q.category] ?: 0) + points
        catXp[q.category] = (catXp[q.category] ?: 0L) + points
        glut += points
        if (glut > glutBest) {
            glutBest = glut
            glutBestDay = day
        }
        ach.glutMax = max(ach.glutMax, glut.roundToInt().toLong())
        val ws = weekStart(day)
        weeks[ws] = (weeks[ws] ?: 0) + points
        months[YearMonth.from(day)] = (months[YearMonth.from(day)] ?: 0) + points
        val cats = weekCats.getOrPut(ws) { HashSet() }
        cats += q.category
        ach.categoriesWeekMax = max(ach.categoriesWeekMax, cats.size.toLong())
        val qp = weekQuestPts.getOrPut(ws) { HashMap() }
        qp[q.id] = (qp[q.id] ?: 0) + points
        if (ws !in goalHit && (weeks[ws] ?: 0) >= goalFor(ws)) {
            goalHit += ws
            ach.goalsReached++
        }
    }

    // ---------------------------------------------------------------- Wette, Beute, Hochzeit

    fun placeWager(e: Wager) {
        if (wagerEvent != null) return
        val stake = min(Rules.wagerStake(glut.roundToInt()), glut.roundToInt())
        if (stake <= 0) return
        glut -= stake
        wagerEvent = e
        wagerStake = stake
        lastWager = WagerState(e.id, e.questId, stake, e.deadline, resolved = false, won = false)
    }

    fun resolveWagerIfExpired(t: Long) {
        val w = wagerEvent ?: return
        if (t > w.deadline) {
            lastWager = WagerState(w.id, w.questId, wagerStake, w.deadline, resolved = true, won = false)
            wagerEvent = null
        }
    }

    fun resolveWagerOnDone(questId: String, at: Long): Int? {
        val w = wagerEvent ?: return null
        if (w.questId != questId || at > w.deadline) return null
        val back = wagerStake * 2
        glut += back
        if (glut > glutBest) glutBest = glut
        lastWager = WagerState(w.id, w.questId, wagerStake, w.deadline, resolved = true, won = true)
        wagerEvent = null
        ach.wagerWins++
        return back
    }

    fun rollLoot(eventId: String, q: Quest, boss: Boolean, at: Long): Loot? {
        val rng = Rng(eventId)
        val chance = if (boss) 1.0 else Rules.LOOT_BASE_CHANCE + 0.02 * (q.dread - 3)
        if (rng.nextDouble() >= chance) return null
        val roll = rng.nextInt(100)
        val loot = when {
            roll < 25 && shields < Rules.SHIELD_MAX -> {
                shields++
                Loot(LootKind.SHIELD, 1, "Schutzschild", rare = false)
            }
            roll < 55 -> {
                val c = 25 + rng.nextInt(96)
                lootCoins += c
                Loot(LootKind.COINS, c, "Münzbeutel", rare = false)
            }
            roll < 75 -> {
                val g = 40 + rng.nextInt(61)
                glut += g
                if (glut > glutBest) glutBest = glut
                Loot(LootKind.GLUT, g, "Glutfunke", rare = false)
            }
            roll < 90 -> {
                jokers++
                Loot(LootKind.JOKER, 1, "Glückskarte", rare = false)
            }
            else -> {
                val t = Trophies.pick(rng)
                trophies += t.name to at
                Loot(LootKind.TROPHY, 1, t.name, t.rare)
            }
        }
        lootLog += at to loot
        ach.loot++
        return loot
    }

    fun weddingApplies(questId: String, at: Long): Boolean {
        val lf = lastFull ?: return false
        if (lf.first == questId || at - lf.second > Rules.WEDDING_WINDOW_MS) return false
        return marriages.containsKey(lf.first to questId)
    }

    fun trackPair(questId: String, at: Long) {
        val lf = lastFull
        if (lf != null && lf.first != questId && at - lf.second in 0..Rules.WEDDING_WINDOW_MS) {
            val key = lf.first to questId
            val c = (pairCounts[key] ?: 0) + 1
            pairCounts[key] = c
            val existing = marriages[key]
            if (existing != null) {
                marriages[key] = existing.copy(count = c)
            } else if (c >= Rules.WEDDING_NEEDED && !marriages.containsKey(questId to lf.first) &&
                marriages.keys.none { it.first == key.first || it.second == key.second }
            ) {
                marriages[key] = Marriage(key.first, key.second, at, c)
                ach.marriages++
            }
        }
        lastFull = questId to at
    }

    // ---------------------------------------------------------------- Gerald

    fun geraldFor(day: LocalDate): Int {
        val seed = save.meta.seed + day.toEpochDay()
        val rng = Rng(seed * 31 + 7)
        val avg = (1L..14L).map { days[day.minusDays(it)]?.xp ?: 0 }.average()
        return if (avg < 5) 30 + rng.nextInt(50) else (avg * (1.02 + rng.nextDouble() * 0.16)).roundToInt()
    }

    // ---------------------------------------------------------------- Meilensteine

    fun checkAchievements(at: Long) {
        for (a in Achievements.all) {
            if (a.id in unlocked) continue
            if (a.value(ach) >= a.target) unlocked[a.id] = at
        }
    }

    // ---------------------------------------------------------------- Ergebnis

    fun build(firstDay: LocalDate): GameState {
        days[today] = DayStat(dayXp, dayCount, dayMini, HashMap(dayCat))
        val ws = weekStart(today)
        weekGlutEnd[ws] = glut.roundToInt()
        val paused = isPaused(today)
        val hardcore = isHardcore(today)
        val goal = goalFor(ws)
        val lastAt = lastActionAt
        val chainNow = if (lastAt != null && now - lastAt in 0..Rules.COMBO_WINDOW_MS) chainIndex + 1 else 0
        val streakNow = if (dayActive) streak else streak + 1
        val comebackNow = !dayActive && everActive && inactiveRun >= Rules.COMEBACK_DAYS
        val seasonNow = if (ws in seasonBonusGiven) 0 else (goal * Rules.SEASON_START_SHARE).roundToInt()
        val holidayToday = if (chaos.holidays) Holidays.on(today) else null
        val snapshot = ScoreSnapshot(
            streakIfDoneNow = streakNow,
            chainIndexNow = chainNow,
            bountyId = if (bountyClaimed) null else bountyId,
            comebackNow = comebackNow,
            hardcoreNow = hardcore,
            seasonBonusNow = seasonNow,
            holidayFor = { q -> holidayToday?.takeIf { it.appliesTo(q) } },
            weddingFor = { id -> chaos.weddings && weddingApplies(id, now) },
            jokers = jokers,
        )

        val curseCandidates = ArrayList<Pair<QRun, Status>>()
        val states = ArrayList<QuestState>()
        for (q in quests) {
            if (q.archived) continue
            val r = runs[q.id] ?: newRun(q)
            val st = statusOf(r, today)
            val isBounty = !bountyClaimed && bountyId == q.id
            val totalW = q.subtasks.sumOf { max(1, it.weight) }
            val doneW = q.subtasks.filter { it.id in r.hits }.sumOf { max(1, it.weight) }
            val dust = if (st.status == WindowStatus.OVERDUE) when {
                st.overdue >= 7 -> 3
                st.overdue >= 3 -> 2
                else -> 1
            } else 0
            if (st.status == WindowStatus.OVERDUE && st.overdue >= Rules.curseThreshold(q)) curseCandidates += r to st
            val partner = marriages.keys.firstOrNull { it.first == q.id || it.second == q.id }
                ?.let { if (it.first == q.id) it.second else it.first }
            val urgency = when (st.status) {
                WindowStatus.OVERDUE -> 1000.0 + st.overdue * 10 + q.importance * 5
                WindowStatus.OPEN -> 500.0 - (st.closesIn ?: 30) * 10 + q.importance * 3 + q.dread
                WindowStatus.EARLY -> -(st.opensIn ?: 0).toDouble()
                WindowStatus.DONE -> -1000.0
            }
            val pre = QuestState(
                quest = q, status = st.status, daysSince = r.daysSince, opensIn = st.opensIn, closesIn = st.closesIn,
                overdueDays = st.overdue, lastDone = r.lastFullAt, doneCount = r.doneCount, bestSec = r.bestSec,
                avgSec = if (r.durations.isEmpty()) null else r.durations.average().roundToInt(),
                hitsThisCycle = HashSet(r.hits), bossHpLeft = totalW - doneW, bossHpMax = totalW,
                bossRage = if (q.boss) bossRage(r) else 0.0, dust = dust, isBounty = isBounty, cursed = false,
                partnerId = partner, preview = 0, miniPreview = snapshot.mini(), urgency = urgency,
                history = ArrayList(r.eventIds),
            )
            states += pre.copy(preview = snapshot.full(pre))
        }
        val cursed = if (chaos.comicCurse) curseCandidates.maxByOrNull { it.second.overdue } else null
        val finalStates = if (cursed == null) states else states.map {
            if (it.quest.id == cursed.first.q.id) it.copy(cursed = true) else it
        }

        val visible = finalStates
        val board = Board(
            overdue = visible.filter { it.status == WindowStatus.OVERDUE && !it.quest.boss }.sortedByDescending { it.urgency },
            open = visible.filter { it.status == WindowStatus.OPEN && !it.quest.boss }.sortedByDescending { it.urgency },
            bosses = visible.filter { it.quest.boss && it.status != WindowStatus.DONE }.sortedByDescending { it.urgency },
            early = visible.filter { it.status == WindowStatus.EARLY && !it.quest.boss }.sortedBy { it.opensIn ?: 0 },
            done = visible.filter { it.status == WindowStatus.DONE }.sortedByDescending { it.lastDone ?: 0 },
        )

        val level = Rules.levelForXp(xp)
        val coins = (xp + lootCoins - spent).toInt()
        val player = PlayerState(
            xp = xp, level = level, levelStartXp = Rules.xpForLevel(level), nextLevelXp = Rules.xpForLevel(level + 1),
            rank = Ranks.forLevel(level), nextRank = Ranks.next(level), glut = glut.roundToInt(),
            glutBest = glutBest.roundToInt(), glutFloor = (glutBest * Rules.GLUT_FLOOR_SHARE).roundToInt(),
            streak = streak, streakAtRisk = !dayActive && gap >= 1 && streak > 0, bestStreak = bestStreak,
            shields = shields, coins = coins, comebackReady = comebackNow, comboIndex = chainNow,
            comboUntil = lastAt?.let { it + Rules.COMBO_WINDOW_MS }?.takeIf { it > now },
            todayXp = dayXp, todayCount = dayCount, activeDays = activeDays,
        )

        // Saison & Geist
        val weekXp = weeks[ws] ?: 0
        val ghostFrom = ws.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ghostTo = now - 7L * 24 * 3600 * 1000
        val ghostXp = results.values.filter { it.at in ghostFrom..ghostTo }.sumOf { it.points }
        val season = Season(ws, weekXp, goal, ghostXp, 6 - (today.dayOfWeek.value - 1), hardcore, weeks[ws.minusWeeks(1)] ?: 0)

        // Rückblick auf die Vorwoche
        val prevWs = ws.minusWeeks(1)
        val recap = if ((weekActiveDays[prevWs] ?: 0) > 0) buildRecap(prevWs) else null

        // Rekorde
        val bestDay = days.filterValues { it.xp > 0 }.maxByOrNull { it.value.xp }?.let { it.key to it.value.xp }
        val bestWeek = weeks.filterValues { it > 0 }.maxByOrNull { it.value }?.let { it.key to it.value }
        val bestMonth = months.filterValues { it > 0 }.maxByOrNull { it.value }?.let { it.key to it.value }
        val mostDay = days.filterValues { it.count > 0 }.maxByOrNull { it.value.count }?.let { it.key to it.value.count }
        val fastest = runs.values.filter { it.bestSec != null && !it.q.archived }
            .sortedBy { it.bestSec }.map { Triple(it.q.id, it.q.title, it.bestSec!!) }
        val records = Records(bestDay, bestWeek, bestMonth, bestStreak, glutBestDay?.let { it to glutBest.roundToInt() }, mostDay, fastest)

        val achievements = Achievements.all.map { a ->
            AchievementState(a, unlocked[a.id], a.value(ach) to a.target)
        }
        val skills = Categories.all.mapNotNull { c ->
            val cx = catXp[c.key] ?: return@mapNotNull null
            val lv = Rules.skillLevel(cx)
            SkillState(c, cx, lv, Rules.xpForSkill(lv), Rules.xpForSkill(lv + 1))
        }.sortedByDescending { it.xp }

        // Gerald
        val gToday = geraldFor(today)
        val dayFraction = (now - today.atStartOfDay(zone).toInstant().toEpochMilli()) / 86_400_000.0
        val gTodayNow = (gToday * dayFraction.coerceIn(0.0, 1.0)).roundToInt()
        val gWeek = (0L until (today.dayOfWeek.value - 1).toLong()).sumOf { geraldDays[ws.plusDays(it)] ?: geraldFor(ws.plusDays(it)) } + gTodayNow
        val gRng = Rng(save.meta.seed + today.toEpochDay() * 13)
        val lie = gRng.nextInt(10) == 0
        val gerald = GeraldState(gTodayNow, gWeek, GeraldLines.pick(gRng, weekXp, gWeek, lie), lie, geraldBeatenWeeks)

        val curse = cursed?.let { Curse(it.first.q.id, it.first.q.title, it.second.overdue) }
        val dust = if (!chaos.dustBunnies) 0 else min(
            Rules.DUST_MAX,
            finalStates.filter { it.status == WindowStatus.OVERDUE && it.quest.category in Rules.DUST_CATEGORIES }.sumOf { it.overdueDays }
        )
        val taxes = weekTax.entries.filter { it.key.isBefore(ws) }.map {
            TaxNotice(it.key, it.value[0].roundToInt(), it.value[1].roundToInt())
        }.filter { it.total > 0 }.sortedByDescending { it.weekStart }

        val wagerState = wagerEvent?.let { WagerState(it.id, it.questId, wagerStake, it.deadline, resolved = false, won = false) } ?: lastWager

        return GameState(
            now = now, today = today, player = player, quests = finalStates, board = board, results = results,
            days = days, weeks = weeks, months = months, season = season, recap = recap, records = records,
            achievements = achievements, skills = skills,
            inventory = Inventory(shields, jokers, trophies.toList()), purchases = purchases.toList(),
            lootLog = lootLog.toList(), bountyQuestId = if (bountyClaimed) null else bountyId, holiday = holidayToday,
            gerald = gerald, curse = curse, dust = dust, marriages = marriages.values.toList(), taxNotices = taxes,
            wager = wagerState, paused = paused, hardcore = hardcore, firstDay = firstDay, snapshot = snapshot,
        )
    }

    fun buildRecap(ws: LocalDate): Recap {
        val weekDays = (0L..6L).map { ws.plusDays(it) }
        val bestDay = weekDays.mapNotNull { d -> days[d]?.takeIf { it.xp > 0 }?.let { d to it.xp } }.maxByOrNull { it.second }
        val top = weekQuestPts[ws]?.maxByOrNull { it.value }?.let { (qById[it.key]?.title ?: "?") to it.value }
        val from = ws.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ws.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inWeek = results.values.filter { it.at in from until to }
        val best = inWeek.firstOrNull { it.bossDefeated } ?: inWeek.maxByOrNull { it.points }
        val tax = weekTax[ws]?.let { (it[0] + it[1]).roundToInt() } ?: 0
        val gWeek = weekDays.sumOf { geraldDays[it] ?: 0 }
        val cats = HashMap<String, Int>()
        weekDays.forEach { d -> days[d]?.byCategory?.forEach { (k, v) -> cats[k] = (cats[k] ?: 0) + v } }
        return Recap(
            weekStart = ws, xp = weeks[ws] ?: 0, goal = goalFor(ws), activeDays = weekActiveDays[ws] ?: 0,
            doneCount = weekDone[ws] ?: 0, bestDay = bestDay, topQuest = top, bestMoment = best,
            glutStart = weekGlutStart[ws] ?: 0, glutEnd = weekGlutEnd[ws] ?: 0, prevXp = weeks[ws.minusWeeks(1)] ?: 0,
            tax = tax, geraldXp = gWeek, categories = cats, bossesDefeated = weekBosses[ws] ?: emptyList(),
            newRecord = ws in weekRecord,
        )
    }
}

/** C03: Was Gerald so treibt. */
object GeraldLines {
    private val ahead = listOf(
        "Gerald hat heute schon zweimal gesaugt.",
        "Gerald hat die Fugen im Bad mit einer Zahnbürste geschrubbt. Mit seiner eigenen.",
        "Gerald hat seine Steuererklärung schon für nächstes Jahr gemacht.",
        "Gerald hat die Gewürze alphabetisch sortiert. Zweimal, zur Sicherheit.",
        "Gerald war um sechs Uhr laufen. Er sagt, es war „erfrischend“.",
        "Gerald hat seine Socken nach Farbtemperatur gefaltet.",
        "Gerald hat den Kühlschrank geputzt und dabei gepfiffen.",
    )
    private val behind = listOf(
        "Gerald liegt zurück. Er behauptet, sein Staubsauger sei kaputt.",
        "Gerald hat heute „nur“ die Fenster geputzt. Er wirkt nervös.",
        "Gerald fragt, wie du das machst. Verrat es ihm nicht.",
        "Gerald hat dich auf seinem Kühlschrank-Plan als Rivalen eingetragen.",
    )
    private val lies = listOf(
        "Gerald hat angeblich die ganze Wohnung gestrichen. Korrektur: Es war ein Stuhl.",
        "Gerald will 40 Quests geschafft haben. Gezählt hat er die Kaffeetassen.",
        "Gerald sagt, er habe einen Drachen besiegt. Es war eine Spinne in der Badewanne.",
    )

    fun pick(rng: Rng, userWeek: Int, geraldWeek: Int, lie: Boolean): String = when {
        lie -> lies[rng.nextInt(lies.size)]
        userWeek >= geraldWeek -> behind[rng.nextInt(behind.size)]
        else -> ahead[rng.nextInt(ahead.size)]
    }
}
