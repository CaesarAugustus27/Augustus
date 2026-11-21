package io.github.octaviusframework.form.control.type.selection.dropdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.RenderNormalLabel
import io.github.octaviusframework.form.localization.FormTr

/**
 * Base class of all dropdown controls.
 * Only renders the UI frame (ExposedDropdownMenuBox) and leaves the menu content to subclasses.
 */
abstract class DropdownControlBase<T : Any>(
    label: String?,
    required: Boolean?,
    dependencies: Map<String, ControlDependency<*>>?,
    actions: List<ControlAction<T>>?
) : Control<T>(
    label,
    required,
    dependencies,
    hasStandardLayout = false,
    actions = actions
) {

    /**
     * Works out the text of the selected value. Called **once per value**, outside the rendering path, and the
     * result lands in `ControlState.displayText` - which is why querying the database or an API is fine here.
     *
     * Returning `null` means "nothing to show" (e.g. the row disappeared from under the saved id); the control
     * then shows a placeholder text.
     */
    protected abstract suspend fun resolveDisplayText(value: T): String?

    /**
     * Subclasses implement this to render the content of the dropdown menu.
     *
     * They get the whole [ControlState], not just the value, because selecting an item sets the value **and**
     * the text - the menu already knows the label, so there is no reason to work it out a second time.
     *
     * [menuScrollState] is the scroll state of the menu itself. This class creates it, because it calls
     * `ExposedDropdownMenu`, but the subclass loading further pages reads it - hence the parameter. A subclass
     * with the full list of options simply ignores it.
     */
    @Composable
    protected abstract fun ColumnScope.RenderMenuItems(
        controlContext: ControlContext,
        scope: CoroutineScope,
        controlState: ControlState<T>,
        menuScrollState: ScrollState,
        closeMenu: () -> Unit
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<T>, isRequired: Boolean) {
        var expanded by remember { mutableStateOf(false) }
        val scope = rememberSupervisedCoroutineScope()

        // The value comes from the database, its text does not - and working it out can cost a query or a
        // network round-trip, so it cannot happen on every recomposition. It is resolved once, here, and the
        // result sits in the state keyed by path, i.e. separately for every repeatable row.
        //
        // Three states of the text: `null` - not resolved yet, `""` - resolved and nothing to show (the row
        // disappeared from under the saved id), anything else - the label. Without this distinction a failed
        // resolution would leave the field on "loading" forever.
        val resolvedText = controlState.displayText.value
        val unresolved = controlState.value.value != null && resolvedText == null

        LaunchedEffect(controlState.value.value, resolvedText) {
            val value = controlState.value.value
            if (value != null && controlState.displayText.value == null) {
                // Resolving the label happens without the user, right when the form opens, and there is
                // nowhere to show an error - the menu is closed. Instead of crashing the application or
                // flooding it with dialogs for several rows at once, a placeholder text stays; the message
                // shows up only once the list is opened.
                controlState.displayText.value = try {
                    resolveDisplayText(value) ?: ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ""
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            RenderNormalLabel(label, isRequired)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = resolvedText?.takeIf { it.isNotEmpty() } ?: when {
                        unresolved -> FormTr.Form.Dropdown.loading()
                        !isRequired -> FormTr.Form.Dropdown.noSelection()
                        else -> FormTr.Form.Dropdown.selectOption()
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                // Menu with the options. The scroll state is kept here, because loading further pages runs
                // off the menu's scroll position - the menu already scrolls, so there is no point in putting
                // a second scrollable list inside it.
                val menuScrollState = rememberScrollState()

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    scrollState = menuScrollState
                ) {
                    RenderMenuItems(
                        controlContext = controlContext,
                        scope = scope,
                        controlState = controlState,
                        menuScrollState = menuScrollState,
                        closeMenu = { expanded = false }
                    )
                }
            }
            DisplayFieldErrors(controlContext)
        }
    }
}
