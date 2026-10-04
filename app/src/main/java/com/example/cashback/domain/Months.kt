package com.example.cashback.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/** Месяц хранится в базе целым числом: год * 12 + (месяц - 1). */
object Months {
    private val NAMES = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    private val IN_MONTH = listOf(
        "январе", "феврале", "марте", "апреле", "мае", "июне",
        "июле", "августе", "сентябре", "октябре", "ноябре", "декабре",
    )

    /** «в ноябре» */
    fun inMonth(month: YearMonth): String = "в ${IN_MONTH[month.monthValue - 1]}"

    fun key(month: YearMonth): Int = month.year * 12 + month.monthValue - 1

    fun fromKey(key: Int): YearMonth = YearMonth.of(Math.floorDiv(key, 12), Math.floorMod(key, 12) + 1)

    fun name(month: YearMonth): String = NAMES[month.monthValue - 1]

    fun nameWithYear(month: YearMonth): String = "${name(month)} ${month.year}"
}

/**
 * Текущий месяц. [refresh] вызывается при показе приложения и раз в минуту,
 * чтобы 1-го числа следующий месяц стал текущим без перезапуска.
 */
class MonthProvider(private val now: () -> YearMonth = { YearMonth.now() }) {
    private val _current = MutableStateFlow(now())
    val current: StateFlow<YearMonth> = _current.asStateFlow()

    fun refresh() {
        _current.value = now()
    }
}
