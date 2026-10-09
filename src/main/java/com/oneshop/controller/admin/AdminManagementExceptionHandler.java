package com.oneshop.controller.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Concurrent edits / duplicate email have a safe error with no SQL, hash or submitted password. */
@ControllerAdvice(assignableTypes = {AdminUserController.class, AdminStaffAssignmentController.class, AdminReviewController.class})
public class AdminManagementExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    public ModelAndView conflict(DataAccessException ex) {
        var view = new ModelAndView("error-message"); view.setStatus(HttpStatus.CONFLICT);
        view.addObject("status", 409); view.addObject("error", "Không thể lưu thay đổi");
        view.addObject("message", "Dữ liệu có thể đã thay đổi. Vui lòng tải lại trang và thử lại."); return view;
    }
}
