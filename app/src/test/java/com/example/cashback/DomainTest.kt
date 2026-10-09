package com.example.cashback

import com.example.cashback.data.BankEntity
import com.example.cashback.data.CategoryEntity
import com.example.cashback.data.MonthlyRateEntity
import com.example.cashback.data.PermanentRateEntity
import com.example.cashback.domain.BankRate
import com.example.cashback.domain.CategoryRow
import com.example.cashback.domain.Months
import com.example.cashback.domain.Percent
import com.example.cashback.domain.buildMainRows
import com.example.cashback.domain.ruPlural
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class DomainTest {
    @Test
    fun percentParse() {
        assertEquals(Percent.Parsed.Empty, Percent.parse(""))
        assertEquals(Percent.Parsed.Incomplete, Percent.parse(","))
        assertEquals(Percent.Parsed.Value(50), Percent.parse("5"))
        assertEquals(Percent.Parsed.Value(15), Percent.parse("1,5"))
        assertEquals(Percent.Parsed.Value(15), Percent.parse("1.5"))
        assertEquals(Percent.Parsed.Value(50), Percent.parse("5,"))
        assertEquals(Percent.Parsed.Value(5), Percent.parse(",5"))
        assertEquals(Percent.Parsed.Value(1000), Percent.parse("100"))
        assertTrue(Percent.parse("0") is Percent.Parsed.Error)
        assertTrue(Percent.parse("0,0") is Percent.Parsed.Error)
        assertTrue(Percent.parse("100,1") is Percent.Parsed.Error)
    }

    @Test
    fun percentInputFilter() {
        assertTrue(Percent.isAcceptableInput(""))
        assertTrue(Percent.isAcceptableInput("12,5"))
        assertFalse(Percent.isAcceptableInput("1,55")) // только один знак после запятой
        assertFalse(Percent.isAcceptableInput("1000"))
        assertFalse(Percent.isAcceptableInput("1,2,3"))
        assertFalse(Percent.isAcceptableInput("-1"))
        assertFalse(Percent.isAcceptableInput("abc"))
    }

    @Test
    fun percentFormat() {
        assertEquals("5", Percent.format(50))
        assertEquals("1,5", Percent.format(15))
        assertEquals("0,1", Percent.format(1))
    }

    @Test
    fun plural() {
        assertEquals("категория", ruPlural(1, "категория", "категории", "категорий"))
        assertEquals("категории", ruPlural(3, "категория", "категории", "категорий"))
        assertEquals("категорий", ruPlural(5, "категория", "категории", "категорий"))
        assertEquals("категорий", ruPlural(11, "категория", "категории", "категорий"))
        assertEquals("категория", ruPlural(21, "категория", "категории", "категорий"))
        assertEquals("категорий", ruPlural(112, "категория", "категории", "категорий"))
    }

    @Test
    fun monthKeys() {
        val oct = YearMonth.of(2026, 10)
        assertEquals(oct, Months.fromKey(Months.key(oct)))
        assertEquals(Months.key(oct) + 1, Months.key(oct.plusMonths(1)))
        assertEquals(YearMonth.of(2027, 1), Months.fromKey(Months.key(YearMonth.of(2026, 12)) + 1))
        assertEquals("Октябрь", Months.name(oct))
        assertEquals("Октябрь 2026", Months.nameWithYear(oct))
        assertEquals("в мае", Months.inMonth(YearMonth.of(2026, 5)))
    }

    @Test
    fun mainRowsOrderAndOverride() {
        val banks = listOf(BankEntity(1, "Альфа", 1), BankEntity(2, "Т-Банк", 0), BankEntity(3, "ВТБ", 2))
        val categories = listOf(
            CategoryEntity(10, "АЗС", 2),
            CategoryEntity(11, "Супермаркеты", 0),
            CategoryEntity(12, "Аптеки", 1),
            CategoryEntity(13, "Кино", 3),
        )
        val month = 100
        val permanent = listOf(
            PermanentRateEntity(1, 12, 10), // Альфа: Аптеки 1% постоянно
            PermanentRateEntity(3, 10, 30), // ВТБ: АЗС 3% постоянно
        )
        val monthly = listOf(
            MonthlyRateEntity(1, 12, month, 50), // Альфа: Аптеки 5% в этом месяце — перекрывает 1%
            MonthlyRateEntity(2, 10, month, 15), // Т-Банк: АЗС 1,5%
        )
        val rows = buildMainRows(categories, banks, permanent, monthly)

        // Выбранные — в порядке категорий; «Супермаркеты» (позиция 0) без банков уходит вниз
        assertEquals(listOf("Аптеки", "АЗС"), rows.selected.map { it.name })
        assertEquals(listOf("Супермаркеты", "Кино"), rows.unselected.map { it.name })
        assertEquals(listOf("Альфа 5%"), rows.selected[0].best.map { it.label })
        assertEquals(emptyList<String>(), rows.selected[0].rest.map { it.label })
        // Лучший — по проценту, а не по списку банков (Т-Банк в списке раньше ВТБ)
        assertEquals(listOf("ВТБ 3%"), rows.selected[1].best.map { it.label })
        assertEquals(listOf("Т-Банк 1,5%"), rows.selected[1].rest.map { it.label })
    }

    @Test
    fun mainRowsWithoutMonthlyUsesPermanent() {
        val rows = buildMainRows(
            listOf(CategoryEntity(1, "Аптеки", 0)),
            listOf(BankEntity(1, "Альфа", 0)),
            listOf(PermanentRateEntity(1, 1, 10)),
            emptyList(),
        )
        assertEquals(listOf("Альфа 1%"), rows.selected.single().best.map { it.label })
    }

    private fun rate(id: Long, name: String, tenths: Int) = BankRate(id, name, tenths)

    @Test
    fun bestAndRestSplit() {
        // Банки переданы в общем порядке списка банков.
        val row = CategoryRow.of(
            1, "Маркетплейсы",
            listOf(
                rate(1, "Т-Банк", 50), rate(2, "Альфа", 10), rate(3, "Яндекс", 30),
                rate(4, "Озон", 50), rate(5, "ОТП", 30), rate(6, "WB", 50),
            ),
        )
        // Несколько лучших — все в плашках, по порядку списка
        assertEquals(listOf("Т-Банк 5%", "Озон 5%", "WB 5%"), row.best.map { it.label })
        // Остальные — по убыванию процента, при равных — по порядку списка
        assertEquals(listOf("Яндекс 3%", "ОТП 3%", "Альфа 1%"), row.rest.map { it.label })
        assertTrue(row.isSelected)
    }

    @Test
    fun bestOnlyAndEmpty() {
        val single = CategoryRow.of(1, "АЗС", listOf(rate(1, "Т-Банк", 15)))
        assertEquals(listOf("Т-Банк 1,5%"), single.best.map { it.label })
        assertTrue(single.rest.isEmpty())

        val bestHigherLater = CategoryRow.of(2, "Кафе", listOf(rate(1, "Т-Банк", 50), rate(2, "Яндекс", 100)))
        assertEquals(listOf("Яндекс 10%"), bestHigherLater.best.map { it.label })
        assertEquals(listOf("Т-Банк 5%"), bestHigherLater.rest.map { it.label })

        val empty = CategoryRow.of(3, "Кино", emptyList())
        assertTrue(empty.best.isEmpty() && empty.rest.isEmpty())
        assertFalse(empty.isSelected)
    }
}
