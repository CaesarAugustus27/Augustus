package io.github.octaviusframework.form.control.base

import io.github.octaviusframework.form.component.FormResultException
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FormResultDataTest {

    private val results: FormResultData = mapOf(
        "name" to ControlResultData(currentValue = "Solo Leveling", initialValue = "Solo Leveling"),
        "hidden" to ControlResultData(currentValue = null, initialValue = 5),
        "section" to ControlResultData(
            currentValue = mapOf("count" to ControlResultData(currentValue = 3, initialValue = 2)),
            initialValue = mapOf("count" to ControlResultData(currentValue = 3, initialValue = 2))
        ),
        "items" to ControlResultData(
            currentValue = RepeatableResultValue(emptyList(), emptyList(), emptyList(), emptyList()),
            initialValue = null
        )
    )

    @Test
    fun `typed read returns the value`() {
        assertThat(results.getCurrentAs<String>("name")).isEqualTo("Solo Leveling")
        assertThat(results.getCurrentAs<Int>("section/count")).isEqualTo(3)
        assertThat(results.getInitialAs<Int>("section/count")).isEqualTo(2)
        assertThat(results.getCurrentAs<Int?>("hidden")).isNull()
        assertThat(results.getCurrentAs<RepeatableResultValue>("items").addedRows).isEmpty()
    }

    @Test
    fun `value of another type fails with the path`() {
        assertThatThrownBy { results.getCurrentAs<Int>("name") }
            .isInstanceOf(FormResultException::class.java)
            .hasFieldOrPropertyWithValue("path", "name")
            .hasMessageContaining("kotlin.Int")
            .hasMessageContaining("String")
    }

    @Test
    fun `null for a non-null type fails with the path`() {
        assertThatThrownBy { results.getCurrentAs<Int>("hidden") }
            .isInstanceOf(FormResultException::class.java)
            .hasFieldOrPropertyWithValue("path", "hidden")
            .hasMessageContaining("got null")
    }

    @Test
    fun `path without a control fails with the path`() {
        assertThatThrownBy { results.getCurrent("section/missing") }
            .isInstanceOf(FormResultException::class.java)
            .hasFieldOrPropertyWithValue("path", "section/missing")
            .hasMessageContaining("'missing'")
    }

    @Test
    fun `path through a control that is not a section fails with the path`() {
        assertThatThrownBy { results.getCurrent("name/x") }
            .isInstanceOf(FormResultException::class.java)
            .hasMessageContaining("'name' is not a section")
        assertThatThrownBy { results.getCurrent("items/name") }
            .isInstanceOf(FormResultException::class.java)
            .hasMessageContaining("'items' is not a section")
    }
}
