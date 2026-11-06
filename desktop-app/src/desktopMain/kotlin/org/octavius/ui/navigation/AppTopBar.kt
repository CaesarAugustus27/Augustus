package org.octavius.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.octavius.navigation.AppNavigationState
import org.octavius.navigation.Tab

/**
 * Pasek zakładek: [tabs] po równo na całą szerokość, aktywna z [currentState] podświetlona. Kliknięta
 * zakładka trafia do [onTabSelected].
 */
@Composable
fun AppTopBar(
    tabs: List<Tab>,
    currentState: AppNavigationState,
    onTabSelected: (Tab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp), color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                TopBarTab(
                    tab = tab,
                    isSelected = (tab == currentState.activeTab),
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.TopBarTab(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            tab.options.icon?.let { icon ->
                Icon(
                    painter = icon,
                    contentDescription = tab.options.title,
                    tint = contentColor
                )
            }
            Text(
                text = tab.options.title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = contentColor
            )
        }
    }
}