package com.oneshop.controller.client;

import com.oneshop.exception.BadRequestException;
import com.oneshop.service.StoreService;
import com.oneshop.web.SelectedStoreCookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Store Finder and the choice of the Store being browsed (Roadmap V2 6.1). Selecting a Store only changes the browsing
 * context: it never touches a cart (BR-16).
 */
@Controller
public class StoreController {

    private static final String DEFAULT_RETURN = "/products";

    private final StoreService storeService;
    private final SelectedStoreCookie selectedStoreCookie;

    public StoreController(StoreService storeService, SelectedStoreCookie selectedStoreCookie) {
        this.storeService = storeService;
        this.selectedStoreCookie = selectedStoreCookie;
    }

    @GetMapping("/stores")
    public String finder(@RequestParam(required = false) String provinceCity,
                         @RequestParam(required = false) String area, Model model) {
        model.addAttribute("stores", storeService.findStores(provinceCity, area));
        model.addAttribute("provinceCities", storeService.getProvinceCities());
        model.addAttribute("areas", storeService.getAreas(provinceCity));
        model.addAttribute("provinceCity", provinceCity);
        model.addAttribute("area", area);
        return "store/list";
    }

    /** Remembers the Store, after the Service has confirmed it exists and is ACTIVE. */
    @PostMapping("/stores/select")
    public String select(@RequestParam(required = false) Long storeId,
                         @RequestParam(required = false) String returnTo, HttpServletResponse response) {
        try {
            selectedStoreCookie.write(response, storeService.requireSelectableStore(storeId).id());
        } catch (BadRequestException ex) {
            return "redirect:/stores?error=unavailable";
        }
        return "redirect:" + localPath(returnTo);
    }

    /** Back to the whole chain. */
    @PostMapping("/stores/clear")
    public String clear(@RequestParam(required = false) String returnTo, HttpServletResponse response) {
        selectedStoreCookie.clear(response);
        return "redirect:" + localPath(returnTo);
    }

    /** Only a path inside this site may be redirected to, never another host. */
    private static String localPath(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")
                || returnTo.contains("\\") || returnTo.contains("://") || returnTo.contains("\n")
                || returnTo.contains("\r")) {
            return DEFAULT_RETURN;
        }
        return returnTo;
    }
}
