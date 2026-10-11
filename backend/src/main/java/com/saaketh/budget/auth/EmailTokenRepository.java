package com.saaketh.budget.auth;

import com.saaketh.budget.auth.EmailToken.Purpose;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, Purpose purpose, Instant after);

    /** Older links stop working when a new one is sent or one is used. */
    @Modifying
    @Query("""
            UPDATE EmailToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL""")
    int expireAll(@Param("userId") Long userId, @Param("purpose") Purpose purpose, @Param("now") Instant now);
}
