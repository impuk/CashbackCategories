package com.example.cashback.domain

import com.example.cashback.data.BankEntity
import com.example.cashback.data.CategoryEntity
import com.example.cashback.data.MonthlyRateEntity
import com.example.cashback.data.PermanentRateEntity

data class BankRate(val bankId: Long, val bankName: String, val percentTenths: Int) {
    /** «Т-Банк 1,5%» */
    val label: String get() = "$bankName ${Percent.format(percentTenths)}%"
}

/**
 * Строка категории на главном экране.
 * [best] — банки с наибольшим процентом (плашки), в порядке списка банков.
 * [rest] — остальные банки (бледная строка), по убыванию процента, при равных — по списку банков.
 */
data class CategoryRow(
    val categoryId: Long,
    val name: String,
    val best: List<BankRate>,
    val rest: List<BankRate>,
) {
    val isSelected: Boolean get() = best.isNotEmpty()

    companion object {
        /** Делит банки на лучшие и остальные. [ratesInBankOrder] — в общем порядке банков. */
        fun of(categoryId: Long, name: String, ratesInBankOrder: List<BankRate>): CategoryRow {
            // sortedByDescending стабильна: при равных процентах остаётся порядок списка банков.
            val sorted = ratesInBankOrder.sortedByDescending { it.percentTenths }
            val max = sorted.firstOrNull()?.percentTenths
            val best = sorted.takeWhile { it.percentTenths == max }
            return CategoryRow(categoryId, name, best, sorted.drop(best.size))
        }
    }
}

data class MainRows(val selected: List<CategoryRow>, val unselected: List<CategoryRow>)

/**
 * Строки главного экрана для одного месяца. Процент банка в категории — месячный,
 * если он задан на этот месяц, иначе постоянный. Категории идут в заданном порядке,
 * выбранные (есть хотя бы один банк) — выше невыбранных.
 */
fun buildMainRows(
    categories: List<CategoryEntity>,
    banks: List<BankEntity>,
    permanent: List<PermanentRateEntity>,
    monthly: List<MonthlyRateEntity>,
): MainRows {
    val permanentByPair = permanent.associate { (it.bankId to it.categoryId) to it.percentTenths }
    val monthlyByPair = monthly.associate { (it.bankId to it.categoryId) to it.percentTenths }
    val orderedBanks = banks.sortedWith(compareBy({ it.position }, { it.id }))
    val rows = categories.sortedWith(compareBy({ it.position }, { it.id })).map { category ->
        CategoryRow.of(
            categoryId = category.id,
            name = category.name,
            ratesInBankOrder = orderedBanks.mapNotNull { bank ->
                val pair = bank.id to category.id
                (monthlyByPair[pair] ?: permanentByPair[pair])?.let { BankRate(bank.id, bank.name, it) }
            },
        )
    }
    val (selected, unselected) = rows.partition { it.isSelected }
    return MainRows(selected, unselected)
}
