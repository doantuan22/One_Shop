package com.oneshop.exception;

import com.oneshop.dto.request.BrandRequest;
import com.oneshop.dto.request.CategoryRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Friendly error page for the Thymeleaf controllers. Anything not handled here falls through to
 * the Spring Boot {@code /error} page ({@code templates/error.html}).
 */
@ControllerAdvice
public class WebExceptionHandler {

    private static final String VIEW = "error-message";

    private static final Pattern PRODUCT_URI = Pattern.compile("^/admin/products/(\\d+)(?:/.*)?$");
    private static final Pattern BRAND_URI = Pattern.compile("^/admin/brands/(\\d+)(?:/.*)?$");
    private static final Pattern CATEGORY_URI = Pattern.compile("^/admin/categories/(\\d+)(?:/.*)?$");

    @Value("${spring.servlet.multipart.max-file-size:5MB}")
    private String maxFileSize;

    @ModelAttribute("maxFileSize")
    public String maxFileSize() {
        return maxFileSize;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(HttpServletRequest request, MaxUploadSizeExceededException ex) {
        String uri = request.getRequestURI();
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        String sizeMsg = "Ảnh vượt quá " + maxFileSize + ". Vui lòng chọn tệp nhỏ hơn.";
        if (flashMap != null) {
            flashMap.put("error", "image-size");
            flashMap.put("errorMessage", sizeMsg);
        }

        String name = request.getParameter("name");
        String description = request.getParameter("description");

        Matcher productMatcher = PRODUCT_URI.matcher(uri);
        if (productMatcher.matches()) {
            String id = productMatcher.group(1);
            return "redirect:/admin/products/" + id + "/edit?error=image-size";
        }

        Matcher brandMatcher = BRAND_URI.matcher(uri);
        if (brandMatcher.matches()) {
            String id = brandMatcher.group(1);
            if (flashMap != null && StringUtils.hasText(name)) {
                BrandRequest form = new BrandRequest();
                form.setName(name);
                form.setDescription(description);
                flashMap.put("form", form);
            }
            return "redirect:/admin/brands/" + id + "/edit?error=image-size";
        }

        Matcher categoryMatcher = CATEGORY_URI.matcher(uri);
        if (categoryMatcher.matches()) {
            String id = categoryMatcher.group(1);
            if (flashMap != null && StringUtils.hasText(name)) {
                CategoryRequest form = new CategoryRequest();
                form.setName(name);
                form.setDescription(description);
                flashMap.put("form", form);
            }
            return "redirect:/admin/categories/" + id + "/edit?error=image-size";
        }

        if (uri.startsWith("/admin/categories")) {
            if (flashMap != null && StringUtils.hasText(name)) {
                CategoryRequest form = new CategoryRequest();
                form.setName(name);
                form.setDescription(description);
                flashMap.put("form", form);
            }
            return "redirect:/admin/categories?error=image-size";
        }

        if (uri.startsWith("/admin/brands")) {
            if (flashMap != null && StringUtils.hasText(name)) {
                BrandRequest form = new BrandRequest();
                form.setName(name);
                form.setDescription(description);
                flashMap.put("form", form);
            }
            return "redirect:/admin/brands?error=image-size";
        }

        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(referer) && (referer.startsWith("/") || referer.contains("/admin/"))) {
            return "redirect:" + referer + (referer.contains("?") ? "&" : "?") + "error=image-size";
        }

        return "redirect:/admin/products?error=image-size";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex) {
        return errorView(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ModelAndView handlePaymentConflict(PaymentConflictException ex) {
        ModelAndView mav = errorView(HttpStatus.CONFLICT, ex.getMessage());
        mav.addObject("code", ex.getCode());
        return mav;
    }

    @ExceptionHandler(FulfillmentConflictException.class)
    public ModelAndView handleFulfillmentConflict(FulfillmentConflictException ex) {
        ModelAndView mav = errorView(HttpStatus.CONFLICT, ex.getMessage());
        mav.addObject("code", ex.getCode());
        return mav;
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
