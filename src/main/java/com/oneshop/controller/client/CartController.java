package com.oneshop.controller.client;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartController {

    /** Skeleton page: cart logic will be added together with the order domain. */
    @GetMapping("/cart")
    public String cart() {
        return "cart/index";
    }
}
