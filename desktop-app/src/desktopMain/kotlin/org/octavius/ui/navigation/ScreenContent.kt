package org.octavius.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.octavius.navigation.Screen

/**
 * Rysuje ekran ze szczytu [screenStack] (ostatni element) i animuje przejście, gdy szczyt się zmienia.
 */
@Composable
fun TabContent(screenStack: List<Screen>) {
    val currentScreen = screenStack.lastOrNull() ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(targetState = currentScreen) { screen ->
            screen.Content()
        }
    }
}