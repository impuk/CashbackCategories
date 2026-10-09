package com.example.cashback.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.example.cashback.domain.BankRate
import com.example.cashback.domain.Percent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashback.domain.CategoryRow
import com.example.cashback.domain.Months

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenCategory: (categoryId: Long, month: Int) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBanks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded by viewModel.unselectedExpanded.collectAsStateWithLifecycle()
    MainContent(
        state = state,
        unselectedExpanded = expanded,
        onShowNext = viewModel::showNext,
        onToggleUnselected = viewModel::toggleUnselected,
        onOpenCategory = { onOpenCategory(it, Months.key(state.shownMonth)) },
        onOpenCategories = onOpenCategories,
        onOpenBanks = onOpenBanks,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    state: MainUiState,
    unselectedExpanded: Boolean,
    onShowNext: (Boolean) -> Unit,
    onToggleUnselected: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBanks: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кэшбэк") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Категории") },
                                onClick = { menuOpen = false; onOpenCategories() },
                            )
                            DropdownMenuItem(
                                text = { Text("Банки") },
                                onClick = { menuOpen = false; onOpenBanks() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "months") {
                MonthSwitch(state, onShowNext)
            }
            if (state.loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }
            if (state.selected.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = Months.inMonth(state.shownMonth).replaceFirstChar { it.uppercase() } +
                            " кэшбэк пока не выбран. Нажмите на категорию ниже, " +
                            "чтобы указать банки и проценты.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            items(state.selected, key = { "s${it.categoryId}" }) { row ->
                SelectedRow(row, onClick = { onOpenCategory(row.categoryId) })
            }
            if (state.unselected.isNotEmpty()) {
                item(key = "unselectedHeader") {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    ListItem(
                        headlineContent = { Text("Без кэшбэка (${state.unselected.size})") },
                        trailingContent = {
                            Icon(
                                if (unselectedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (unselectedExpanded) "Свернуть" else "Развернуть",
                            )
                        },
                        modifier = Modifier.clickable(role = Role.Button, onClick = onToggleUnselected),
                    )
                }
                if (unselectedExpanded) {
                    items(state.unselected, key = { "u${it.categoryId}" }) { row ->
                        ListItem(
                            headlineContent = {
                                Text(row.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable { onOpenCategory(row.categoryId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSwitch(state: MainUiState, onShowNext: (Boolean) -> Unit) {
    val months = listOf(state.currentMonth, state.nextMonth)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            months.forEachIndexed { index, month ->
                SegmentedButton(
                    selected = state.showingNext == (index == 1),
                    onClick = { onShowNext(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index, months.size),
                ) {
                    Text(Months.name(month))
                }
            }
        }
    }
}

/** Горизонтальный отступ внутри плашки: на столько же плашки сдвинуты влево. */
private val ChipPaddingH = 8.dp

/**
 * Выбранная категория: название, ниже плашки лучших банков, ниже бледная строка остальных.
 * Текст плашек стоит на одной линии с названием, фон плашки выступает влево.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedRow(row: CategoryRow, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier
                .padding(top = 6.dp)
                .extendStart(ChipPaddingH)
                .testTag("best_${row.categoryId}"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.best.forEach { BestChip(it) }
        }
        if (row.rest.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp).testTag("rest_${row.categoryId}"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.rest.forEach { rate ->
                    Text(
                        text = rate.bankName + "\u00A0" + Percent.format(rate.percentTenths) + "%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BestChip(rate: BankRate) {
    val percentColor = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = buildAnnotatedString {
                append(rate.bankName)
                append('\u00A0')
                withStyle(SpanStyle(color = percentColor, fontWeight = FontWeight.Medium)) {
                    append(Percent.format(rate.percentTenths) + "%")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = ChipPaddingH, vertical = 3.dp),
        )
    }
}

/** Расширяет элемент влево на [extra] за пределы родителя, не сдвигая правый край. */
private fun Modifier.extendStart(extra: Dp): Modifier = layout { measurable, constraints ->
    val px = extra.roundToPx()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + px else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth, minWidth = 0))
    val width = (placeable.width - px).coerceAtLeast(0).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.placeRelative(-px, 0) }
}
