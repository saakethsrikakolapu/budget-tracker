package com.saaketh.budget.account;

import com.saaketh.budget.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/accounts")
public class AccountController {

    public record CreateAccountRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name must be at most 100 characters") String name) {
    }

    public record AccountResponse(Long id, String name) {
        static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getName());
        }
    }

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.list(user.getId()).stream().map(AccountResponse::from).toList();
    }

    /** Rename, e.g. "Card" to "Chase Freedom". */
    @PutMapping("/{id}")
    public AccountResponse rename(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accountService.rename(user.getId(), id, request.name()));
    }

    /** Remove the account with everything uploaded to it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        accountService.delete(user.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accountService.create(user.getId(), request.name()));
    }
}
