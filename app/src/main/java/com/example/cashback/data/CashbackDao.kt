package com.example.cashback.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CashbackDao {
    // Банки
    @Query("SELECT * FROM banks ORDER BY position, id")
    fun observeBanks(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks ORDER BY position, id")
    suspend fun getBanks(): List<BankEntity>

    @Insert
    suspend fun insertBank(bank: BankEntity): Long

    @Query("DELETE FROM banks WHERE id = :id")
    suspend fun deleteBank(id: Long)

    @Query("UPDATE banks SET position = :position WHERE id = :id")
    suspend fun setBankPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM banks")
    suspend fun maxBankPosition(): Int

    // Категории
    @Query("SELECT * FROM categories ORDER BY position, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeCategory(id: Long): Flow<CategoryEntity?>

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxCategoryPosition(): Int

    // Проценты
    @Query("SELECT * FROM permanent_rates")
    fun observePermanentRates(): Flow<List<PermanentRateEntity>>

    @Query("SELECT * FROM monthly_rates WHERE month = :month")
    fun observeMonthlyRates(month: Int): Flow<List<MonthlyRateEntity>>

    @Query("SELECT * FROM monthly_rates")
    suspend fun getAllMonthlyRates(): List<MonthlyRateEntity>

    @Upsert
    suspend fun upsertPermanent(rate: PermanentRateEntity)

    @Query("DELETE FROM permanent_rates WHERE bankId = :bankId AND categoryId = :categoryId")
    suspend fun deletePermanent(bankId: Long, categoryId: Long)

    @Upsert
    suspend fun upsertMonthly(rate: MonthlyRateEntity)

    @Query("DELETE FROM monthly_rates WHERE bankId = :bankId AND categoryId = :categoryId AND month = :month")
    suspend fun deleteMonthly(bankId: Long, categoryId: Long, month: Int)

    @Query("DELETE FROM permanent_rates WHERE bankId = :bankId")
    suspend fun deletePermanentByBank(bankId: Long)

    @Query("DELETE FROM monthly_rates WHERE bankId = :bankId")
    suspend fun deleteMonthlyByBank(bankId: Long)

    @Query("DELETE FROM permanent_rates WHERE categoryId = :categoryId")
    suspend fun deletePermanentByCategory(categoryId: Long)

    @Query("DELETE FROM monthly_rates WHERE categoryId = :categoryId")
    suspend fun deleteMonthlyByCategory(categoryId: Long)

    @Query("DELETE FROM monthly_rates WHERE month < :min OR month > :max")
    suspend fun deleteMonthlyOutside(min: Int, max: Int)

    /** Сколько разных категорий привязано к банку (постоянно или в любом месяце). */
    @Query(
        "SELECT COUNT(*) FROM (SELECT categoryId FROM permanent_rates WHERE bankId = :bankId " +
            "UNION SELECT categoryId FROM monthly_rates WHERE bankId = :bankId)"
    )
    suspend fun countCategoriesOfBank(bankId: Long): Int

    /** Сколько разных банков привязано к категории (постоянно или в любом месяце). */
    @Query(
        "SELECT COUNT(*) FROM (SELECT bankId FROM permanent_rates WHERE categoryId = :categoryId " +
            "UNION SELECT bankId FROM monthly_rates WHERE categoryId = :categoryId)"
    )
    suspend fun countBanksOfCategory(categoryId: Long): Int
}
