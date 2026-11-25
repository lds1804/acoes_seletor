package com.lynchscreener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lynchscreener.ui.AppNavHost
import com.lynchscreener.ui.theme.LynchScreenerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LynchScreenerApp).container

        setContent {
            // Dark mode is the default; pass darkTheme = false for light.
            LynchScreenerTheme(darkTheme = true) {
                AppNavHost(container)
            }
        }
    }
}
