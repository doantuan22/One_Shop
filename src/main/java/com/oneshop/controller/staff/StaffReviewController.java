package com.oneshop.controller.staff;

import com.oneshop.exception.BadRequestException;
import com.oneshop.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/**
 * Reviews of the Stores the Staff is assigned to, and their single reply. Which reviews belong to the Staff is decided
 * by the Service from the assignments in the database, never by a request parameter.
 */
@Controller
@RequestMapping("/staff/reviews")
public class StaffReviewController {
    private final ReviewService reviews;

    public StaffReviewController(ReviewService reviews) { this.reviews = reviews; }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviews.getStaffReviews(page));
        return "staff/reviews/index";
    }

    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam(required = false) String reply,
                        @RequestParam(defaultValue = "0") int page) {
        String back = "redirect:/staff/reviews?page=" + Math.max(0, page);
        try {
            reviews.reply(id, reply);
        } catch (BadRequestException ex) {
            return back + "&replyError=invalid";
        }
        return back + "&replied";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) {
        ModelAndView view = new ModelAndView("error-message");
        view.setStatus(HttpStatus.FORBIDDEN);
        view.addObject("status", 403);
        view.addObject("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        view.addObject("message", "Bạn không được phân công xử lý đánh giá của chi nhánh này.");
        return view;
    }
}
