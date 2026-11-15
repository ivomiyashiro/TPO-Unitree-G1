package com.uade.vigia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uade.vigia.ui.AppShell
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.screens.RobotSelectionScreen
import com.uade.vigia.ui.theme.VigiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VigiaTheme {
                val vm: VigiaViewModel = viewModel(factory = VigiaViewModel.Factory)
                val state by vm.state.collectAsStateWithLifecycle()
                if (state.selectedRobot == null) {
                    RobotSelectionScreen(state, onConnect = vm::selectRobot)
                } else {
                    BackHandler(onBack = vm::disconnectFromRobot)
                    AppShell(state, vm)
                }
            }
        }
    }
}
