package com.example.cashback.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashback.data.CashbackRepository
import com.example.cashback.domain.CategoryRow
import com.example.cashback.domain.MonthProvider
import com.example.cashback.domain.Months
import com.example.cashback.domain.buildMainRows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class MainUiState(
    val loading: Boolean = true,
    val currentMonth: YearMonth = YearMonth.now(),
    val showingNext: Boolean = false,
    val selected: List<CategoryRow> = emptyList(),
    val unselected: List<CategoryRow> = emptyList(),
) {
    val nextMonth: YearMonth get() = currentMonth.plusMonths(1)
    val shownMonth: YearMonth get() = if (showingNext) nextMonth else currentMonth
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    repository: CashbackRepository,
    monthProvider: MonthProvider,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val showingNext = savedState.getStateFlow(KEY_NEXT, false)

    private val shown = combine(monthProvider.current, showingNext) { current, next -> current to next }

    val uiState: StateFlow<MainUiState> = shown.flatMapLatest { (current, next) ->
        val month = if (next) current.plusMonths(1) else current
        combine(
            repository.categories,
            repository.banks,
            repository.permanentRates,
            repository.monthlyRates(Months.key(month)),
        ) { categories, banks, permanent, monthly ->
            val rows = buildMainRows(categories, banks, permanent, monthly)
            MainUiState(
                loading = false,
                currentMonth = current,
                showingNext = next,
                selected = rows.selected,
                unselected = rows.unselected,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val unselectedExpanded: StateFlow<Boolean> = savedState.getStateFlow(KEY_EXPANDED, false)

    fun showNext(next: Boolean) {
        savedState[KEY_NEXT] = next
    }

    fun toggleUnselected() {
        savedState[KEY_EXPANDED] = !unselectedExpanded.value
    }

    private companion object {
        const val KEY_NEXT = "showingNext"
        const val KEY_EXPANDED = "unselectedExpanded"
    }
}
