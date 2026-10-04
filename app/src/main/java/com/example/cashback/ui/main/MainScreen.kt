package com.example.cashback.ui.main

import androidx.compose.foundation.clickable
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

@Composable
private fun SelectedRow(row: CategoryRow, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(row.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                row.ratesText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
