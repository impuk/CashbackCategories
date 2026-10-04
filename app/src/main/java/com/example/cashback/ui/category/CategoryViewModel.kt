package com.example.cashback.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashback.data.CashbackRepository
import com.example.cashback.domain.MonthProvider
import com.example.cashback.domain.Months
import com.example.cashback.domain.Percent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class RateField { PERMANENT, MONTHLY }

data class BankFieldsState(
    val bankId: Long,
    val bankName: String,
    val permanentText: String,
    val monthlyText: String,
) {
    val permanentError: String? get() = errorOf(permanentText)
    val monthlyError: String? get() = errorOf(monthlyText)

    private fun errorOf(text: String) = (Percent.parse(text) as? Percent.Parsed.Error)?.message
}

data class CategoryUiState(
    val loading: Boolean = true,
    val categoryName: String = "",
    val month: YearMonth,
    val banks: List<BankFieldsState> = emptyList(),
    /** Экран нужно закрыть: категорию удалили или месяц ушёл в прошлое. */
    val closed: Boolean = false,
)

private data class FieldTexts(val permanent: String, val monthly: String)

/**
 * Экран категории: у каждого банка поля «Постоянно» и «В этом месяце».
 * Каждое корректное изменение сразу пишется в базу; пустое поле убирает значение.
 */
class CategoryViewModel(
    private val categoryId: Long,
    private val month: Int,
    private val repository: CashbackRepository,
    monthProvider: MonthProvider,
    appScope: CoroutineScope,
) : ViewModel() {
    /** Тексты полей, как их набрал пользователь. Из базы читаются один раз при открытии. */
    private val texts = MutableStateFlow<Map<Long, FieldTexts>?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Записи идут строго по очереди и в области приложения: последнее нажатие
     * сохранится, даже если экран сразу закрыли.
     */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        appScope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (e: Exception) {
                    _messages.tryEmit("Не удалось сохранить")
                }
            }
        }
        viewModelScope.launch {
            try {
                val banks = repository.banks.first()
                val permanent = repository.permanentRates.first()
                    .filter { it.categoryId == categoryId }.associate { it.bankId to it.percentTenths }
                val monthly = repository.monthlyRates(month).first()
                    .filter { it.categoryId == categoryId }.associate { it.bankId to it.percentTenths }
                texts.value = banks.associate { bank ->
                    bank.id to FieldTexts(
                        permanent = permanent[bank.id]?.let(Percent::format).orEmpty(),
                        monthly = monthly[bank.id]?.let(Percent::format).orEmpty(),
                    )
                }
            } catch (e: Exception) {
                texts.value = emptyMap()
                _messages.tryEmit("Не удалось загрузить данные")
            }
        }
    }

    val uiState: StateFlow<CategoryUiState> = combine(
        repository.category(categoryId),
        repository.banks,
        texts,
        monthProvider.current,
    ) { category, banks, texts, current ->
        val currentKey = Months.key(current)
        val monthGone = month < currentKey || month > currentKey + 1
        if (texts == null) {
            CategoryUiState(month = Months.fromKey(month), closed = monthGone)
        } else {
            CategoryUiState(
                loading = false,
                categoryName = category?.name.orEmpty(),
                month = Months.fromKey(month),
                banks = banks.map { bank ->
                    val t = texts[bank.id] ?: FieldTexts("", "")
                    BankFieldsState(bank.id, bank.name, t.permanent, t.monthly)
                },
                closed = category == null || monthGone,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState(month = Months.fromKey(month)))

    fun onTextChange(bankId: Long, field: RateField, text: String) {
        if (texts.value == null || !Percent.isAcceptableInput(text)) return
        texts.update { map ->
            val old = map?.get(bankId) ?: FieldTexts("", "")
            val new = when (field) {
                RateField.PERMANENT -> old.copy(permanent = text)
                RateField.MONTHLY -> old.copy(monthly = text)
            }
            map.orEmpty() + (bankId to new)
        }
        val value = when (val parsed = Percent.parse(text)) {
            Percent.Parsed.Empty -> null
            is Percent.Parsed.Value -> parsed.tenths
            else -> return // ошибка или незаконченный ввод: в базе остаётся прежнее значение
        }
        writes.trySend {
            when (field) {
                RateField.PERMANENT -> repository.setPermanent(bankId, categoryId, value)
                RateField.MONTHLY -> repository.setMonthly(bankId, categoryId, month, value)
            }
        }
    }

    override fun onCleared() {
        writes.close() // очередь дописывается до конца и завершается
    }
}
