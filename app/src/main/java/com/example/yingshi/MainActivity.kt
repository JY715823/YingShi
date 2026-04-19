package com.example.yingshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.yingshi.app.YingShiApp
import com.example.yingshi.ui.theme.YingShiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YingShiTheme {
                YingShiApp()
            }
        }
    }
}
