package com.oneshop.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProperty;
import org.sitemesh.webapp.WebAppContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Bridge between SiteMesh and Thymeleaf. SiteMesh forwards to this path after buffering the page
 * and exposes the extracted title/head/body; the decorator template merges them into the layout.
 */
@Controller
public class DecoratorController {

    @RequestMapping("/decorators/main")
    public String main(HttpServletRequest request, Model model) {
        Object content = request.getAttribute(WebAppContext.CONTENT_KEY);
        if (content instanceof Content page) {
            ContentProperty properties = page.getExtractedProperties();
            model.addAttribute("pageTitle", properties.getChild("title").getValue());
            model.addAttribute("pageHead", properties.getChild("head").getValue());
            model.addAttribute("pageBody", properties.getChild("body").getValue());
        }
        return "layouts/main";
    }
}
