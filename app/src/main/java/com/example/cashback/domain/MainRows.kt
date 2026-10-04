package com.example.cashback.domain

import com.example.cashback.data.BankEntity
import com.example.cashback.data.CategoryEntity
import com.example.cashback.data.MonthlyRateEntity
import com.example.cashback.data.PermanentRateEntity

data class BankRate(val bankId: Long, val bankName: String, val percentTenths: Int)

data class CategoryRow(val categoryId: Long, val name: String, val rates: List<BankRate>) {
    /** «Т-Банк 5% · Альфа 1,5%» */
    val ratesText: String
        get() = rates.joinToString(" · ") { "${it.bankName} ${Percent.format(it.percentTenths)}%" }
}

data class MainRows(val selected: List<CategoryRow>, val unselected: List<CategoryRow>)

/**
 * Строки главного экрана для одного месяца. Процент банка в категории — месячный,
 * если он задан на этот месяц, иначе постоянный. Категории идут в заданном порядке,
 * выбранные (есть хотя бы один банк) — выше невыбранных; банки — в общем порядке.
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
        CategoryRow(
            categoryId = category.id,
            name = category.name,
            rates = orderedBanks.mapNotNull { bank ->
                val pair = bank.id to category.id
                (monthlyByPair[pair] ?: permanentByPair[pair])?.let { BankRate(bank.id, bank.name, it) }
            },
        )
    }
    val (selected, unselected) = rows.partition { it.rates.isNotEmpty() }
    return MainRows(selected, unselected)
}
