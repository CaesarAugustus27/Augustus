package org.octavius.feature.books.form.book.ui

import org.octavius.feature.books.form.book.BookFormDataManager
import org.octavius.feature.books.form.book.BookFormSchemaBuilder
import org.octavius.feature.books.localization.BooksTr
import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class BookFormScreen {
    companion object {
        fun create(
            entityId: Int? = null
        ): Screen {
            val title =
                if (entityId == null) BooksTr.Form.newBook() else BooksTr.Form.editBook()

            val formHandler = FormHandler(
                formSchemaBuilder = BookFormSchemaBuilder(),
                formDataManager = BookFormDataManager(),
                payload = mapOf("id" to entityId)
            )

            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
