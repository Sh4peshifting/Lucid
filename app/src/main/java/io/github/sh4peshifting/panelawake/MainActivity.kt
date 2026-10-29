package io.github.sh4peshifting.panelawake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: LucidViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            LucidTheme {
                LucidScreen(state, model::requestConnection, model::setKeepAwake, model::setPanelOff, model::restore)
            }
        }
    }
}
