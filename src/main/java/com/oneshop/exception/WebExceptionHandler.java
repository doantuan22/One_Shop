package com.oneshop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Friendly error page for the Thymeleaf controllers. Anything not handled here falls through to
 * the Spring Boot {@code /error} page ({@code templates/error.html}).
 */
@ControllerAdvice(basePackages = {"com.oneshop.controller.client", "com.oneshop.controller.staff",
        "com.oneshop.controller.admin", "com.oneshop.controller.web"})
public class WebExceptionHandler {

    /**
     * Rendered inside the normal request, so SiteMesh wraps it in the layout of the area (client, staff or admin).
     * {@code templates/error.html} is a complete page of its own and is only for the container's error dispatch.
     */
    private static final String VIEW = "error-message";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex) {
        return errorView(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ModelAndView handleBadRequest(BadRequestException ex) {
        return errorView(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ModelAndView errorView(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(VIEW);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("message", message);
        return mav;
    }
}
