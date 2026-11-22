package org.octavius.modules.sandbox.form.nested

import io.github.octaviusframework.form.component.FormSchemaBuilder
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.type.button.ButtonControl
import io.github.octaviusframework.form.control.type.button.ButtonType
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.number.IntegerValidation
import io.github.octaviusframework.form.control.type.primitive.CheckboxControl
import io.github.octaviusframework.form.control.type.primitive.StringControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableControl
import org.octavius.localization.Tr
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.navigation.AppRouter

/**
 * Trzy poziomy list powtarzalnych: kohorty, w nich centurie, w nich kontubernia. Pole `soldiers` z trzeciego
 * poziomu zależy od `show_strength` z pierwszego (`../../`). Przycisk w wierszu kohorty ustawia `soldiers` tylko
 * w niej (wildcard względny), a przycisk na pasku akcji we wszystkich kohortach (wildcard bezwzględny).
 */
class SandboxNestedFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "legion" to StringControl(
            SandboxTr.NestedForm.legion(),
            required = true
        ),
        "cohorts" to RepeatableControl(
            rowControls = mapOf(
                "cohort_name" to StringControl(
                    SandboxTr.NestedForm.cohortName(),
                    required = true
                ),
                "show_strength" to CheckboxControl(
                    SandboxTr.NestedForm.showStrength()
                ),
                "full_strength" to ButtonControl(
                    text = SandboxTr.NestedForm.fullStrength(),
                    actions = listOf(
                        ControlAction {
                            updateControls("./centuries[*]/contubernia[*]/soldiers", 8)
                        }
                    ),
                    buttonType = ButtonType.Text
                ),
                "centuries" to RepeatableControl(
                    rowControls = mapOf(
                        "centurion" to StringControl(
                            SandboxTr.NestedForm.centurion(),
                            required = true
                        ),
                        "contubernia" to RepeatableControl(
                            rowControls = mapOf(
                                "contubernium_name" to StringControl(
                                    SandboxTr.NestedForm.contuberniumName(),
                                    required = true
                                ),
                                "soldiers" to IntegerControl(
                                    SandboxTr.NestedForm.soldiers(),
                                    validationOptions = IntegerValidation(min = 0, max = 8),
                                    dependencies = mapOf(
                                        "visible" to ControlDependency(
                                            controlPath = "../../show_strength",
                                            value = true,
                                            dependencyType = DependencyType.Visible,
                                            comparisonType = ComparisonType.Equals
                                        )
                                    )
                                )
                            ),
                            rowOrder = listOf("contubernium_name", "soldiers"),
                            label = SandboxTr.NestedForm.contubernia()
                        )
                    ),
                    rowOrder = listOf("centurion", "contubernia"),
                    label = SandboxTr.NestedForm.centuries()
                )
            ),
            rowOrder = listOf("cohort_name", "show_strength", "full_strength", "centuries"),
            label = SandboxTr.NestedForm.cohorts()
        ),
        "save_button" to ButtonControl(
            text = Tr.Action.save(),
            actions = listOf(
                ControlAction {
                    trigger.triggerAction("save", true)
                }
            ),
            buttonType = ButtonType.Filled
        ),
        "reset_legion_button" to ButtonControl(
            text = SandboxTr.NestedForm.resetLegion(),
            actions = listOf(
                ControlAction {
                    updateControls("cohorts[*]/centuries[*]/contubernia[*]/soldiers", 0)
                }
            ),
            buttonType = ButtonType.Text
        ),
        "cancel_button" to ButtonControl(
            text = Tr.Action.cancel(),
            actions = listOf(
                ControlAction {
                    AppRouter.goBack()
                }
            ),
            buttonType = ButtonType.Outlined
        )
    )

    override fun defineContentOrder(): List<String> = listOf(
        "legion",
        "cohorts"
    )

    override fun defineActionBarOrder(): List<String> = listOf(
        "reset_legion_button",
        "cancel_button",
        "save_button"
    )
}
