package com.example.cashback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashback.data.CashbackDatabase
import com.example.cashback.data.CashbackRepository
import com.example.cashback.data.DefaultData
import com.example.cashback.data.NameResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: CashbackDatabase
    private lateinit var repo: CashbackRepository

    @Before
    fun setUp() {
        db = CashbackDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = CashbackRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun bank(name: String) = repo.banks.first().first { it.name == name }
    private suspend fun category(name: String) = repo.categories.first().first { it.name == name }

    @Test
    fun seedListsInOrder() = runBlocking {
        assertEquals(DefaultData.banks, repo.banks.first().map { it.name })
        assertEquals(DefaultData.categories, repo.categories.first().map { it.name })
        assertEquals(10, DefaultData.banks.size)
        assertEquals(25, DefaultData.categories.size)
    }

    @Test
    fun addBankChecksNames() = runBlocking {
        assertEquals(NameResult.BLANK, repo.addBank("   "))
        assertEquals(NameResult.DUPLICATE, repo.addBank(" т-банк "))
        assertEquals(NameResult.OK, repo.addBank("  Сбер "))
        val banks = repo.banks.first()
        assertEquals("Сбер", banks.last().name) // добавляется в конец, без пробелов
        assertEquals(11, banks.size)
    }

    @Test
    fun addAndRenameCategory() = runBlocking {
        assertEquals(NameResult.OK, repo.addCategory("Подписки"))
        assertEquals("Подписки", repo.categories.first().last().name)
        val azs = category("АЗС")
        assertEquals(NameResult.DUPLICATE, repo.renameCategory(azs.id, "такси"))
        assertEquals(NameResult.BLANK, repo.renameCategory(azs.id, " "))
        assertEquals(NameResult.OK, repo.renameCategory(azs.id, "азс")) // своё же имя другим регистром — можно
        assertEquals(NameResult.OK, repo.renameCategory(azs.id, "Топливо"))
        assertEquals("Топливо", repo.categories.first().first { it.id == azs.id }.name)
    }

    @Test
    fun setAndClearRates() = runBlocking {
        val t = bank("Т-Банк"); val azs = category("АЗС")
        repo.setPermanent(t.id, azs.id, 10)
        repo.setPermanent(t.id, azs.id, 20) // одна строка на пару, значение заменяется
        assertEquals(listOf(20), repo.permanentRates.first().map { it.percentTenths })
        repo.setPermanent(t.id, azs.id, null)
        assertTrue(repo.permanentRates.first().isEmpty())

        repo.setMonthly(t.id, azs.id, 100, 50)
        repo.setMonthly(t.id, azs.id, 101, 30)
        assertEquals(listOf(50), repo.monthlyRates(100).first().map { it.percentTenths })
        repo.setMonthly(t.id, azs.id, 100, null)
        assertTrue(repo.monthlyRates(100).first().isEmpty())
        assertEquals(1, repo.monthlyRates(101).first().size)
    }

    @Test
    fun deleteBankWithBindings() = runBlocking {
        val t = bank("Т-Банк"); val alfa = bank("Альфа")
        val azs = category("АЗС"); val taxi = category("Такси"); val kino = category("Кино")
        repo.setPermanent(t.id, azs.id, 10)
        repo.setMonthly(t.id, azs.id, 100, 50) // та же категория — считается один раз
        repo.setMonthly(t.id, taxi.id, 101, 30)
        repo.setMonthly(alfa.id, kino.id, 100, 70)
        assertEquals(2, repo.countCategoriesOfBank(t.id))

        repo.deleteBank(t.id)
        assertTrue(repo.banks.first().none { it.id == t.id })
        assertTrue(repo.permanentRates.first().isEmpty())
        assertEquals(listOf(alfa.id), repo.monthlyRates(100).first().map { it.bankId })
        assertTrue(repo.monthlyRates(101).first().isEmpty())
    }

    @Test
    fun deleteCategoryWithBindings() = runBlocking {
        val t = bank("Т-Банк"); val alfa = bank("Альфа"); val azs = category("АЗС"); val kino = category("Кино")
        repo.setPermanent(t.id, azs.id, 10)
        repo.setMonthly(alfa.id, azs.id, 100, 50)
        repo.setMonthly(alfa.id, kino.id, 100, 70)
        assertEquals(2, repo.countBanksOfCategory(azs.id))

        repo.deleteCategory(azs.id)
        assertTrue(repo.categories.first().none { it.id == azs.id })
        assertTrue(repo.permanentRates.first().isEmpty())
        assertEquals(listOf(kino.id), repo.monthlyRates(100).first().map { it.categoryId })
    }

    @Test
    fun reorder() = runBlocking {
        val ids = repo.banks.first().map { it.id }.reversed()
        repo.reorderBanks(ids)
        assertEquals(ids, repo.banks.first().map { it.id })
        assertEquals("УБРиР", repo.banks.first().first().name)

        val catIds = repo.categories.first().map { it.id }.let { listOf(it.last()) + it.dropLast(1) }
        repo.reorderCategories(catIds)
        assertEquals("Образование", repo.categories.first().first().name)
    }

    @Test
    fun keepOnlyCurrentAndNextMonth() = runBlocking {
        val t = bank("Т-Банк"); val azs = category("АЗС")
        repo.setPermanent(t.id, azs.id, 10)
        listOf(99, 100, 101, 102).forEach { repo.setMonthly(t.id, azs.id, it, 50) }
        repo.keepOnlyMonths(current = 100)
        assertTrue(repo.monthlyRates(99).first().isEmpty())
        assertEquals(1, repo.monthlyRates(100).first().size)
        assertEquals(1, repo.monthlyRates(101).first().size)
        assertTrue(repo.monthlyRates(102).first().isEmpty())
        assertEquals(1, repo.permanentRates.first().size) // постоянные не трогаем
    }
}
