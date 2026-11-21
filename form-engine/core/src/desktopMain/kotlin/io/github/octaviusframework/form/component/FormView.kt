package io.github.octaviusframework.form.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.localization.FormTr

/**
 * Form UI - renders the controls provided by [FormHandler].
 *
 * The screen title and the place on the navigation stack belong to navigation, not to the form engine. A
 * module that knows the navigation wraps this in `ComponentScreen(title) { FormView(formHandler) }`.
 */
@Composable
fun FormView(formHandler: FormHandler) {
    val loadState by formHandler.loadState
    val actionTriggered by formHandler.actionTriggered

    Box(modifier = Modifier.fillMaxSize()) {
        if (loadState != FormLoadState.Loaded) {
            // Before loading and after a failed load the controls have no states, so they are not rendered.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (loadState == FormLoadState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = FormTr.Form.Load.failed(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            // Form loaded - render the controls
            Column(modifier = Modifier.fillMaxSize()) {
                // Main content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Global errors
                    val globalErrors by formHandler.errorManager.globalErrors
                    if (globalErrors.isNotEmpty()) {
                        GlobalErrorsCard(errors = globalErrors)
                    }

                    // content
                    formHandler.getContentControlsInOrder().forEach { controlName ->
                        val control = formHandler.getControl(controlName)!!
                        val state = formHandler.getControlState(controlName)!!
                        control.Render(controlContext = ControlContext(controlName), controlState = state)
                    }
                } // End of the scrolled area

                // Action bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        formHandler.getActionBarControlsInOrder().forEach { controlName ->
                            val control = formHandler.getControl(controlName)!!
                            val state = formHandler.getControlState(controlName)!!
                            Box(modifier = Modifier.weight(1f)) {
                                control.Render(controlContext = ControlContext(controlName), controlState = state)
                            }
                        }
                    }
                } // End of the action bar
            }

            // Overlay blocking interaction while an action is running
            if (actionTriggered) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .pointerInput(Unit) { /* swallows all events */ },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Shows the global errors
 */
@Composable
private fun GlobalErrorsCard(errors: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = FormTr.Form.Actions.errorsLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            errors.forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
