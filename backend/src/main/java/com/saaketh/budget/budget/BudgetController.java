package com.saaketh.budget.budget;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.budget.BudgetPlanService.BudgetPlan;
import com.saaketh.budget.budget.BudgetService.BudgetOverview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    /** Money arrives as an exact decimal (e.g. "200.00"), never as floating point. */
    public record BudgetRequest(
            @NotNull(message = "Enter a monthly amount")
            @DecimalMin(value = "0.01", message = "The amount must be more than $0")
            @DecimalMax(value = "1000000", message = "The amount must be at most $1,000,000")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places") BigDecimal monthlyLimit) {
    }

    /** "Use this budget" for a month, optionally around the person's own monthly total. */
    public record ApplyPlanRequest(@NotNull(message = "Choose a month") String month, BigDecimal target) {
    }

    private final BudgetService budgetService;
    private final BudgetPlanService budgetPlanService;

    public BudgetController(BudgetService budgetService, BudgetPlanService budgetPlanService) {
        this.budgetService = budgetService;
        this.budgetPlanService = budgetPlanService;
    }

    /**
     * GET /api/budgets/plan?month=2026-10 (&target=600): a budget suggested from your own history,
     * and how this month compares with usual. Nothing is saved.
     */
    @GetMapping("/plan")
    public BudgetPlan plan(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(required = false) BigDecimal target) {
        return budgetPlanService.plan(user.getId(), month, target);
    }

    /** Save the suggested budget (only categories without a budget yet), then return the overview. */
    @PostMapping("/plan")
    public BudgetOverview applyPlan(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApplyPlanRequest request) {
        budgetPlanService.apply(user.getId(), request.month(), request.target());
        return budgetService.overview(user.getId(), request.month());
    }

    /** GET /api/budgets?month=2026-09: every budget with what's been spent that month. */
    @GetMapping
    public BudgetOverview overview(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month) {
        return budgetService.overview(user.getId(), month);
    }

    /** Set (or change) the monthly limit for a category. PUT because doing it twice has the same effect. */
    @PutMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long categoryId,
            @Valid @RequestBody BudgetRequest request) {
        budgetService.set(user.getId(), categoryId, request.monthlyLimit());
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long categoryId) {
        budgetService.delete(user.getId(), categoryId);
    }
}
