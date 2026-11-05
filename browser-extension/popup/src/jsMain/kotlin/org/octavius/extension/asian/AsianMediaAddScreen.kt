package org.octavius.extension.asian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.octavius.modules.asian.model.AsianPublicationData
import org.octavius.modules.asian.model.PublicationAddRequest
import org.octavius.modules.asian.model.PublicationCheckRequest
import org.octavius.modules.asian.model.PublicationCheckResponse
import org.octavius.modules.asian.model.PublicationLinkRequest
import org.octavius.modules.asian.model.PublicationSummary
import org.octavius.modules.asian.model.TitleOpenRequest
import org.octavius.navigation.Screen

/**
 * Ekran dedykowany do wyświetlania danych sparsowanych ze strony i umożliwiający dodanie ich do bazy
 * danych Augustus - albo podpięcie do tytułu, który już tam jest.
 *
 * Co da się zrobić, zależy od tego, co znalazł `/check`: seria rozpoznana po identyfikatorze już jest
 * w bazie i można ją tylko otworzyć; podobny tytuł to podpowiedź, że może chodzi o istniejący tytuł;
 * brak dopasowania - zwykłe dodanie.
 */
class AsianMediaAddScreen(private val data: AsianPublicationData) : Screen {
    override val title = "asianMediaAddScreen"

    @Composable
    override fun Content() {
        var isLoading by remember { mutableStateOf(false) }
        var checkResponse by remember { mutableStateOf<PublicationCheckResponse?>(null) }
        var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
        val coroutineScope = rememberCoroutineScope()

        suspend fun check() = ApiClient.checkPublicationExistence(PublicationCheckRequest(data.titles, data.externalId))

        // Sprawdź przy wejściu na ekran, czy seria już jest w bazie
        LaunchedEffect(data) {
            checkResponse = check()
        }

        // Po udanym dodaniu albo podpięciu sprawdzamy jeszcze raz: seria jest już w bazie po
        // identyfikatorze, więc ekran przestaje proponować dodanie jej drugi raz.
        fun runAction(action: suspend () -> Pair<String, Boolean>) {
            isLoading = true
            statusMessage = null
            coroutineScope.launch {
                val result = action()
                if (result.second) checkResponse = check()
                statusMessage = result
                isLoading = false
            }
        }

        val addAsNew: suspend () -> Pair<String, Boolean> = {
            val response = ApiClient.addPublication(
                PublicationAddRequest(
                    titles = data.titles,
                    type = data.type,
                    language = data.language,
                    externalId = data.externalId
                )
            )
            Pair(response.message, response.success)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                "Augustus Helper",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Text(
                "Źródło: ${data.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Co już jest w bazie
            val check = checkResponse
            if (check != null && check.found) {
                if (check.byExternalId) {
                    StatusCard(
                        icon = Icons.Default.CheckCircle,
                        heading = "Już w bazie",
                        title = check.matchedTitle ?: "Nieznany tytuł",
                        publications = check.publications,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    StatusCard(
                        icon = Icons.Default.Warning,
                        heading = "Podobny tytuł w bazie!",
                        title = check.matchedTitle ?: "Nieznany tytuł",
                        publications = check.publications,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Content Card
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Wykryte tytuły:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (data.titles.isEmpty()) {
                        Text("Nie znaleziono tytułów...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        data.titles.forEach { title ->
                            Text(
                                "• $title",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    InfoRow("Typ", data.type.toDisplayString())
                    InfoRow("Język", data.language.toDisplayString())
                    InfoRow("Identyfikator", data.externalId?.id ?: "brak")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            val titleId = check?.titleId
            when {
                check == null -> ActionButton("Sprawdzam...", enabled = false, isLoading = true) {}

                check.byExternalId && titleId != null -> ActionButton("Otwórz w Augustusie", !isLoading, isLoading) {
                    runAction {
                        if (ApiClient.openTitle(TitleOpenRequest(titleId))) Pair("Otwarto w aplikacji", true)
                        else Pair("Nie można połączyć się z serwerem Augustus.", false)
                    }
                }

                check.found && titleId != null -> {
                    ActionButton("Podepnij do „${check.matchedTitle}”", !isLoading && data.titles.isNotEmpty(), isLoading) {
                        runAction {
                            val response = ApiClient.linkPublication(
                                PublicationLinkRequest(
                                    titleId = titleId,
                                    titles = data.titles,
                                    type = data.type,
                                    externalId = data.externalId
                                )
                            )
                            Pair(response.message, response.success)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { runAction(addAsNew) },
                        enabled = !isLoading && data.titles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Dodaj jako nowy tytuł")
                    }
                }

                else -> ActionButton("Dodaj do Augustusa", !isLoading && data.titles.isNotEmpty(), isLoading) {
                    runAction(addAsNew)
                }
            }

            // Status Message
            statusMessage?.let { (message, isSuccess) ->
                val color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    message,
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun ActionButton(label: String, enabled: Boolean, isLoading: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }

    @Composable
    private fun StatusCard(
        icon: ImageVector,
        heading: String,
        title: String,
        publications: List<PublicationSummary>,
        container: Color,
        content: Color
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = heading,
                tint = content,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Znaleziono: \"$title\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = content
                )
                if (publications.isNotEmpty()) {
                    Text(
                        publications.joinToString(" · ") { "${it.type.toDisplayString()}: ${it.status.toDisplayString()}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = content
                    )
                }
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
