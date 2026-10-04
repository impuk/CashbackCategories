package com.example.cashback.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashback.data.CashbackRepository
import com.example.cashback.data.NameResult
import com.example.cashback.domain.ruPlural
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Экран «Банки» или «Категории». У банков нет переименования (по ТЗ). */
enum class ManageKind(val title: String, val canRename: Boolean) {
    BANKS("Банки", canRename = false),
    CATEGORIES("Категории", canRename = true),
}

data class ManageItem(val id: Long, val name: String)

sealed interface ManageDialog {
    data class Add(val text: String = "", val error: String? = null) : ManageDialog
    data class Rename(val id: Long, val text: String, val error: String? = null) : ManageDialog
    data class Delete(val id: Long, val title: String, val message: String?) : ManageDialog
}

class ManageViewModel(
    val kind: ManageKind,
    private val repository: CashbackRepository,
) : ViewModel() {
    private val _items = MutableStateFlow<List<ManageItem>?>(null)
    /** null — ещё загружается. */
    val items: StateFlow<List<ManageItem>?> = _items.asStateFlow()

    private val _dialog = MutableStateFlow<ManageDialog?>(null)
    val dialog: StateFlow<ManageDialog?> = _dialog.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Пока строку тащат, список из базы не перетирает локальный порядок. */
    private var dragging = false

    init {
        viewModelScope.launch {
            val source = when (kind) {
                ManageKind.BANKS -> repository.banks.map { list -> list.map { ManageItem(it.id, it.name) } }
                ManageKind.CATEGORIES -> repository.categories.map { list -> list.map { ManageItem(it.id, it.name) } }
            }
            source.collect { if (!dragging) _items.value = it }
        }
    }

    // Порядок

    fun move(fromKey: Any, toKey: Any) {
        val list = _items.value?.toMutableList() ?: return
        val from = list.indexOfFirst { it.id == fromKey }
        val to = list.indexOfFirst { it.id == toKey }
        if (from < 0 || to < 0) return
        dragging = true
        list.add(to, list.removeAt(from))
        _items.value = list
    }

    fun commitOrder() {
        val ids = _items.value?.map { it.id } ?: return
        if (!dragging) return
        viewModelScope.launch {
            try {
                when (kind) {
                    ManageKind.BANKS -> repository.reorderBanks(ids)
                    ManageKind.CATEGORIES -> repository.reorderCategories(ids)
                }
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось сохранить порядок")
            } finally {
                dragging = false
            }
        }
    }

    // Диалоги

    fun openAdd() {
        _dialog.value = ManageDialog.Add()
    }

    fun openRename(item: ManageItem) {
        if (kind.canRename) _dialog.value = ManageDialog.Rename(item.id, item.name)
    }

    fun openDelete(item: ManageItem) {
        viewModelScope.launch {
            try {
                _dialog.value = when (kind) {
                    ManageKind.BANKS -> {
                        val n = repository.countCategoriesOfBank(item.id)
                        ManageDialog.Delete(
                            id = item.id,
                            title = "Удалить банк «${item.name}»?",
                            message = if (n > 0) {
                                "У банка $n ${ruPlural(n, "категория", "категории", "категорий")}, они тоже удалятся."
                            } else null,
                        )
                    }
                    ManageKind.CATEGORIES -> {
                        val n = repository.countBanksOfCategory(item.id)
                        ManageDialog.Delete(
                            id = item.id,
                            title = "Удалить категорию «${item.name}»?",
                            message = if (n > 0) {
                                "Категория выбрана у $n ${ruPlural(n, "банка", "банков", "банков")}, " +
                                    "эти привязки тоже удалятся."
                            } else null,
                        )
                    }
                }
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось прочитать данные")
            }
        }
    }

    fun onDialogText(text: String) {
        if (text.length > MAX_NAME) return
        _dialog.value = when (val d = _dialog.value) {
            is ManageDialog.Add -> d.copy(text = text, error = null)
            is ManageDialog.Rename -> d.copy(text = text, error = null)
            else -> d
        }
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun confirmDialog() {
        val d = _dialog.value ?: return
        viewModelScope.launch {
            try {
                when (d) {
                    is ManageDialog.Add -> {
                        val result = when (kind) {
                            ManageKind.BANKS -> repository.addBank(d.text)
                            ManageKind.CATEGORIES -> repository.addCategory(d.text)
                        }
                        handleNameResult(result) { _dialog.value = d.copy(error = it) }
                    }
                    is ManageDialog.Rename -> {
                        val result = repository.renameCategory(d.id, d.text)
                        handleNameResult(result) { _dialog.value = d.copy(error = it) }
                    }
                    is ManageDialog.Delete -> {
                        when (kind) {
                            ManageKind.BANKS -> repository.deleteBank(d.id)
                            ManageKind.CATEGORIES -> repository.deleteCategory(d.id)
                        }
                        _dialog.value = null
                    }
                }
            } catch (e: Exception) {
                _dialog.value = null
                _messages.tryEmit("Не удалось сохранить")
            }
        }
    }

    private fun handleNameResult(result: NameResult, showError: (String) -> Unit) {
        when (result) {
            NameResult.OK -> _dialog.value = null
            NameResult.BLANK -> showError("Введите название")
            NameResult.DUPLICATE -> showError(
                if (kind == ManageKind.BANKS) "Такой банк уже есть" else "Такая категория уже есть"
            )
        }
    }

    companion object {
        const val MAX_NAME = 40
    }
}
