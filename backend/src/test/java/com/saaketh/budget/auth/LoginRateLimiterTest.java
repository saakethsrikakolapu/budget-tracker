package com.saaketh.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saaketh.budget.auth.LoginRateLimiter.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final MovableClock clock = new MovableClock();
    /** 3 failures per email, 5 per IP, within 10 minutes. */
    private final LoginRateLimiter limiter = new LoginRateLimiter(3, 5, Duration.ofMinutes(10), clock);

    @Test
    void blocksAnEmailAfterTooManyFailuresUntilTheOldestExpires() {
        fail("a@x.com", "1.1.1.1");
        clock.advance(Duration.ofMinutes(2));
        fail("a@x.com", "1.1.1.1");
        fail("a@x.com", "1.1.1.1");

        assertThatThrownBy(() -> limiter.check("a@x.com", "2.2.2.2")) // a different IP doesn't help
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("Too many failed login attempts. Try again in 8 minutes.")
                .extracting(e -> ((TooManyAttemptsException) e).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(8));
        assertThatCode(() -> limiter.check("b@x.com", "2.2.2.2")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(8)); // the first failure is now 10 minutes old
        assertThatCode(() -> limiter.check("a@x.com", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void successClearsTheEmailButNotTheIp() {
        fail("a@x.com", "1.1.1.1");
        fail("a@x.com", "1.1.1.1");
        limiter.recordSuccess("a@x.com");
        fail("a@x.com", "1.1.1.1");
        fail("a@x.com", "1.1.1.1");
        assertThatCode(() -> limiter.check("a@x.com", "9.9.9.9")).doesNotThrowAnyException();

        // But the IP has 4 failures; one more trying another account and it's blocked for everyone.
        fail("c@x.com", "1.1.1.1");
        assertThatThrownBy(() -> limiter.check("new@x.com", "1.1.1.1")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void oneIpTryingManyAccountsIsBlocked() {
        for (int i = 0; i < 5; i++) {
            fail("user" + i + "@x.com", "6.6.6.6");
        }
        assertThatThrownBy(() -> limiter.check("someone-else@x.com", "6.6.6.6"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.check("someone-else@x.com", "7.7.7.7")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterIsNeverRoundedDownToZeroMinutes() {
        for (int i = 0; i < 3; i++) {
            fail("a@x.com", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(10).minusSeconds(5));
        assertThatThrownBy(() -> limiter.check("a@x.com", "1.1.1.1"))
                .hasMessageEndingWith("Try again in 1 minute.");
    }

    @Test
    void memoryIsSweptWhenManyKeysPileUp() {
        for (int i = 0; i <= LoginRateLimiter.SWEEP_THRESHOLD; i++) {
            fail("bot" + i + "@x.com", "10.0." + (i / 250) + "." + (i % 250));
        }
        clock.advance(Duration.ofMinutes(11)); // all of those have expired
        fail("last@x.com", "1.1.1.1"); // triggers the sweep
        assertThat(limiter).extracting("failures").asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP).hasSize(2);
    }

    private void fail(String email, String ip) {
        limiter.check(email, ip);
        limiter.recordFailure(email, ip);
    }

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
