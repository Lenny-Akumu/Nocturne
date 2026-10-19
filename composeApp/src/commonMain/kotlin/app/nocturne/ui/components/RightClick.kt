package app.nocturne.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.onRightClick(action: () -> Unit): Modifier = pointerInput(action) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                e.changes.forEach { it.consume() }
                action()
            }
        }
    }
}
