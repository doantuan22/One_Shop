package com.oneshop.controller.client;

import com.oneshop.service.ImageCreditsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public attribution page for the product pictures (licence obligation, open to guests). */
@Controller
public class ImageCreditsController {

    private final ImageCreditsService credits;

    public ImageCreditsController(ImageCreditsService credits) {
        this.credits = credits;
    }

    @GetMapping("/image-credits")
    public String imageCredits(Model model) {
        model.addAttribute("credits", credits.getCredits());
        return "credits/image-credits";
    }
}
