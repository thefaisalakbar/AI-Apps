package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.CandidateDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ShareToPcScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExamViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.UiEvent
import com.example.utils.FileManager

class MainActivity : ComponentActivity() {

    private val viewModel: ExamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    LaunchedEffect(Unit) {
                        viewModel.uiEvents.collect { event ->
                            when (event) {
                                is UiEvent.ShowToast -> {
                                    Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                                }
                                is UiEvent.ShareFile -> {
                                    FileManager.shareFile(
                                        this@MainActivity,
                                        event.file,
                                        event.mimeType,
                                        event.title
                                    )
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(viewModel = viewModel)
                            Screen.CANDIDATE_DETAIL -> CandidateDetailScreen(viewModel = viewModel)
                            Screen.PHOTO_CAPTURE -> CameraCaptureScreen(viewModel = viewModel)
                            Screen.PC_TRANSFER -> ShareToPcScreen(viewModel = viewModel)
                            Screen.SETTINGS -> HomeScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
