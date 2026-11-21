package io.github.octaviusframework.form.control.validator

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator

/**
 * Default implementation of a form control validator.
 *
 * Used for controls that need no validation beyond the standard required/visibility checks implemented in
 * the base class ControlValidator.
 *
 * A "null object pattern" implementation - every control has a validator, even if it does no specific
 * validation.
 *
 * @param T type of the data held by the control
 */
class DefaultValidator<T : Any> : ControlValidator<T>() {
    /**
     * Does no additional validation.
     *
     * All basic validation (required, visibility) is handled by the base class ControlValidator.
     *
     * @param value control value - unused in this implementation
     */
    override fun validateSpecific(controlContext: ControlContext, value: T) {
        // The default implementation does no additional validation
    }
}