package com.saaketh.budget.account;

import com.saaketh.budget.common.NotFoundException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> list(Long userId) {
        return accountRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public Account create(Long userId, String name) {
        String trimmed = name.trim();
        if (accountRepository.existsByUserIdAndName(userId, trimmed)) {
            throw new AccountNameTakenException();
        }
        try {
            return accountRepository.saveAndFlush(new Account(userId, trimmed));
        } catch (DataIntegrityViolationException e) {
            // Same race as duplicate emails: the unique constraint is the real guarantee.
            throw new AccountNameTakenException();
        }
    }

    /**
     * Loads an account only if it belongs to this user. Someone else's account looks exactly like a
     * missing one (404), so ids can't be probed to discover other users' data.
     */
    @Transactional(readOnly = true)
    public Account getOwned(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
