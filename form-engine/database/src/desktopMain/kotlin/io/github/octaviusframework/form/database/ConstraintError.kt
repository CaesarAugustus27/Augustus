package io.github.octaviusframework.form.database

import io.github.octaviusframework.form.component.ErrorManager

/** The error a form shows when a save violates a constraint from `DatabaseFormDataManager.constraintErrors`. */
sealed class ConstraintError {

    internal abstract fun report(errorManager: ErrorManager)

    /** An error on the control under [path], e.g. a unique name taken by another record. */
    class Field(private val path: String, private val message: String) : ConstraintError() {
        override fun report(errorManager: ErrorManager) {
            errorManager.setFieldErrors(path, listOf(message))
        }
    }

    /** An error of the whole form, e.g. a record that rows elsewhere still reference. */
    class Global(private val message: String) : ConstraintError() {
        override fun report(errorManager: ErrorManager) {
            errorManager.addGlobalError(message)
        }
    }
}
