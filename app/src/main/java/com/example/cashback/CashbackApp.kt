package com.example.cashback

import android.app.Application
import com.example.cashback.data.CashbackDatabase
import com.example.cashback.data.CashbackRepository
import com.example.cashback.domain.MonthProvider
import com.example.cashback.domain.Months
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Ручной DI: всё, что нужно экранам, создаётся здесь один раз. */
class AppContainer(
    val repository: CashbackRepository,
    val monthProvider: MonthProvider,
    val appScope: CoroutineScope,
) {
    /** 1-го числа прошедший месяц удаляется, следующий становится текущим. */
    fun startMonthCleanup() {
        appScope.launch {
            monthProvider.current.collect { month ->
                runCatching { repository.keepOnlyMonths(Months.key(month)) }
            }
        }
    }
}

class CashbackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            repository = CashbackRepository(CashbackDatabase.build(this)),
            monthProvider = MonthProvider(),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        container.startMonthCleanup()
    }
}
