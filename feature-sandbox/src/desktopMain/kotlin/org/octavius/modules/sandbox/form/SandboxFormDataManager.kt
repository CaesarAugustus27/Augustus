package org.octavius.modules.sandbox.form

import io.github.octaviusframework.form.FormDataManager
import io.github.octaviusframework.form.FormResultData
import org.octavius.modules.sandbox.domain.SandboxPriority
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.ui.snackbar.SnackbarManager

class SandboxFormDataManager : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?> {
        return mapOf(
            "basic_info/name" to "",
            "basic_info/quantity" to null,
            "basic_info/active" to false,
            "basic_info/priority" to SandboxPriority.Medium,
            "basic_info/start_date" to null,
            "basic_info/tags" to emptyList<String>(),
            "elements" to emptyList<Map<String, Any?>>(),
            "nested_repeatable" to emptyList<Map<String, Any?>>()
        ) + payload
    }

    override fun definedFormActions(): Map<String, (formResultData: FormResultData) -> Boolean> {
        return mapOf(
            "save" to { res ->
                SnackbarManager.showMessage(SandboxTr.Form.savedMessage())
                true
            }
        )
    }
}
