package com.lynchscreener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lynchscreener.ui.AppNavHost
import com.lynchscreener.ui.theme.LynchScreenerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as LynchScreenerApp).container

        setContent {
            LynchScreenerTheme {
                AppNavHost(container)
            }
        }
    }
}
