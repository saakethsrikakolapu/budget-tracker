package com.saaketh.budget.category;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.category.CategoryRuleService.RuleChange;
import com.saaketh.budget.category.CategoryRuleService.RuleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rules like "description contains POSHMARK -> Shopping". */
@RestController
@RequestMapping("/api/rules")
public class CategoryRuleController {

    public record RuleRequest(
            @NotBlank(message = "Enter the text to match")
            @Size(max = 100, message = "The text to match must be at most 100 characters") String pattern,
            @NotNull(message = "Choose a category") Long categoryId) {
    }

    /** Response for a delete: how many transactions changed category as a result. */
    public record RuleDeleted(int recategorizedCount) {
    }

    private final CategoryRuleService ruleService;

    public CategoryRuleController(CategoryRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** Most specific first, which is the order they're applied in. */
    @GetMapping
    public List<RuleView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ruleService.list(user.getId());
    }

    /** Creates the rule and applies it to existing (non-manual) transactions. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleChange create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RuleRequest request) {
        return ruleService.create(user.getId(), request.pattern(), request.categoryId());
    }

    @PutMapping("/{id}")
    public RuleChange update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody RuleRequest request) {
        return ruleService.update(user.getId(), id, request.pattern(), request.categoryId());
    }

    /** Returns 200 with a body (not 204) so the page can say how many transactions changed. */
    @DeleteMapping("/{id}")
    public RuleDeleted delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return new RuleDeleted(ruleService.delete(user.getId(), id));
    }
}
