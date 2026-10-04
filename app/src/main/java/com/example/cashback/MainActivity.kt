package com.example.cashback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cashback.ui.AppNavHost
import com.example.cashback.ui.theme.CashbackTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val monthProvider = (application as CashbackApp).container.monthProvider
        // Пока приложение на экране, раз в минуту проверяем, не наступил ли новый месяц.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    monthProvider.refresh()
                    delay(60_000)
                }
            }
        }
        setContent {
            CashbackTheme {
                AppNavHost()
            }
        }
    }
}
