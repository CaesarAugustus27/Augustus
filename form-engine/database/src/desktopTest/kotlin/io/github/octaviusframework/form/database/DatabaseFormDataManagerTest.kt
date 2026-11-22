package io.github.octaviusframework.form.database

import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.driver.exception.ConstraintViolationException
import io.github.octaviusframework.driver.exception.ConstraintViolationExceptionReason
import io.github.octaviusframework.driver.message.ServerErrorMessage
import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormResultData
import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.primitive.StringControl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DatabaseFormDataManagerTest {

    private val db = mockk<OctaviusClient>()
    private val plan = TransactionPlan()

    private val manager = object : DatabaseFormDataManager() {
        override fun initData(payload: Map<String, Any?>) = emptyMap<String, Any?>()
        override fun definedFormPlans() = mapOf("save" to { _: FormResultData -> plan })
        override fun constraintErrors() = mapOf(
            "consuls_name_key" to ConstraintError.Field("name", "consul already elected"),
            "edicts_consul_id_fkey" to ConstraintError.Global("consul still has edicts")
        )
    }

    private val handler = FormHandler(
        formSchemaBuilder = object : FormSchemaBuilder() {
            override fun defineControls() = mapOf("name" to StringControl(null))
            override fun defineContentOrder() = listOf("name")
            override fun defineActionBarOrder() = emptyList<String>()
        },
        formDataManager = manager,
        handlerScope = CoroutineScope(Dispatchers.Unconfined)
    )

    @BeforeEach
    fun startDatabase() {
        startKoin { modules(module { single { db } }) }
    }

    @AfterEach
    fun stopDatabase() {
        stopKoin()
    }

    private fun violationOf(constraint: String) = ConstraintViolationException(
        ConstraintViolationExceptionReason.UNIQUE_CONSTRAINT_VIOLATION,
        "23505",
        ServerErrorMessage(
            severity = "ERROR", localizedSeverity = "ERROR", code = "23505", message = "violation", detail = null,
            hint = null, position = null, internalPosition = null, internalQuery = null, where = null,
            schema = null, table = null, column = null, datatype = null, constraint = constraint, file = null,
            line = null, routine = null
        )
    )

    private fun runningPlanThrows(exception: Exception) {
        every { db.executeTransactionPlan(any(), any(), any(), any(), any(), any()) } throws exception
    }

    private fun save() = runBlocking { handler.triggerAction("save", validates = false) }

    @Test
    fun `runs the action's plan and succeeds`() {
        every { db.executeTransactionPlan(any(), any(), any(), any(), any(), any()) } returns mockk()

        assertThat(save()).isTrue()
        verify { db.executeTransactionPlan(plan, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `known constraint becomes the field error it maps to`() {
        runningPlanThrows(violationOf("consuls_name_key"))

        assertThat(save()).isFalse()
        assertThat(manager.errorManager.getFieldErrors("name")).containsExactly("consul already elected")
        assertThat(manager.errorManager.globalErrors.value).isEmpty()
    }

    @Test
    fun `known constraint becomes the global error it maps to`() {
        runningPlanThrows(violationOf("edicts_consul_id_fkey"))

        assertThat(save()).isFalse()
        assertThat(manager.errorManager.globalErrors.value).containsExactly("consul still has edicts")
        assertThat(manager.errorManager.getFieldErrors("name")).isEmpty()
    }

    @Test
    fun `unknown constraint goes on to the error dialog`() {
        val violation = violationOf("consuls_term_check")
        runningPlanThrows(violation)

        assertThatThrownBy { save() }.isSameAs(violation)
    }
}
