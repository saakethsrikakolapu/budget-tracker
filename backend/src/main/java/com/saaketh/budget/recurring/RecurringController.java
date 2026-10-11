package com.saaketh.budget.recurring;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.recurring.RecurringService.RecurringOverview;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    /** GET /api/recurring: subscriptions and bills found in the user's transactions. */
    @GetMapping
    public RecurringOverview overview(@AuthenticationPrincipal AuthenticatedUser user) {
        return recurringService.overview(user.getId());
    }

    /** PUT /api/recurring/hidden/NETFLIX%20COM: "not a subscription". PUT because repeating it is harmless. */
    @PutMapping("/hidden/{merchantKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String merchantKey) {
        recurringService.hide(user.getId(), merchantKey);
    }

    @DeleteMapping("/hidden/{merchantKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String merchantKey) {
        recurringService.unhide(user.getId(), merchantKey);
    }
}
