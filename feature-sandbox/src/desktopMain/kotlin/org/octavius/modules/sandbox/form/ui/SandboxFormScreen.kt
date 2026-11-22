package org.octavius.modules.sandbox.form.ui

import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.modules.sandbox.form.SandboxFormDataManager
import org.octavius.modules.sandbox.form.SandboxFormSchemaBuilder
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class SandboxFormScreen {
    companion object {
        fun create(): Screen {
            val title = SandboxTr.Form.newItem()

            val formHandler = FormHandler(
                formSchemaBuilder = SandboxFormSchemaBuilder(),
                formDataManager = SandboxFormDataManager()
            )

            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
