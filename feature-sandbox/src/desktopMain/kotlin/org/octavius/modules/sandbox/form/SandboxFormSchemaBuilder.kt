package org.octavius.modules.sandbox.form

import io.github.octaviusframework.form.component.FormSchemaBuilder
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.ControlOrientation
import io.github.octaviusframework.form.control.type.button.ButtonControl
import io.github.octaviusframework.form.control.type.button.ButtonType
import io.github.octaviusframework.form.control.type.collection.StringListControl
import io.github.octaviusframework.form.control.type.container.SectionControl
import io.github.octaviusframework.form.control.type.datetime.DateControl
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.number.IntegerValidation
import io.github.octaviusframework.form.control.type.primitive.CheckboxControl
import io.github.octaviusframework.form.control.type.primitive.FilePickerControl
import io.github.octaviusframework.form.control.type.primitive.FilePickerMode
import io.github.octaviusframework.form.control.type.primitive.MultilineStringControl
import io.github.octaviusframework.form.control.type.primitive.StringControl
import io.github.octaviusframework.form.control.type.primitive.SwitchControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableValidation
import io.github.octaviusframework.form.control.type.selection.CheckboxGroupControl
import io.github.octaviusframework.form.control.type.selection.EnumControl
import io.github.octaviusframework.form.control.type.selection.RadioGroupControl
import io.github.octaviusframework.form.control.type.selection.SelectionOption
import org.octavius.localization.Tr
import org.octavius.modules.sandbox.domain.SandboxPriority
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.navigation.AppRouter

class SandboxFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "basic_info" to SectionControl(
            controls = mapOf(
                "name" to StringControl(
                    SandboxTr.Form.name(),
                    required = true
                ),
                "quantity" to IntegerControl(
                    SandboxTr.Form.quantity(),
                    validationOptions = IntegerValidation(min = 0)
                ),
                "active" to CheckboxControl(
                    SandboxTr.Form.active(),
                    required = true
                ),
                "priority" to EnumControl(
                    SandboxTr.Form.priority(),
                    SandboxPriority::class,
                    required = true
                ),
                "start_date" to DateControl(
                    SandboxTr.Form.startDate()
                ),
                "tags" to StringListControl(
                    SandboxTr.Form.tags()
                ),
                "radio_test" to RadioGroupControl(
                    label = "Test Radio (Poziom)",
                    options = listOf(
                        SelectionOption("opcja1", "Opcja 1"),
                        SelectionOption("opcja2", "Opcja 2"),
                        SelectionOption("opcja3", "Opcja 3")
                    ),
                    orientation = ControlOrientation.HORIZONTAL
                ),
                "checkbox_test" to CheckboxGroupControl(
                    label = "Test Checkbox (Pion)",
                    options = listOf(
                        SelectionOption("opcjaA", "Opcja A"),
                        SelectionOption("opcjaB", "Opcja B"),
                        SelectionOption("opcjaC", "Opcja C")
                    ),
                    orientation = ControlOrientation.VERTICAL
                ),
                "multiline_test" to MultilineStringControl(
                    label = "Test Multiline (Rozszerzalne)",
                    required = false
                ),
                "switch_test" to SwitchControl(
                    label = "Test Switch (Przełącznik)"
                ),
                "file_picker_path" to FilePickerControl<String>(
                    label = "Wybór pliku (Ścieżka)",
                    mode = FilePickerMode.PATH_STRING
                ),
                "file_picker_bytes" to FilePickerControl<ByteArray>(
                    label = "Wybór pliku (Zawartość)",
                    mode = FilePickerMode.CONTENT_BYTES
                )
            ),
            collapsible = true,
            initiallyExpanded = true,
            columns = 2,
            label = SandboxTr.Form.basicInfo()
        ),
        "elements" to RepeatableControl(
            rowControls = mapOf(
                "element_name" to StringControl(
                    SandboxTr.Form.elementName(),
                    required = true
                ),
                "element_value" to IntegerControl(
                    SandboxTr.Form.elementValue(),
                    validationOptions = IntegerValidation(min = 0)
                )
            ),
            rowOrder = listOf("element_name", "element_value"),
            label = SandboxTr.Form.elements(),
            validationOptions = RepeatableValidation(
                minItems = 0,
                maxItems = 10,
                uniqueFields = listOf("element_name")
            )
        ),
        "nested_repeatable" to RepeatableControl(
            rowControls = mapOf(
                "inner_repeatable" to RepeatableControl(
                    rowControls = mapOf(
                        "inner_string" to StringControl(
                            SandboxTr.Form.innerString(),
                            dependencies = mapOf(
                                "visible" to ControlDependency(
                                    controlPath = "../inner_boolean",
                                    value = true,
                                    dependencyType = DependencyType.Visible,
                                    comparisonType = ComparisonType.Equals
                                )
                            )
                        )
                    ),
                    rowOrder = listOf("inner_string"),
                    label = SandboxTr.Form.innerRepeatable()
                ),
                "inner_boolean" to CheckboxControl(
                    SandboxTr.Form.innerBoolean()
                )
            ),
            rowOrder = listOf("inner_repeatable", "inner_boolean"),
            label = SandboxTr.Form.nestedRepeatable()
        ),
        "save_button" to ButtonControl(
            text = Tr.Action.save(),
            actions = listOf(
                ControlAction {
                    if (trigger.triggerAction("save", true)) AppRouter.goBack()
                }
            ),
            buttonType = ButtonType.Filled
        ),
        "cancel_button" to ButtonControl(
            text = Tr.Action.cancel(),
            actions = listOf(
                ControlAction {
                    AppRouter.goBack()
                }
            ),
            buttonType = ButtonType.Outlined
        ),
        "clear_elements_button" to ButtonControl(
            text = "Wyczyść wartości elementów",
            actions = listOf(
                ControlAction {
                    updateControls<Int>("elements[*]/element_value", null)
                }
            ),
            buttonType = ButtonType.Text
        )
    )

    override fun defineContentOrder(): List<String> = listOf(
        "basic_info",
        "elements",
        "nested_repeatable"
    )

    override fun defineActionBarOrder(): List<String> = listOf(
        "clear_elements_button",
        "cancel_button",
        "save_button"
    )
}
