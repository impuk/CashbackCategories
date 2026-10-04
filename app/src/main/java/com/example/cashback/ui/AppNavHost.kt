package com.example.cashback.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cashback.ui.category.CategoryScreen
import com.example.cashback.ui.main.MainScreen
import com.example.cashback.ui.manage.ManageKind
import com.example.cashback.ui.manage.ManageScreen

private object Routes {
    const val MAIN = "main"
    const val CATEGORY = "category/{categoryId}/{month}"
    const val MANAGE = "manage/{kind}"

    fun category(categoryId: Long, month: Int) = "category/$categoryId/$month"
    fun manage(kind: ManageKind) = "manage/${kind.name}"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) { entry ->
            MainScreen(
                viewModel = viewModel(entry, factory = AppViewModels.main),
                // Переход только из активного экрана: двойной тап не открывает два экрана.
                onOpenCategory = { id, month ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        nav.navigate(Routes.category(id, month))
                    }
                },
                onOpenCategories = dropUnlessResumed { nav.navigate(Routes.manage(ManageKind.CATEGORIES)) },
                onOpenBanks = dropUnlessResumed { nav.navigate(Routes.manage(ManageKind.BANKS)) },
            )
        }
        composable(
            Routes.CATEGORY,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.LongType },
                navArgument("month") { type = NavType.IntType },
            ),
        ) { entry ->
            val categoryId = entry.arguments!!.getLong("categoryId")
            val month = entry.arguments!!.getInt("month")
            CategoryScreen(
                viewModel = viewModel(entry, factory = AppViewModels.category(categoryId, month)),
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable(
            Routes.MANAGE,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kind = ManageKind.valueOf(entry.arguments!!.getString("kind")!!)
            ManageScreen(
                viewModel = viewModel(entry, factory = AppViewModels.manage(kind)),
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
    }
}
