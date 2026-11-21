package org.octavius.modules.sandbox.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.octavius.modules.sandbox.ComponentTestScreen
import org.octavius.modules.sandbox.form.nested.SandboxNestedFormScreen
import org.octavius.modules.sandbox.form.ui.SandboxFormScreen
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.modules.sandbox.popup.PopupShowcaseScreen
import org.octavius.modules.sandbox.report.ui.SandboxReportScreen
import org.octavius.navigation.AppRouter
import org.octavius.navigation.Screen

class SandboxHomeScreen : Screen {

    override val title = SandboxTr.Home.title()

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = SandboxTr.Home.mainText(),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = SandboxTr.Home.description(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = SandboxTr.Home.popups(),
                    description = SandboxTr.Home.popupsDescription(),
                    icon = Icons.Default.BugReport,
                    onClick = { AppRouter.navigateTo(PopupShowcaseScreen.create()) },
                    modifier = Modifier.weight(1f)
                )

                QuickActionCard(
                    title = SandboxTr.Home.form(),
                    description = SandboxTr.Home.formDescription(),
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    onClick = { AppRouter.navigateTo(SandboxFormScreen.create()) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = SandboxTr.Home.report(),
                    description = SandboxTr.Home.reportDescription(),
                    icon = Icons.Default.TableChart,
                    onClick = { AppRouter.navigateTo(SandboxReportScreen.create()) },
                    modifier = Modifier.weight(1f)
                )

                QuickActionCard(
                    title = SandboxTr.componentTestScreen(),
                    description = "",
                    icon = Icons.Default.TableChart,
                    onClick = { AppRouter.navigateTo(ComponentTestScreen()) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = SandboxTr.Home.nestedForm(),
                    description = SandboxTr.Home.nestedFormDescription(),
                    icon = Icons.Default.AccountTree,
                    onClick = { AppRouter.navigateTo(SandboxNestedFormScreen.create()) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    companion object {
        fun create(): SandboxHomeScreen = SandboxHomeScreen()
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
