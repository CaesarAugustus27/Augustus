package io.github.octaviusframework.form.component

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

/**
 * @param isKnownField Whether there is a control at the given path. A field error is keyed by a string, so a
 *   typo (`"titlePl"` instead of `"title_pl"`) would land nowhere: the form would only say it "contains
 *   errors", without pointing at the field. That is why setting an error under an unknown path throws.
 */
class ErrorManager(private val isKnownField: (String) -> Boolean) {
    private val _globalErrors = mutableStateOf<List<String>>(emptyList())
    val globalErrors = _globalErrors

    private val _fieldErrors = mutableStateMapOf<String, List<String>>()
    val fieldErrors = _fieldErrors

    private val _formatErrors = mutableStateMapOf<String, String>()
    val formatErrors = _formatErrors

    fun addGlobalError(error: String) {
        _globalErrors.value = _globalErrors.value + error
    }

    fun addGlobalErrors(errors: List<String>) {
        _globalErrors.value = _globalErrors.value + errors
    }

    fun addFieldError(fieldName: String, error: String) {
        requireKnownField(fieldName)
        val currentErrors = _fieldErrors[fieldName] ?: emptyList()
        _fieldErrors[fieldName] = currentErrors + error
    }

    fun addFieldErrors(fieldName: String, errors: List<String>) {
        requireKnownField(fieldName)
        val currentErrors = _fieldErrors[fieldName] ?: emptyList()
        _fieldErrors[fieldName] = currentErrors + errors
    }

    fun setFieldErrors(fieldName: String, errors: List<String>) {
        requireKnownField(fieldName)
        if (errors.isEmpty()) {
            _fieldErrors.remove(fieldName)
        } else {
            _fieldErrors[fieldName] = errors
        }
    }

    fun clearGlobalErrors() {
        _globalErrors.value = emptyList()
    }

    fun clearFieldErrors() {
        _fieldErrors.clear()
    }

    fun clearFieldErrors(fieldName: String) {
        _fieldErrors.remove(fieldName)
    }

    fun clearAll() {
        clearGlobalErrors()
        clearFieldErrors()
    }

    fun hasErrors(): Boolean {
        return _globalErrors.value.isNotEmpty() || _fieldErrors.isNotEmpty()
    }

    fun hasFieldErrors(): Boolean {
        return _fieldErrors.isNotEmpty()
    }

    fun hasFieldErrors(fieldName: String): Boolean {
        return _fieldErrors[fieldName]?.isNotEmpty() == true
    }

    fun getFieldErrors(fieldName: String): List<String> {
        return _fieldErrors[fieldName] ?: emptyList()
    }

    fun setFormatError(fieldName: String, error: String?) {
        if (error.isNullOrBlank()) {
            _formatErrors.remove(fieldName)
        } else {
            _formatErrors[fieldName] = error
        }
    }

    fun getFormatError(fieldName: String): String? {
        return _formatErrors[fieldName]
    }

    fun hasFormatErrors(): Boolean {
        return _formatErrors.isNotEmpty()
    }

    private fun requireKnownField(fieldName: String) {
        require(isKnownField(fieldName)) { "Field error for '$fieldName', but the form has no such control" }
    }
}