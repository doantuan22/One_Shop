package com.oneshop.controller.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProperty;
import org.sitemesh.webapp.WebAppContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.oneshop.config.SiteMeshConfig.ADMIN_DECORATOR_PATH;
import static com.oneshop.config.SiteMeshConfig.CLIENT_DECORATOR_PATH;
import static com.oneshop.config.SiteMeshConfig.STAFF_DECORATOR_PATH;

/**
 * Bridge between SiteMesh and Thymeleaf. SiteMesh forwards to one of the decorator paths after buffering the page
 * and exposes the extracted title/head/body; the decorator template (client, staff or admin) merges them into its
 * layout. Which decorator applies to which URL is configured in {@code SiteMeshConfig}.
 *
 * <p>The layouts read these optional model attributes, which later phases supply (for example with a
 * {@code @ControllerAdvice}); when they are absent the layouts show their default state:
 * {@code selectedStore} (client header: "Chi nhánh đang chọn", absent = "Toàn chuỗi") and {@code assignedStores}
 * (staff layout: Store(s) of the logged-in Staff).
 */
@Controller
public class DecoratorController {

    @RequestMapping(CLIENT_DECORATOR_PATH)
    public String client(HttpServletRequest request, Model model) {
        return decorate(request, model, "layouts/client");
    }

    @RequestMapping(STAFF_DECORATOR_PATH)
    public String staff(HttpServletRequest request, Model model) {
        return decorate(request, model, "layouts/staff");
    }

    @RequestMapping(ADMIN_DECORATOR_PATH)
    public String admin(HttpServletRequest request, Model model) {
        return decorate(request, model, "layouts/admin");
    }

    private String decorate(HttpServletRequest request, Model model, String layout) {
        Object content = request.getAttribute(WebAppContext.CONTENT_KEY);
        if (content instanceof Content page) {
            ContentProperty properties = page.getExtractedProperties();
            model.addAttribute("pageTitle", properties.getChild("title").getValue());
            model.addAttribute("pageHead", properties.getChild("head").getValue());
            model.addAttribute("pageBody", properties.getChild("body").getValue());
        }
        // the request URI seen here is the decorator's; the page being decorated is the forward origin (menu highlight)
        model.addAttribute("currentPath", request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI));
        return layout;
    }
}
