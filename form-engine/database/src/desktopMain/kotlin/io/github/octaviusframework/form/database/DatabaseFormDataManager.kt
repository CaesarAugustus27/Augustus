package io.github.octaviusframework.form.database

import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.driver.exception.ConstraintViolationException
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Data manager of a form whose data lives in PostgreSQL.
 *
 * It loads the record with [loadData]. Its actions return a [TransactionPlan] instead of writing: the manager
 * runs the plan in one transaction and turns a violation of a constraint from [constraintErrors] into the error
 * that constraint maps to.
 */
abstract class DatabaseFormDataManager : FormDataManager(), KoinComponent {

    protected val db: OctaviusClient by inject()

    /**
     * Loads the record with [id] according to the mapping in [block] (the DSL from `FormLoader.kt`).
     *
     * @return Values under control paths, or an empty map when [id] is `null` (new record). `null` when
     *   loading failed (database error, missing record) - a dialog with the reason has already been shown,
     *   and `initData` should pass the `null` on.
     */
    fun loadData(id: Any?, block: DataLoaderBuilder.() -> Unit): Map<String, Any?>? {
        val builder = DataLoaderBuilder(db).apply(block)
        return builder.execute(id)
    }

    /**
     * The form's actions as plans of what to write, under the same keys as [definedFormActions]. An empty plan
     * succeeds without opening a transaction, e.g. deleting a record that was never saved.
     */
    open fun definedFormPlans(): Map<String, (formResultData: FormResultData) -> TransactionPlan> = emptyMap()

    /**
     * Constraints whose violation is an expected failure, by name, with the error the form shows for each.
     * A violation of any other constraint goes to the global error dialog, like any other exception.
     */
    open fun constraintErrors(): Map<String, ConstraintError> = emptyMap()

    /** [definedFormPlans], each run in one transaction. Override it for actions that write no database. */
    override fun definedFormActions(): Map<String, (formResultData: FormResultData) -> Boolean> =
        definedFormPlans().mapValues { (_, plan) -> { formResultData -> execute(plan(formResultData)) } }

    private fun execute(plan: TransactionPlan): Boolean {
        try {
            db.executeTransactionPlan(plan)
        } catch (e: ConstraintViolationException) {
            val error = e.constraint?.let { constraintErrors()[it] } ?: throw e
            error.report(errorManager)
            return false
        }
        return true
    }
}
