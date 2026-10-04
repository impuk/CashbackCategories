package com.example.cashback.domain

/** Ввод и показ процента. Процент хранится в десятых долях: 15 = 1,5%. */
object Percent {
    /** Что можно набрать в поле: до трёх цифр и один знак после запятой. */
    private val INPUT = Regex("""^\d{0,3}([.,]\d?)?$""")
    const val MAX_TENTHS = 1000

    sealed interface Parsed {
        data object Empty : Parsed
        /** Набрана только запятая — ждём цифры, не сохраняем и не ругаемся. */
        data object Incomplete : Parsed
        data class Value(val tenths: Int) : Parsed
        data class Error(val message: String) : Parsed
    }

    fun isAcceptableInput(text: String): Boolean = INPUT.matches(text)

    fun parse(text: String): Parsed {
        val t = text.trim()
        if (t.isEmpty()) return Parsed.Empty
        if (t == "," || t == ".") return Parsed.Incomplete
        if (!INPUT.matches(t)) return Parsed.Error("Введите число")
        val parts = t.replace(',', '.').split('.')
        val whole = parts[0].ifEmpty { "0" }.toInt()
        val tenth = parts.getOrNull(1)?.ifEmpty { "0" }?.toInt() ?: 0
        val tenths = whole * 10 + tenth
        return when {
            tenths == 0 -> Parsed.Error("Больше 0")
            tenths > MAX_TENTHS -> Parsed.Error("Не больше 100")
            else -> Parsed.Value(tenths)
        }
    }

    /** 50 → «5», 15 → «1,5». */
    fun format(tenths: Int): String =
        if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
}
