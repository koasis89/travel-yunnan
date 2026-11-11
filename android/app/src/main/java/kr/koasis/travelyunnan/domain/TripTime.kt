package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.json.Day
import kr.koasis.travelyunnan.data.json.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 여행지 시간대. 한국보다 1시간 늦다. */
val TRIP_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

fun tripNow(override: String?): LocalDateTime =
    override?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        ?: LocalDateTime.now(TRIP_ZONE).withSecond(0).withNano(0)

sealed interface TripPhase {
    data class BeforeTrip(val daysLeft: Long) : TripPhase
    data class OnTrip(val day: Day) : TripPhase
    data object AfterTrip : TripPhase
}

object DayResolver {
    fun resolve(now: LocalDateTime, days: List<Day>): TripPhase {
        val sorted = days.sortedBy { it.date }
        if (sorted.isEmpty()) return TripPhase.AfterTrip
        val today = now.toLocalDate()
        val first = LocalDate.parse(sorted.first().date)
        val last = LocalDate.parse(sorted.last().date)
        return when {
            today.isBefore(first) -> TripPhase.BeforeTrip(ChronoUnit.DAYS.between(today, first))
            today.isAfter(last) -> TripPhase.AfterTrip
            else -> TripPhase.OnTrip(sorted.first { LocalDate.parse(it.date) == today })
        }
    }

    /** 홈 바로가기 등에 쓸 "기준 일차": 여행 전이면 D1, 여행 후면 마지막 날 */
    fun focusDay(phase: TripPhase, days: List<Day>): Day = when (phase) {
        is TripPhase.OnTrip -> phase.day
        is TripPhase.BeforeTrip -> days.minBy { it.date }
        TripPhase.AfterTrip -> days.maxBy { it.date }
    }
}

fun parseTime(t: String?): LocalTime? = t?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

fun Schedule.startAt(day: Day): LocalDateTime? = parseTime(timeStart)?.let { LocalDate.parse(day.date).atTime(it) }

data class NowState(val current: Schedule?, val next: Schedule?)

object CurrentScheduleFinder {
    /**
     * 현재 시각이 속한 행(current)과 그다음 행(next).
     * 종료 시각이 없는 행은 다음 행 시작 전까지 진행 중으로 본다.
     */
    fun find(rows: List<Schedule>, time: LocalTime): NowState {
        val timed = rows.filter { parseTime(it.timeStart) != null }.sortedBy { it.seq }
        fun min(t: LocalTime) = t.hour * 60 + t.minute
        // 자정을 넘는 행(예: D1 23:35~00:20, 00:20~)은 다음 날 시각(+24시간)으로 본다(웹 findNow 와 같다)
        val starts = IntArray(timed.size); var off = 0
        for ((i, r) in timed.withIndex()) {
            var s = min(parseTime(r.timeStart)!!) + off
            if (i > 0 && s < starts[i - 1] - 360) { off += 1440; s += 1440 }
            starts[i] = s
        }
        val t = min(time)
        var current: Schedule? = null
        for ((i, r) in timed.withIndex()) {
            val s = starts[i]
            var e: Int? = parseTime(r.timeEnd)?.let { min(it) + if (s >= 1440) 1440 else 0 } ?: starts.getOrNull(i + 1)
            if (e != null && e <= s) e += 1440
            if (t >= s && (e == null || t < e)) current = r
        }
        val next = timed.withIndex().firstOrNull { (i, r) -> starts[i] > t && r != current }?.value
        return NowState(current, next)
    }
}

/** 놓치면 안 되는 이동(열차·투어 차량·빵차·전용차·픽업) */
private val MOVE_KEYWORDS = listOf("열차", "투어 차량", "빵차", "전용차량", "픽업")

fun Schedule.isKeyMove(): Boolean = MOVE_KEYWORDS.any { transport.contains(it) }

data class NextMove(val day: Day, val row: Schedule, val at: LocalDateTime, val minutesLeft: Long)

object NextMoveFinder {
    fun find(now: LocalDateTime, days: List<Day>, rowsOf: (String) -> List<Schedule>): NextMove? =
        days.sortedBy { it.date }.asSequence()
            .flatMap { d -> rowsOf(d.id).asSequence().map { d to it } }
            .filter { (_, r) -> r.isKeyMove() }
            .mapNotNull { (d, r) -> r.startAt(d)?.let { NextMove(d, r, it, ChronoUnit.MINUTES.between(now, it)) } }
            .firstOrNull { it.at.isAfter(now) }
}

/** 현재 일정 행으로 회화 범주를 추천한다. */
object PhraseSuggester {
    fun suggest(row: Schedule?): String {
        if (row == null) return "car"
        val all = "${row.to} ${row.transport} ${row.tip}"
        return when {
            listOf("열차", "개찰", "역").any { row.transport.contains(it) || row.to.endsWith(it) } -> "station"
            listOf("체크인", "입주").any { all.contains(it) } -> "hotel"
            listOf("점심", "저녁", "아침", "식당", "야식").any { all.contains(it) } -> "restaurant"
            listOf("매표", "입장", "케이블카").any { all.contains(it) } -> "ticket"
            listOf("택시", "DiDi", "DIDI", "디디", "빵차", "차량", "전용").any { all.contains(it) } -> "car"
            else -> "car"
        }
    }
}

fun formatLeft(minutes: Long): String = when {
    minutes < 0 -> "지남"
    minutes < 60 -> "${minutes}분 후"
    minutes < 24 * 60 -> "${minutes / 60}시간 ${minutes % 60}분 후"
    else -> "${minutes / (24 * 60)}일 ${(minutes % (24 * 60)) / 60}시간 후"
}
