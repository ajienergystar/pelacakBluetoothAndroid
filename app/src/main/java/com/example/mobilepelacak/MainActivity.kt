package com.example.mobilepelacak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mobilepelacak.ui.navigation.AppNavHost
import com.example.mobilepelacak.ui.theme.MobilePelacakTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobilePelacakTheme {
                AppNavHost()
            }
        }
    }
}
