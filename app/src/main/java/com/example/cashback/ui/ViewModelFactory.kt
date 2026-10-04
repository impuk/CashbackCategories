package com.example.cashback.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashback.AppContainer
import com.example.cashback.CashbackApp
import com.example.cashback.ui.category.CategoryViewModel
import com.example.cashback.ui.main.MainViewModel
import com.example.cashback.ui.manage.ManageKind
import com.example.cashback.ui.manage.ManageViewModel

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as CashbackApp).container

object AppViewModels {
    val main = viewModelFactory {
        initializer {
            val c = container()
            MainViewModel(c.repository, c.monthProvider, createSavedStateHandle())
        }
    }

    fun category(categoryId: Long, month: Int) = viewModelFactory {
        initializer {
            val c = container()
            CategoryViewModel(categoryId, month, c.repository, c.monthProvider, c.appScope)
        }
    }

    fun manage(kind: ManageKind) = viewModelFactory {
        initializer { ManageViewModel(kind, container().repository) }
    }
}
