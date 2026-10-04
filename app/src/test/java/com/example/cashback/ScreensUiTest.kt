package com.example.cashback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashback.domain.BankRate
import com.example.cashback.domain.CategoryRow
import com.example.cashback.ui.category.BankFieldsState
import com.example.cashback.ui.category.CategoryContent
import com.example.cashback.ui.category.CategoryUiState
import com.example.cashback.ui.category.RateField
import com.example.cashback.ui.main.MainContent
import com.example.cashback.ui.main.MainUiState
import com.example.cashback.ui.manage.ManageContent
import com.example.cashback.ui.manage.ManageDialog
import com.example.cashback.ui.manage.ManageDialogView
import com.example.cashback.ui.manage.ManageItem
import com.example.cashback.ui.manage.ManageKind
import com.example.cashback.ui.theme.CashbackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ScreensUiTest {
    @get:Rule
    val compose = createComposeRule()

    /** Рисует все окна (экран, меню, диалоги) в один PNG для ручной проверки вёрстки. */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>
        val main = views.first()
        val bitmap = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEachIndexed { index, view ->
            if (index > 0) canvas.drawColor(0x66000000) // затемнение под диалогом
            val loc = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(loc[0].toFloat(), loc[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val mainState = MainUiState(
        loading = false,
        currentMonth = YearMonth.of(2026, 10),
        selected = listOf(
            CategoryRow(1, "Супермаркеты", listOf(BankRate(1, "Т-Банк", 50), BankRate(2, "Альфа", 30))),
            CategoryRow(2, "АЗС", listOf(BankRate(1, "Т-Банк", 15))),
            CategoryRow(3, "Аптеки", listOf(BankRate(2, "Альфа", 10), BankRate(7, "ВТБ", 50), BankRate(9, "WB", 20))),
        ),
        unselected = listOf(CategoryRow(4, "Кино", emptyList()), CategoryRow(5, "Цветы", emptyList())),
    )

    @Test
    fun mainScreen() {
        var expanded by mutableStateOf(false)
        var opened: Long? = null
        var next: Boolean? = null
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                MainContent(
                    state = mainState,
                    unselectedExpanded = expanded,
                    onShowNext = { next = it },
                    onToggleUnselected = { expanded = !expanded },
                    onOpenCategory = { opened = it },
                    onOpenCategories = {},
                    onOpenBanks = {},
                )
            }
        }
        compose.onNodeWithText("Т-Банк 5% · Альфа 3%").assertIsDisplayed()
        compose.onNodeWithText("Альфа 1% · ВТБ 5% · WB 2%").assertIsDisplayed()
        compose.onNodeWithText("Без кэшбэка (2)").assertIsDisplayed()
        compose.onNodeWithText("Кино").assertDoesNotExist() // блок свёрнут
        screenshot("1_main")

        compose.onNodeWithText("Без кэшбэка (2)").performClick()
        compose.onNodeWithText("Кино").assertIsDisplayed()
        screenshot("2_main_expanded")

        compose.onNodeWithText("Ноябрь").performClick()
        assertEquals(true, next)
        compose.onNodeWithText("АЗС").performClick()
        assertEquals(2L, opened)
    }

    @Test
    fun mainMenu() {
        var categories = 0
        var banks = 0
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                MainContent(mainState, false, {}, {}, {}, { categories++ }, { banks++ })
            }
        }
        compose.onNodeWithContentDescriptionCompat("Меню").performClick()
        screenshot("3_main_menu")
        compose.onNodeWithText("Банки").performClick()
        assertEquals(1, banks)
        compose.onNodeWithContentDescriptionCompat("Меню").performClick()
        compose.onNodeWithText("Категории").performClick()
        assertEquals(1, categories)
    }

    @Test
    fun mainEmpty() {
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                MainContent(
                    mainState.copy(selected = emptyList(), showingNext = true),
                    false, {}, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithText("В ноябре кэшбэк пока не выбран", substring = true).assertIsDisplayed()
        screenshot("4_main_empty_next_month")
    }

    @Test
    fun categoryScreen() {
        val changes = mutableListOf<Triple<Long, RateField, String>>()
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                CategoryContent(
                    state = CategoryUiState(
                        loading = false,
                        categoryName = "Аптеки",
                        month = YearMonth.of(2026, 10),
                        banks = listOf(
                            BankFieldsState(1, "Т-Банк", "", ""),
                            BankFieldsState(2, "Альфа", "1", "5"),
                            BankFieldsState(3, "Халва", "0", ""),
                            BankFieldsState(4, "Яндекс", "", "1,5"),
                        ),
                    ),
                    snackbar = SnackbarHostState(),
                    onBack = {},
                    onTextChange = { id, field, text -> changes += Triple(id, field, text) },
                )
            }
        }
        compose.onNodeWithText("Аптеки").assertIsDisplayed()
        compose.onNodeWithText("Октябрь 2026").assertIsDisplayed()
        compose.onNodeWithText("Больше 0").assertIsDisplayed()
        screenshot("5_category")

        compose.onNodeWithTag("monthly_1").performTextInput("3")
        assertEquals(Triple(1L, RateField.MONTHLY, "3"), changes.last())
    }

    @Test
    fun manageScreens() {
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                ManageContent(
                    kind = ManageKind.BANKS,
                    items = listOf("Т-Банк", "Альфа", "Халва", "Яндекс", "Озон").mapIndexed { i, n -> ManageItem(i.toLong(), n) },
                    snackbar = SnackbarHostState(),
                    onBack = {}, onAdd = {}, onRename = {}, onDelete = {}, onMove = { _, _ -> }, onDragStopped = {},
                )
                ManageDialogView(
                    kind = ManageKind.BANKS,
                    dialog = ManageDialog.Delete(0, "Удалить банк «Т-Банк»?", "У банка 2 категории, они тоже удалятся."),
                    onText = {}, onConfirm = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("У банка 2 категории, они тоже удалятся.").assertIsDisplayed()
        screenshot("6_banks_delete")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionCompat(d: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(d))
}

/**
 * Отдельный класс без размера экрана: в Robolectric диалог Material с полем ввода
 * на xxhdpi никогда не доходит до простоя (воспроизводится и без кода приложения).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DialogUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun categoriesAddDialog() {
        compose.setContent {
            CashbackTheme(dynamicColor = false) {
                ManageContent(
                    kind = ManageKind.CATEGORIES,
                    items = listOf("Супермаркеты", "Кафе и рестораны", "Фастфуд", "АЗС").mapIndexed { i, n -> ManageItem(i.toLong(), n) },
                    snackbar = SnackbarHostState(),
                    onBack = {}, onAdd = {}, onRename = {}, onDelete = {}, onMove = { _, _ -> }, onDragStopped = {},
                )
                ManageDialogView(
                    kind = ManageKind.CATEGORIES,
                    dialog = ManageDialog.Add("Такси", "Такая категория уже есть"),
                    onText = {}, onConfirm = {}, onDismiss = {},
                    autoFocus = false, // мигающий курсор не даёт Robolectric дождаться простоя

                )
            }
        }
        compose.onNodeWithText("Такая категория уже есть").assertIsDisplayed()
    }
}
