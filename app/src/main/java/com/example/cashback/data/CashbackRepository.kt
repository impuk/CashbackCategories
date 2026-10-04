package com.example.cashback.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.Locale

enum class NameResult { OK, BLANK, DUPLICATE }

class CashbackRepository(private val db: CashbackDatabase) {
    private val dao = db.dao()

    val banks: Flow<List<BankEntity>> = dao.observeBanks()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val permanentRates: Flow<List<PermanentRateEntity>> = dao.observePermanentRates()

    fun monthlyRates(month: Int): Flow<List<MonthlyRateEntity>> = dao.observeMonthlyRates(month)

    fun category(id: Long): Flow<CategoryEntity?> = dao.observeCategory(id)

    /** null — убрать постоянный кэшбэк. */
    suspend fun setPermanent(bankId: Long, categoryId: Long, percentTenths: Int?) {
        if (percentTenths == null) dao.deletePermanent(bankId, categoryId)
        else dao.upsertPermanent(PermanentRateEntity(bankId, categoryId, percentTenths))
    }

    /** null — убрать месячный кэшбэк. */
    suspend fun setMonthly(bankId: Long, categoryId: Long, month: Int, percentTenths: Int?) {
        if (percentTenths == null) dao.deleteMonthly(bankId, categoryId, month)
        else dao.upsertMonthly(MonthlyRateEntity(bankId, categoryId, month, percentTenths))
    }

    /** Оставляет только текущий и следующий месяц. */
    suspend fun keepOnlyMonths(current: Int) = dao.deleteMonthlyOutside(current, current + 1)

    // Банки

    suspend fun addBank(name: String): NameResult = db.withTransaction {
        val clean = name.trim()
        when {
            clean.isEmpty() -> NameResult.BLANK
            dao.getBanks().any { it.name.sameName(clean) } -> NameResult.DUPLICATE
            else -> {
                dao.insertBank(BankEntity(name = clean, position = dao.maxBankPosition() + 1))
                NameResult.OK
            }
        }
    }

    suspend fun deleteBank(id: Long) = db.withTransaction {
        dao.deletePermanentByBank(id)
        dao.deleteMonthlyByBank(id)
        dao.deleteBank(id)
    }

    suspend fun countCategoriesOfBank(id: Long): Int = dao.countCategoriesOfBank(id)

    suspend fun reorderBanks(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> dao.setBankPosition(id, index) }
    }

    // Категории

    suspend fun addCategory(name: String): NameResult = db.withTransaction {
        val clean = name.trim()
        when {
            clean.isEmpty() -> NameResult.BLANK
            dao.getCategories().any { it.name.sameName(clean) } -> NameResult.DUPLICATE
            else -> {
                dao.insertCategory(CategoryEntity(name = clean, position = dao.maxCategoryPosition() + 1))
                NameResult.OK
            }
        }
    }

    suspend fun renameCategory(id: Long, name: String): NameResult = db.withTransaction {
        val clean = name.trim()
        when {
            clean.isEmpty() -> NameResult.BLANK
            dao.getCategories().any { it.id != id && it.name.sameName(clean) } -> NameResult.DUPLICATE
            else -> {
                dao.renameCategory(id, clean)
                NameResult.OK
            }
        }
    }

    suspend fun deleteCategory(id: Long) = db.withTransaction {
        dao.deletePermanentByCategory(id)
        dao.deleteMonthlyByCategory(id)
        dao.deleteCategory(id)
    }

    suspend fun countBanksOfCategory(id: Long): Int = dao.countBanksOfCategory(id)

    suspend fun reorderCategories(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> dao.setCategoryPosition(id, index) }
    }

    private fun String.sameName(other: String) =
        trim().lowercase(Locale.ROOT) == other.trim().lowercase(Locale.ROOT)
}
