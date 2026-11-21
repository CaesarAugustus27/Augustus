package org.octavius.modules.asian.form

import io.github.octaviusframework.form.component.FormValidator
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.base.getInitial
import org.octavius.modules.asian.localization.AsianMediaTr

class AsianMediaValidator: FormValidator() {
    override fun validateBusinessRules(formResultData: FormResultData): Boolean {
        return validateTitleDuplication(formResultData)
    }

    private fun validateTitleDuplication(formResultData: FormResultData): Boolean {
        val titles = formResultData.getCurrentAs<List<String>>("title_info/titles")
        val hasDuplicates = titles.size != titles.toSet().size

        if (hasDuplicates) {
            errorManager.addFieldError("title_info/titles", AsianMediaTr.Form.duplicateTitles())
        }

        return !hasDuplicates
    }

    /**
     * Nie pozwala dodać nowego rekordu z tytułem, który już jest w bazie. Zapisanego rekordu to nie dotyczy, bo
     * różne dzieła bywają tak samo zatytułowane.
     */
    private fun validateNewTitlesAgainstDatabase(formResultData: FormResultData): Boolean {
        if (formResultData.getInitial("id") != null) return true

        val titles = formResultData.getCurrentAs<List<String>>("title_info/titles")
        if (titles.isEmpty()) return true

        val count = db.select("COUNT(*)").fromSubquery("SELECT UNNEST(titles) AS title FROM titles")
            .where("title = ANY(@titles)").fetchField<Long>("titles" to titles)

        if (count > 0L) {
            errorManager.addGlobalError(AsianMediaTr.Form.titlesAlreadyExist())
            return false
        }
        return true
    }

    override fun defineActionValidations(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> validateNewTitlesAgainstDatabase(formData) }
        )
    }
}
