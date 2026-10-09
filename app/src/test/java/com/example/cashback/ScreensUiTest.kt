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
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

    /** Рисует все окна (экран, меню, диалоги) в один bitmap. */
    private fun render(): Bitmap {
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
        return bitmap
    }

    /** Сохраняет снимок экрана в PNG для ручной проверки вёрстки. */
    private fun screenshot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { render().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Проверяет первую плашку категории: цвет фона и отступ текста от края фона (5 dp).
     */
    private fun checkFirstChip(tag: String, expectedBackground: Int) {
        val chip = compose.onNodeWithTag(tag, useUnmergedTree = true).onChildren()[0]
        val chipBounds = chip.getBoundsInRoot()
        val textBounds = chip.onChildren()[0].getBoundsInRoot()
        assertEquals(5f, (textBounds.left - chipBounds.left).value, 0.5f)
        assertEquals(2f, (textBounds.top - chipBounds.top).value, 0.5f)
        val density = compose.density.density
        val bitmap = render()
        // Точка внутри фона плашки: в левом отступе, посередине по высоте
        val x = ((chipBounds.left.value + 2.5f) * density).toInt()
        val y = (((chipBounds.top.value + chipBounds.bottom.value) / 2f) * density).toInt()
        assertEquals(
            "цвет фона плашки",
            String.format("#%06X", expectedBackground),
            String.format("#%06X", bitmap.getPixel(x, y) and 0xFFFFFF),
        )
    }

    private val mainState = MainUiState(
        loading = false,
        currentMonth = YearMonth.of(2026, 10),
        // Банки в каждой категории переданы в общем порядке списка, как их отдаёт buildMainRows.
        selected = listOf(
            CategoryRow.of(1, "Супермаркеты", listOf(BankRate(1, "Т-Банк", 30), BankRate(2, "Альфа", 15), BankRate(7, "ВТБ", 50))),
            CategoryRow.of(
                2, "Кафе и рестораны",
                listOf(BankRate(1, "Т-Банк", 50), BankRate(4, "Яндекс", 100), BankRate(5, "Озон", 70), BankRate(8, "Газпром", 30)),
            ),
            CategoryRow.of(3, "АЗС", listOf(BankRate(1, "Т-Банк", 15))),
            CategoryRow.of(4, "Аптеки", listOf(BankRate(2, "Альфа", 50), BankRate(3, "Халва", 50), BankRate(9, "WB", 20))),
            CategoryRow.of(
                5, "Маркетплейсы",
                listOf(BankRate(1, "Т-Банк", 50), BankRate(2, "Альфа", 10), BankRate(4, "Яндекс", 30), BankRate(5, "Озон", 50), BankRate(9, "WB", 50)),
            ),
        ),
        unselected = listOf(CategoryRow.of(6, "Кино", emptyList()), CategoryRow.of(7, "Цветы", emptyList())),
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
        // Лучший банк — плашкой, остальные — бледной строкой по убыванию процента
        assertEquals(listOf("ВТБ 5%"), texts("best_1"))
        assertEquals(listOf("Т-Банк 3%", "Альфа 1,5%"), texts("rest_1"))
        assertEquals(listOf("Яндекс 10%"), texts("best_2"))
        assertEquals(listOf("Озон 7%", "Т-Банк 5%", "Газпром 3%"), texts("rest_2"))
        // Один банк — только плашка, бледной строки нет
        assertEquals(listOf("Т-Банк 1,5%"), texts("best_3"))
        compose.onNodeWithTag("rest_3", useUnmergedTree = true).assertDoesNotExist()
        // Несколько лучших — плашки по порядку списка банков
        assertEquals(listOf("Альфа 5%", "Халва 5%"), texts("best_4"))
        assertEquals(listOf("Т-Банк 5%", "Озон 5%", "WB 5%"), texts("best_5"))
        assertEquals(listOf("Яндекс 3%", "Альфа 1%"), texts("rest_5"))
        // Текст плашек на одной линии с названием категории и бледной строкой
        val nameLeft = compose.onNodeWithText("Супермаркеты", useUnmergedTree = true).getBoundsInRoot().left
        val chipTextLeft = compose.onNodeWithTag("best_1", useUnmergedTree = true).onChildren()[0]
            .onChildren()[0].getBoundsInRoot().left
        val restLeft = compose.onNodeWithTag("rest_1", useUnmergedTree = true).onChildren()[0].getBoundsInRoot().left
        assertEquals(nameLeft.value, chipTextLeft.value, 0.5f)
        assertEquals(nameLeft.value, restLeft.value, 0.5f)
        // Плашка: отступы 5/2 dp и постоянный светлый зелёный фон
        checkFirstChip("best_1", 0xCDEBD8)
        compose.onNodeWithText("Без кэшбэка (2)").assertIsDisplayed()
        compose.onNodeWithText("Кино").assertDoesNotExist() // блок свёрнут
        screenshot("1_main")

        compose.onNodeWithText("Без кэшбэка (2)").performClick()
        compose.onNodeWithText("Кино").assertIsDisplayed()
        screenshot("2_main_expanded")

        compose.onNodeWithText("Ноябрь").performClick()
        assertEquals(true, next)
        compose.onNodeWithText("АЗС").performClick()
        assertEquals(3L, opened)
    }

    /** Тексты дочерних элементов ряда в порядке показа; неразрывный пробел заменён обычным. */
    private fun texts(tag: String): List<String> {
        val children = compose.onNodeWithTag(tag, useUnmergedTree = true).onChildren().fetchSemanticsNodes()
        return children.sortedBy { it.boundsInRoot.top * 10_000 + it.boundsInRoot.left }.map { node ->
            val own = node.config.getOrNull(SemanticsProperties.Text)
                ?: node.children.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            own.joinToString("") { it.text }.replace('\u00A0', ' ')
        }
    }

    @Test
    fun mainScreenDark() {
        compose.setContent {
            CashbackTheme(darkTheme = true, dynamicColor = false) {
                MainContent(mainState, false, {}, {}, {}, {}, {})
            }
        }
        assertEquals(listOf("Т-Банк 5%", "Озон 5%", "WB 5%"), texts("best_5"))
        checkFirstChip("best_1", 0x1B3A2A)
        screenshot("1_main_dark")
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
