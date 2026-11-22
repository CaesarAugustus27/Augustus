package org.octavius.modules.sandbox.form.nested

import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class SandboxNestedFormScreen {
    companion object {
        fun create(): Screen {
            val formHandler = FormHandler(
                formSchemaBuilder = SandboxNestedFormSchemaBuilder(),
                formDataManager = SandboxNestedFormDataManager()
            )
            return ComponentScreen(SandboxTr.NestedForm.title()) { FormView(formHandler) }
        }
    }
}
