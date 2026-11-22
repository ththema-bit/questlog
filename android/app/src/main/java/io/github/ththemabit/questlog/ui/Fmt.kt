package io.github.ththemabit.questlog.ui

import io.github.ththemabit.questlog.core.QuestState
import io.github.ththemabit.questlog.core.WindowStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val nf = NumberFormat.getIntegerInstance(Locale.GERMANY)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
    val weekdayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun n(v: Number): String = nf.format(v)
    fun signed(v: Int): String = (if (v >= 0) "+" else "−") + n(kotlin.math.abs(v))

    fun duration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

    /** „1 Tag“ / „3 Tagen“ für Sätze mit „in“ und „seit“. */
    fun tagen(n: Int): String = if (n == 1) "1 Tag" else "$n Tagen"
    fun tage(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"

    fun day(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    fun relative(at: Long, today: LocalDate): String {
        val d = day(at)
        val diff = ChronoUnit.DAYS.between(d, today).toInt()
        return when {
            diff == 0 -> "heute, " + Instant.ofEpochMilli(at).atZone(zone).format(time)
            diff == 1 -> "gestern"
            diff in 2..6 -> "vor $diff Tagen"
            else -> d.format(dayMonth)
        }
    }

    fun window(qs: QuestState): String {
        val q = qs.quest
        return when (qs.status) {
            WindowStatus.EARLY -> "öffnet in ${tagen(qs.opensIn ?: 0)}"
            WindowStatus.OVERDUE -> if (q.recurring) "seit ${tagen(qs.overdueDays)} überfällig" else "Termin seit ${tagen(qs.overdueDays)} vorbei"
            WindowStatus.DONE -> "erledigt"
            WindowStatus.OPEN -> when {
                qs.closesIn == null -> if (q.recurring) "im Fenster" else "offen"
                qs.closesIn == 0 -> if (q.recurring) "letzter Tag im Fenster" else "heute fällig"
                q.recurring -> "Fenster schließt in ${tagen(qs.closesIn)}"
                else -> "fällig in ${tagen(qs.closesIn)}"
            }
        }
    }

    fun rhythm(min: Int?, max: Int?): String = when {
        min == null && max == null -> "einmalig"
        min != null && max != null && min == max -> if (min == 1) "täglich" else "alle $min Tage"
        min != null && max != null -> "alle $min bis $max Tage"
        max != null -> "spätestens alle $max Tage"
        else -> "frühestens alle $min Tage"
    }

    fun effort(min: Int): String = if (min >= 60 && min % 60 == 0) "${min / 60} h" else if (min > 60) "${min / 60} h ${min % 60} min" else "$min min"
}
