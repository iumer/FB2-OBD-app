package com.fb2.obd

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.theme.FB2Theme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the cluster awake while mounted on the dash.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FB2Theme {
                val state by viewModel.uiState.collectAsState()
                DashboardScreen(state = state, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
