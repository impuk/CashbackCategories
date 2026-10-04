package com.example.cashback.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ManageScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    ManageContent(
        kind = viewModel.kind,
        items = items,
        snackbar = snackbar,
        onBack = onBack,
        onAdd = viewModel::openAdd,
        onRename = viewModel::openRename,
        onDelete = viewModel::openDelete,
        onMove = viewModel::move,
        onDragStopped = viewModel::commitOrder,
    )
    dialog?.let {
        ManageDialogView(
            kind = viewModel.kind,
            dialog = it,
            onText = viewModel::onDialogText,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageContent(
    kind: ManageKind,
    items: List<ManageItem>?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRename: (ManageItem) -> Unit,
    onDelete: (ManageItem) -> Unit,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onDragStopped: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (kind == ManageKind.BANKS) "Добавить банк" else "Добавить категорию",
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val haptic = LocalHapticFeedback.current
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            onMove(from.key, to.key)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item(key = "hint") {
                Text(
                    "Порядок меняется перетаскиванием за ☰" +
                        if (kind.canRename) ". Нажмите на категорию, чтобы переименовать." else ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(items, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { isDragging ->
                    Surface(shadowElevation = if (isDragging) 6.dp else 0.dp) {
                        ListItem(
                            leadingContent = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = { onDragStopped() },
                                    ),
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Перетащить «${item.name}»")
                                }
                            },
                            headlineContent = { Text(item.name) },
                            trailingContent = {
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить «${item.name}»")
                                }
                            },
                            colors = ListItemDefaults.colors(),
                            modifier = Modifier
                                .longPressDraggableHandle(onDragStopped = { onDragStopped() })
                                .let { m -> if (kind.canRename) m.clickable { onRename(item) } else m },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManageDialogView(
    kind: ManageKind,
    dialog: ManageDialog,
    onText: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    autoFocus: Boolean = true,
) {
    when (dialog) {
        is ManageDialog.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialog.title) },
            text = dialog.message?.let { { Text(it) } },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        is ManageDialog.Add, is ManageDialog.Rename -> {
            val (title, text, error) = when (dialog) {
                is ManageDialog.Add ->
                    Triple(if (kind == ManageKind.BANKS) "Новый банк" else "Новая категория", dialog.text, dialog.error)
                is ManageDialog.Rename -> Triple("Переименовать", dialog.text, dialog.error)
                else -> error("unreachable")
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    // Поле живёт в окне диалога, поэтому фокус запрашивается здесь, после его появления.
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
                    OutlinedTextField(
                        value = text,
                        onValueChange = onText,
                        label = { Text("Название") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.focusRequester(focus),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirm, enabled = text.isNotBlank()) {
                        Text(if (dialog is ManageDialog.Add) "Добавить" else "Сохранить")
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
            )
        }
    }
}
