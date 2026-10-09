package com.example.cashback

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashback.data.CashbackDatabase
import com.example.cashback.data.CashbackRepository
import com.example.cashback.domain.MonthProvider
import com.example.cashback.domain.Months
import com.example.cashback.ui.category.CategoryViewModel
import com.example.cashback.ui.category.RateField
import com.example.cashback.ui.main.MainViewModel
import com.example.cashback.ui.manage.ManageDialog
import com.example.cashback.ui.manage.ManageKind
import com.example.cashback.ui.manage.ManageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ViewModelTest {
    private lateinit var db: CashbackDatabase
    private lateinit var repo: CashbackRepository
    private lateinit var appScope: CoroutineScope
    private var now = YearMonth.of(2026, 10)
    private val months = MonthProvider { now }
    private val oct = Months.key(YearMonth.of(2026, 10))
    private val nov = oct + 1

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = CashbackDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = CashbackRepository(db)
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun ids(bank: String, category: String) = runBlocking {
        repo.banks.first().first { it.name == bank }.id to repo.categories.first().first { it.name == category }.id
    }

    // Главный экран

    @Test
    fun mainShowsCurrentOrNextMonth() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        val (alfa, apteki) = ids("Альфа", "Аптеки")
        repo.setMonthly(t, azs, oct, 50)
        repo.setMonthly(t, azs, nov, 30)
        repo.setPermanent(alfa, apteki, 10)

        val vm = MainViewModel(repo, months, SavedStateHandle())
        val job = appScope.launch { vm.uiState.collect {} }
        awaitUntil { !vm.uiState.value.loading }
        assertEquals(listOf("АЗС", "Аптеки"), vm.uiState.value.selected.map { it.name }) // АЗС выше по порядку
        assertEquals(listOf("Т-Банк 5%"), vm.uiState.value.selected[0].best.map { it.label })
        assertEquals(23, vm.uiState.value.unselected.size)

        vm.showNext(true)
        awaitUntil { vm.uiState.value.showingNext && vm.uiState.value.selected.firstOrNull()?.best?.map { it.label } == listOf("Т-Банк 3%") }
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.shownMonth)
        assertEquals(listOf("Альфа 1%"), vm.uiState.value.selected[1].best.map { it.label }) // постоянная — в каждом месяце
        job.cancel()
    }

    @Test
    fun mainFollowsMonthChange() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        repo.setMonthly(t, azs, nov, 30)
        val vm = MainViewModel(repo, months, SavedStateHandle())
        val job = appScope.launch { vm.uiState.collect {} }
        awaitUntil { !vm.uiState.value.loading }
        assertTrue(vm.uiState.value.selected.isEmpty())

        now = YearMonth.of(2026, 11) // наступило 1-е число
        months.refresh()
        awaitUntil { vm.uiState.value.currentMonth == now && vm.uiState.value.selected.size == 1 }
        job.cancel()
    }

    // Экран категории

    private fun categoryVm(categoryId: Long, month: Int) =
        CategoryViewModel(categoryId, month, repo, months, appScope)

    @Test
    fun categoryLoadsAndSavesImmediately() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        val (alfa, _) = ids("Альфа", "АЗС")
        repo.setPermanent(t, azs, 15)
        repo.setMonthly(t, azs, oct, 50)

        val vm = categoryVm(azs, oct)
        val job = appScope.launch { vm.uiState.collect {} }
        awaitUntil { !vm.uiState.value.loading }
        val tRow = vm.uiState.value.banks.first { it.bankId == t }
        assertEquals("АЗС", vm.uiState.value.categoryName)
        assertEquals("1,5", tRow.permanentText)
        assertEquals("5", tRow.monthlyText)
        assertEquals(10, vm.uiState.value.banks.size)

        vm.onTextChange(alfa, RateField.MONTHLY, "7")
        vm.onTextChange(alfa, RateField.MONTHLY, "7,")
        vm.onTextChange(alfa, RateField.MONTHLY, "7,5")
        awaitUntil { runBlocking { repo.monthlyRates(oct).first().any { it.bankId == alfa && it.percentTenths == 75 } } }
        assertTrue(repo.monthlyRates(nov).first().isEmpty()) // только в своём месяце

        vm.onTextChange(t, RateField.PERMANENT, "") // пустое поле убирает значение
        awaitUntil { runBlocking { repo.permanentRates.first().isEmpty() } }
        job.cancel()
    }

    @Test
    fun categoryRejectsBadInput() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        repo.setMonthly(t, azs, oct, 50)
        val vm = categoryVm(azs, oct)
        val job = appScope.launch { vm.uiState.collect {} }
        awaitUntil { !vm.uiState.value.loading }

        vm.onTextChange(t, RateField.MONTHLY, "5,55") // лишний знак не набирается
        assertEquals("5", vm.uiState.value.banks.first { it.bankId == t }.monthlyText)

        vm.onTextChange(t, RateField.MONTHLY, "0")
        awaitUntil { vm.uiState.value.banks.first { it.bankId == t }.monthlyError == "Больше 0" }
        Thread.sleep(200)
        assertEquals(50, repo.monthlyRates(oct).first().single().percentTenths) // в базе прежнее значение
        job.cancel()
    }

    @Test
    fun categoryClosesWhenMonthPassed() = runBlocking {
        val (_, azs) = ids("Т-Банк", "АЗС")
        val vm = categoryVm(azs, oct)
        val job = appScope.launch { vm.uiState.collect {} }
        awaitUntil { !vm.uiState.value.loading }
        assertTrue(!vm.uiState.value.closed)
        now = YearMonth.of(2026, 11)
        months.refresh()
        awaitUntil { vm.uiState.value.closed }
        job.cancel()
    }

    // Экраны «Банки» и «Категории»

    @Test
    fun deleteBankAsksWithCount() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        val (_, taxi) = ids("Т-Банк", "Такси")
        repo.setPermanent(t, azs, 10)
        repo.setMonthly(t, taxi, nov, 30)
        val vm = ManageViewModel(ManageKind.BANKS, repo)
        awaitUntil { vm.items.value != null }

        vm.openDelete(vm.items.value!!.first { it.id == t })
        awaitUntil { vm.dialog.value is ManageDialog.Delete }
        val dialog = vm.dialog.value as ManageDialog.Delete
        assertEquals("Удалить банк «Т-Банк»?", dialog.title)
        assertEquals("У банка 2 категории, они тоже удалятся.", dialog.message)

        vm.confirmDialog()
        awaitUntil { vm.items.value!!.none { it.id == t } }
        assertNull(vm.dialog.value)
        assertTrue(repo.permanentRates.first().isEmpty())
    }

    @Test
    fun deleteUnusedCategoryHasNoMessage() = runBlocking {
        val vm = ManageViewModel(ManageKind.CATEGORIES, repo)
        awaitUntil { vm.items.value != null }
        vm.openDelete(vm.items.value!!.first { it.name == "Цветы" })
        awaitUntil { vm.dialog.value is ManageDialog.Delete }
        assertNull((vm.dialog.value as ManageDialog.Delete).message)
    }

    @Test
    fun deleteCategoryMessage() = runBlocking {
        val (t, azs) = ids("Т-Банк", "АЗС")
        repo.setPermanent(t, azs, 10)
        val vm = ManageViewModel(ManageKind.CATEGORIES, repo)
        awaitUntil { vm.items.value != null }
        vm.openDelete(vm.items.value!!.first { it.id == azs })
        awaitUntil { vm.dialog.value is ManageDialog.Delete }
        assertEquals(
            "Категория выбрана у 1 банка, эти привязки тоже удалятся.",
            (vm.dialog.value as ManageDialog.Delete).message,
        )
    }

    @Test
    fun addDuplicateShowsError() = runBlocking {
        val vm = ManageViewModel(ManageKind.BANKS, repo)
        awaitUntil { vm.items.value != null }
        vm.openAdd()
        vm.onDialogText("альфа")
        vm.confirmDialog()
        awaitUntil { (vm.dialog.value as? ManageDialog.Add)?.error == "Такой банк уже есть" }
        vm.onDialogText("Сбер")
        vm.confirmDialog()
        awaitUntil { vm.dialog.value == null && vm.items.value!!.last().name == "Сбер" }
    }

    @Test
    fun renameCategory() = runBlocking {
        val vm = ManageViewModel(ManageKind.CATEGORIES, repo)
        awaitUntil { vm.items.value != null }
        vm.openRename(vm.items.value!!.first { it.name == "Кино" })
        vm.onDialogText("Кино и театр")
        vm.confirmDialog()
        awaitUntil { vm.items.value!!.any { it.name == "Кино и театр" } }
    }

    @Test
    fun banksCannotBeRenamed() = runBlocking {
        val vm = ManageViewModel(ManageKind.BANKS, repo)
        awaitUntil { vm.items.value != null }
        vm.openRename(vm.items.value!!.first())
        assertNull(vm.dialog.value)
    }

    @Test
    fun dragReorderIsSaved() = runBlocking {
        val vm = ManageViewModel(ManageKind.CATEGORIES, repo)
        awaitUntil { vm.items.value != null }
        val items = vm.items.value!!
        vm.move(items[0].id, items[1].id) // «Супермаркеты» на второе место
        vm.move(items[0].id, items[2].id) // и дальше на третье
        assertEquals(listOf("Кафе и рестораны", "Фастфуд", "Супермаркеты"), vm.items.value!!.take(3).map { it.name })
        vm.commitOrder()
        awaitUntil {
            runBlocking { repo.categories.first().take(3).map { it.name } } ==
                listOf("Кафе и рестораны", "Фастфуд", "Супермаркеты")
        }
    }
}
