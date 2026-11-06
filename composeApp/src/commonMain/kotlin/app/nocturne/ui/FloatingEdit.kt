package app.nocturne.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object FloatingEdit {
    var editing by mutableStateOf(false)
    /** First-run setup: show the mini player even with nothing playing. */
    var preview by mutableStateOf(false)
    /** Pulsing outline so the eye finds the mini player. */
    var highlight by mutableStateOf(false)
}
