package com.example.cashback.domain

/** Русское склонение после числа: 1 категория, 2 категории, 5 категорий. */
fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
