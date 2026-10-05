package com.oneshop.controller.admin;

import com.oneshop.exception.BadRequestException;
import org.springframework.validation.BindingResult;

/** Shows a business-rule error of a Service next to the form field it is about. */
final class FormErrors {

    private FormErrors() {
    }

    static void reject(BindingResult bindingResult, BadRequestException ex) {
        if (ex.getField() != null && bindingResult.getTarget() != null
                && bindingResult.getFieldType(ex.getField()) != null) {
            bindingResult.rejectValue(ex.getField(), "invalid", ex.getMessage());
        } else {
            bindingResult.reject("invalid", ex.getMessage());
        }
    }
}
