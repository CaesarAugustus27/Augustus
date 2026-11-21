package io.github.octaviusframework.form.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.octavius.dialog.GlobalDialogManager
import io.github.octaviusframework.form.control.base.ComparisonType
import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.control.base.DependencyType
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.IntegerValidation
import io.github.octaviusframework.form.control.base.RepeatableValidation
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.control.type.container.SectionControl
import io.github.octaviusframework.form.control.type.datetime.DateControl
import io.github.octaviusframework.form.control.type.number.DoubleControl
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.primitive.StringControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue
import io.github.octaviusframework.form.control.type.repeatable.RepeatableRow
import java.math.BigDecimal

class FormHandlerTest {

    private fun handler(
        controls: Map<String, Control<*>>,
        initData: Map<String, Any?>? = emptyMap(),
        validator: FormValidator = FormValidator(),
        save: (FormResultData) -> Boolean
    ) = FormHandler(
        formSchemaBuilder = object : FormSchemaBuilder() {
            override fun defineControls() = controls
            override fun defineContentOrder() = controls.keys.toList()
            override fun defineActionBarOrder() = emptyList<String>()
        },
        formDataManager = object : FormDataManager() {
            override fun initData(payload: Map<String, Any?>) = initData
            override fun definedFormActions() = mapOf("save" to save)
        },
        formValidator = validator,
        // Unconfined: loading the data finishes inside the constructor, without waiting.
        handlerScope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `retry after failed save still reports row deleted from the database`() {
        val deletedRowsSeen = mutableListOf<Int>()
        val handler = handler(
            controls = mapOf(
                "items" to RepeatableControl(mapOf("name" to StringControl(null)), listOf("name"), null)
            ),
            initData = mapOf("items" to listOf(mapOf("name" to "from db")))
        ) { formData ->
            deletedRowsSeen += formData.getCurrentAs<RepeatableResultValue>("items").deletedRows.size
            false // the save failed, the user will try again
        }

        @Suppress("UNCHECKED_CAST")
        val items = handler.getControlState("items") as ControlState<List<RepeatableRow>>
        items.value.value = emptyList()

        val first = runBlocking { handler.triggerAction("save", validates = false) }
        val second = runBlocking { handler.triggerAction("save", validates = false) }

        assertThat(first).isFalse()
        assertThat(second).isFalse()
        assertThat(deletedRowsSeen).containsExactly(1, 1)
    }

    @Test
    fun `exception in action releases the overlay`() {
        val handler = handler(controls = mapOf("name" to StringControl(null))) { error("action failed") }

        assertThatThrownBy { runBlocking { handler.triggerAction("save", validates = false) } }
            .hasMessage("action failed")
        assertThat(handler.actionTriggered.value).isFalse()
    }

    @Test
    fun `nested repeatable validates fields of its rows`() {
        var saved = false
        val inner = RepeatableControl(
            rowControls = mapOf("text" to StringControl(null, required = true)),
            rowOrder = listOf("text"),
            label = null,
            validationOptions = RepeatableValidation(minItems = 1)
        )
        // Both levels get one empty row each from minItems when loading.
        val handler = handler(
            controls = mapOf(
                "outer" to RepeatableControl(
                    rowControls = mapOf("inner" to inner),
                    rowOrder = listOf("inner"),
                    label = null,
                    validationOptions = RepeatableValidation(minItems = 1)
                )
            )
        ) { saved = true; true }

        val result = runBlocking { handler.triggerAction("save", validates = true) }

        assertThat(result).isFalse()
        assertThat(saved).isFalse()
        assertThat(handler.errorManager.fieldErrors.keys).singleElement()
            .satisfies({ assertThat(it).matches("outer\\[.+]/inner\\[.+]/text") })
    }

    // Section visible only when the "show" control has the value "yes".
    private fun sectionShownBy(vararg controls: Pair<String, Control<*>>) = SectionControl(
        controls = mapOf(*controls),
        label = "section",
        dependencies = mapOf(
            "visible" to ControlDependency(
                controlPath = "show",
                value = "yes",
                dependencyType = DependencyType.Visible,
                comparisonType = ComparisonType.Equals
            )
        )
    )

    @Test
    fun `child of hidden section does not pass its value to save`() {
        var savedName: Any? = "not saved"
        var initialName: Any? = null
        val handler = handler(
            controls = mapOf(
                "show" to StringControl(null),
                "section" to sectionShownBy("name" to StringControl(null))
            ),
            initData = mapOf("show" to "no", "section/name" to "from db")
        ) { formData ->
            savedName = formData.getCurrent("section/name")
            initialName = formData.getInitial("section/name")
            true
        }

        runBlocking { handler.triggerAction("save", validates = false) }

        assertThat(savedName).isNull()
        assertThat(initialName).isEqualTo("from db")
    }

    @Test
    fun `required child of section is validated`() {
        val handler = handler(
            controls = mapOf(
                "section" to SectionControl(controls = mapOf("name" to StringControl(null, required = true)), label = "section")
            )
        ) { true }

        val result = runBlocking { handler.triggerAction("save", validates = true) }

        assertThat(result).isFalse()
        assertThat(handler.errorManager.fieldErrors.keys).containsExactly("section/name")
    }

    @Test
    fun `same name in two sections keeps two values`() {
        var saved: List<Any?> = emptyList()
        val handler = handler(
            controls = mapOf(
                "first" to SectionControl(controls = mapOf("name" to StringControl(null)), label = "one"),
                "second" to SectionControl(controls = mapOf("name" to StringControl(null)), label = "two")
            ),
            initData = mapOf("first/name" to "one", "second/name" to "two")
        ) { formData -> saved = listOf(formData.getCurrent("first/name"), formData.getCurrent("second/name")); true }

        runBlocking { handler.triggerAction("save", validates = false) }

        assertThat(saved).containsExactly("one", "two")
    }

    @Test
    fun `dependency on a control that does not exist fails loudly`() {
        val handler = handler(
            controls = mapOf(
                "name" to StringControl(
                    null,
                    dependencies = mapOf(
                        "visible" to ControlDependency(
                            controlPath = "missing",
                            value = true,
                            dependencyType = DependencyType.Visible,
                            comparisonType = ComparisonType.Equals
                        )
                    )
                )
            )
        ) { true }

        assertThatThrownBy { runBlocking { handler.triggerAction("save", validates = true) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'missing'")
    }

    @Test
    fun `required child of section nested in hidden section is not validated`() {
        var saved = false
        val handler = handler(
            controls = mapOf(
                "show" to StringControl(null),
                "outer" to sectionShownBy(
                    "inner" to SectionControl(controls = mapOf("name" to StringControl(null, required = true)), label = "inner")
                )
            ),
            initData = mapOf("show" to "no")
        ) { saved = true; true }

        val result = runBlocking { handler.triggerAction("save", validates = true) }

        assertThat(result).isTrue()
        assertThat(saved).isTrue()
    }

    @Test
    fun `section inside repeatable row keeps its children in the row`() {
        var modifiedRows: List<FormResultData> = emptyList()
        val handler = handler(
            controls = mapOf(
                "items" to RepeatableControl(
                    rowControls = mapOf(
                        "details" to SectionControl(controls = mapOf("name" to StringControl(null)), label = "details")
                    ),
                    rowOrder = listOf("details"),
                    label = null
                )
            ),
            initData = mapOf("items" to listOf(mapOf("details/name" to "from db")))
        ) { formData -> modifiedRows = formData.getCurrentAs<RepeatableResultValue>("items").modifiedRows; true }

        @Suppress("UNCHECKED_CAST")
        val rowId = (handler.getControlState("items") as ControlState<List<RepeatableRow>>).value.value!!.single().id
        @Suppress("UNCHECKED_CAST")
        val name = handler.getControlState("items[$rowId]/details/name") as ControlState<String>
        assertThat(name.value.value).isEqualTo("from db")
        name.value.value = "changed"

        runBlocking { handler.triggerAction("save", validates = false) }

        assertThat(modifiedRows).singleElement()
            .satisfies({ assertThat(it.getCurrent("details/name")).isEqualTo("changed") })
    }

    @Test
    fun `field error under a name without a control fails loudly`() {
        val validator = object : FormValidator() {
            override fun validateBusinessRules(formResultData: FormResultData): Boolean {
                errorManager.setFieldErrors("titlePl", listOf("title exists"))
                return false
            }
        }
        val handler = handler(controls = mapOf("title_pl" to StringControl(null)), validator = validator) { true }

        assertThatThrownBy { runBlocking { handler.triggerAction("save", validates = true) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("titlePl")
    }

    // Loads the form and returns the load stage together with the text of the dialog that popped up meanwhile (null - none).
    private fun loadWithDialog(handler: () -> FormHandler): Pair<FormLoadState, String?> {
        GlobalDialogManager.dismiss()
        val loadState = handler().loadState.value
        return loadState to GlobalDialogManager.dialogConfig.value?.text
    }

    @Test
    fun `value of wrong type fails the load with the control path`() {
        val (loadState, dialog) = loadWithDialog {
            handler(
                controls = mapOf("play" to SectionControl(controls = mapOf("hours" to DoubleControl(null)), label = "time")),
                // numeric comes from the database as BigDecimal, not Double
                initData = mapOf("play/hours" to BigDecimal("12.50"))
            ) { true }
        }

        assertThat(loadState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).contains("InitDataException", "'play/hours'", "BigDecimal", "DoubleControl")
    }

    @Test
    fun `field of repeatable row with wrong type fails the load with its schema path`() {
        val (loadState, dialog) = loadWithDialog {
            handler(
                controls = mapOf("items" to RepeatableControl(mapOf("count" to IntegerControl(null)), listOf("count"), null)),
                initData = mapOf("items" to listOf(mapOf("count" to 5L)))
            ) { true }
        }

        assertThat(loadState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).contains("'items/count'", "Long")
    }

    @Test
    fun `repeatable given something other than rows fails the load`() {
        val (loadState, dialog) = loadWithDialog {
            handler(
                controls = mapOf("items" to RepeatableControl(mapOf("name" to StringControl(null)), listOf("name"), null)),
                initData = mapOf("items" to "not rows")
            ) { true }
        }

        assertThat(loadState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).contains("'items'")
    }

    @Test
    fun `key without a control in a repeatable row fails the load`() {
        val (loadState, dialog) = loadWithDialog {
            handler(
                controls = mapOf("items" to RepeatableControl(mapOf("name" to StringControl(null)), listOf("name"), null)),
                initData = mapOf("items" to listOf(mapOf("nmae" to "typo")))
            ) { true }
        }

        assertThat(loadState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).contains("'items/nmae'")
    }

    @Test
    fun `date control accepts only the type of its adapter`() {
        val controls = mapOf("date" to DateControl(null))

        val (dateState, _) = loadWithDialog { handler(controls, mapOf("date" to LocalDate(2026, 10, 24))) { true } }
        val (dateTimeState, dialog) = loadWithDialog {
            handler(controls, mapOf("date" to LocalDateTime(2026, 10, 24, 12, 0))) { true }
        }

        assertThat(dateState).isEqualTo(FormLoadState.Loaded)
        assertThat(dateTimeState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).contains("'date'", "LocalDateTime")
    }

    @Test
    fun `null from initData fails the load without a second dialog`() {
        // null is a failure the data manager has already shown - the engine adds no unhandled-error dialog.
        val (loadState, dialog) = loadWithDialog {
            handler(controls = mapOf("name" to StringControl(null)), initData = null) { true }
        }

        assertThat(loadState).isEqualTo(FormLoadState.Failed)
        assertThat(dialog).isNull()
    }

    @Test
    fun `type-specific validator checks the loaded value`() {
        val handler = handler(
            controls = mapOf("count" to IntegerControl(null, validationOptions = IntegerValidation(min = 1))),
            initData = mapOf("count" to 0)
        ) { true }

        val result = runBlocking { handler.triggerAction("save", validates = true) }

        assertThat(result).isFalse()
        assertThat(handler.errorManager.getFieldErrors("count")).isNotEmpty()
    }
}
