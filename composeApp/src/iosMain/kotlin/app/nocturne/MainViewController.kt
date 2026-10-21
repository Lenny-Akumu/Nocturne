package app.nocturne

import androidx.compose.ui.window.ComposeUIViewController
import app.nocturne.player.IosAudioEngine
import app.nocturne.ui.App
import platform.UIKit.UIViewController

private val graph by lazy { AppGraph.init(IosAudioEngine()) }

fun MainViewController(): UIViewController {
    graph
    return ComposeUIViewController { App() }
}
