package io.github.octaviusframework.form.control.type

import io.github.octaviusframework.form.control.type.collection.StringListValidation
import io.github.octaviusframework.form.control.type.number.DoubleValidation
import io.github.octaviusframework.form.control.type.primitive.StringValidation
import io.github.octaviusframework.form.control.type.repeatable.RepeatableRow
import io.github.octaviusframework.form.control.type.repeatable.RepeatableValidation
import io.github.octaviusframework.form.localization.FormTr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidationRulesTest {

    @Test
    fun `step accepts multiples on both sides of zero`() {
        val rules = DoubleValidation(step = 0.1)

        assertThat(rules.errors(0.3)).isEmpty()
        assertThat(rules.errors(-0.3)).isEmpty()
        assertThat(rules.errors(0.35)).containsExactly(FormTr.Validation.multipleOf(0.1))
    }

    @Test
    fun `blank text passes the rules`() {
        assertThat(StringValidation(minLength = 3).errors("  ")).isEmpty()
    }

    @Test
    fun `list item breaking the item rules is named by its position`() {
        val rules = StringListValidation(itemValidation = StringValidation(minLength = 3))

        assertThat(rules.errors(listOf("Gaius", "Ti", "")))
            .containsExactly(FormTr.Validation.itemError(item = 2, message = FormTr.Validation.minLength(3)))
    }

    @Test
    fun `first row repeating the unique fields is reported, rows with an empty one are not compared`() {
        val names = mapOf("a" to "Brutus", "b" to "", "c" to "Cassius", "d" to "", "e" to "Brutus")
        val rows = names.keys.mapIndexed { index, id -> RepeatableRow(id, index) }

        val errors = RepeatableValidation(uniqueFields = listOf("name")).errors(rows) { row, _ -> names[row.id] }

        assertThat(errors).containsExactly(FormTr.Validation.duplicateInRow(5))
    }
}
