package com.saaketh.budget.category;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.category.CategoryService.CategoryWithCount;
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

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    /** Used for both create (POST) and update (PUT). */
    public record CategoryRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 50, message = "Name must be at most 50 characters") String name,
            @NotNull(message = "Choose whether this category counts as spending") Boolean countsAsSpending) {
    }

    public record CategoryResponse(Long id, String name, boolean countsAsSpending, long transactionCount) {

        static CategoryResponse from(Category category, long transactionCount) {
            return new CategoryResponse(category.getId(), category.getName(), category.isCountsAsSpending(),
                    transactionCount);
        }
    }

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return categoryService.list(user.getId()).stream()
                .map((CategoryWithCount c) -> CategoryResponse.from(c.category(), c.transactionCount()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(
                categoryService.create(user.getId(), request.name(), request.countsAsSpending()), 0);
    }

    /** Rename and/or change "counts as spending". */
    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryWithCount updated = categoryService.update(user.getId(), id, request.name(), request.countsAsSpending());
        return CategoryResponse.from(updated.category(), updated.transactionCount());
    }

    /** Its transactions become Uncategorized. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        categoryService.delete(user.getId(), id);
    }
}
