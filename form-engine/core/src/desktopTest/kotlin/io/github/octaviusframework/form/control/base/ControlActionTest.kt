package io.github.octaviusframework.form.control.base

import androidx.compose.runtime.mutableStateOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import io.github.octaviusframework.form.component.ErrorManager
import io.github.octaviusframework.form.component.FormActionTrigger
import io.github.octaviusframework.form.component.FormSchema
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.primitive.StringControl

class ControlActionTest {

    @Test
    fun `updateControls should update all matching controls`() {
        // Arrange
        val formState = mockk<FormState>()
        val formSchema = mockk<FormSchema>()
        val errorManager = mockk<ErrorManager>()
        val trigger = mockk<FormActionTrigger>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        
        val sourceContext = ControlContext(localName = "triggerSource", statePath = "triggerSource")
        
        // displayText seeded on purpose: writing the value has to invalidate it, since it describes the old value.
        val state1 = ControlState(value = mutableStateOf("old1"), displayText = mutableStateOf("text 1"))
        val state2 = ControlState(value = mutableStateOf("old2"), displayText = mutableStateOf("text 2"))
        val stateOther = ControlState(value = mutableStateOf("other"), displayText = mutableStateOf("other text"))

        val allStates = mapOf(
            "items[1]/value" to state1,
            "items[2]/value" to state2,
            "other/value" to stateOther
        )

        every { formState.getAllStates() } returns allStates
        every { formState.getControlState("items[1]/value") } returns state1
        every { formState.getControlState("items[2]/value") } returns state2
        every { formSchema.getControl("items/value") } returns StringControl(null)

        val context = ActionContext<String>(
            sourceValue = "trigger",
            sourceControlContext = sourceContext,
            formState = formState,
            formSchema = formSchema,
            errorManager = errorManager,
            trigger = trigger,
            coroutineScope = scope
        )

        // Act
        context.updateControls<String>("items[*]/value", "new")

        // Assert
        assertThat(state1.value.value).isEqualTo("new")
        assertThat(state2.value.value).isEqualTo("new")
        assertThat(stateOther.value.value).isEqualTo("other")
        
        assertThat(state1.displayText.value).isNull()
        assertThat(state2.displayText.value).isNull()
        assertThat(stateOther.displayText.value).isEqualTo("other text")
    }

    @Test
    fun `readControl should return current value and fail on unknown path`() {
        // Arrange
        val formState = mockk<FormState>()
        val context = ActionContext(
            sourceValue = "trigger",
            sourceControlContext = ControlContext(localName = "triggerSource", statePath = "triggerSource"),
            formState = formState,
            formSchema = mockk<FormSchema>(),
            errorManager = mockk<ErrorManager>(),
            trigger = mockk<FormActionTrigger>(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        every { formState.getControlState("titles") } returns
                ControlState(value = mutableStateOf(listOf("Solo Leveling", "Na Honjaman Level Up")))
        every { formState.getControlState("no_such") } returns null

        // Act & Assert
        assertThat(context.readControl<List<String>>("titles"))
            .containsExactly("Solo Leveling", "Na Honjaman Level Up")
        assertThatThrownBy { context.readControl<String>("no_such") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'no_such'")
    }

    @Test
    fun `update and label on unknown path fail loudly`() {
        val formState = mockk<FormState>()
        val context = ActionContext(
            sourceValue = "trigger",
            sourceControlContext = ControlContext(localName = "triggerSource"),
            formState = formState,
            formSchema = mockk<FormSchema>(),
            errorManager = mockk<ErrorManager>(),
            trigger = mockk<FormActionTrigger>(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        every { formState.getControlState("no_such") } returns null

        assertThatThrownBy { context.updateControl("no_such", "x") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { context.updateControls("no_such", "x") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { context.updateLabel("no_such", "x") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `update with a value the control does not accept fails loudly`() {
        val formState = mockk<FormState>()
        val formSchema = mockk<FormSchema>()
        val state = ControlState(value = mutableStateOf(1))
        every { formState.getControlState("items[1]/count") } returns state
        every { formSchema.getControl("items/count") } returns IntegerControl(null)
        val context = ActionContext(
            sourceValue = "trigger",
            sourceControlContext = ControlContext(localName = "triggerSource"),
            formState = formState,
            formSchema = formSchema,
            errorManager = mockk<ErrorManager>(),
            trigger = mockk<FormActionTrigger>(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        assertThatThrownBy { context.updateControl("/items[1]/count", 5L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'items[1]/count'")
            .hasMessageContaining("IntegerControl")
        assertThat(state.value.value).isEqualTo(1)

        context.updateControl("/items[1]/count", 7)
        assertThat(state.value.value).isEqualTo(7)
    }
}
