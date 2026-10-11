package com.saaketh.budget.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing by counting FAILED logins in a sliding window:
 * <ul>
 *   <li>per email: someone guessing one person's password;</li>
 *   <li>per IP address: one machine trying many accounts.</li>
 * </ul>
 * Successful logins don't count, and a success clears that email's failures.
 *
 * <p>Kept in memory: fine for one server (our Render instance); with several servers the counts
 * would need to live somewhere shared, like Redis or Postgres. A restart forgets the counts.
 *
 * <p>Trade-off: anyone can lock an email out for a while by failing on purpose. That's why the
 * window is short (minutes, not a permanent lock) and the message says when to try again.
 */
@Component
public class LoginRateLimiter {

    /** Thrown when a key is over its limit; retryAfter is how long until the oldest failure expires. */
    public static class TooManyAttemptsException extends RuntimeException {

        private final Duration retryAfter;

        TooManyAttemptsException(Duration retryAfter) {
            super("Too many failed login attempts. Try again in " + minutes(retryAfter) + ".");
            this.retryAfter = retryAfter;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }

        private static String minutes(Duration d) {
            long m = Math.max(1, (d.toSeconds() + 59) / 60);
            return m == 1 ? "1 minute" : m + " minutes";
        }
    }

    /** Above this many tracked keys, expired entries are swept so memory can't grow without bound. */
    static final int SWEEP_THRESHOLD = 10_000;
    /** A sweep scans every key, so do it at most this often (not on every failure during a flood). */
    static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final int maxPerEmail;
    private final int maxPerIp;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.EPOCH;

    @Autowired
    public LoginRateLimiter(@Value("${app.login-limit.per-email:5}") int maxPerEmail,
            @Value("${app.login-limit.per-ip:30}") int maxPerIp,
            @Value("${app.login-limit.window:15m}") Duration window) {
        this(maxPerEmail, maxPerIp, window, Clock.systemUTC());
    }

    LoginRateLimiter(int maxPerEmail, int maxPerIp, Duration window, Clock clock) {
        this.maxPerEmail = maxPerEmail;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
    }

    /** Call before checking the password; throws if this email or IP has failed too often. */
    public void check(String email, String ip) {
        Instant now = clock.instant();
        checkKey("email:" + email, maxPerEmail, now);
        checkKey("ip:" + ip, maxPerIp, now);
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (failures.size() > SWEEP_THRESHOLD && now.isAfter(lastSweep.plus(SWEEP_INTERVAL))) {
            lastSweep = now;
            failures.keySet().forEach(key -> prune(key, now));
        }
        add("email:" + email, now);
        add("ip:" + ip, now);
    }

    /** The right password: forget this email's failures (the IP's stay, in case it's guessing many). */
    public void recordSuccess(String email) {
        failures.remove("email:" + email);
    }

    // ConcurrentHashMap.compute* runs atomically per key, so each deque is only touched by one
    // thread at a time and two simultaneous logins can't lose a failure.

    private void checkKey(String key, int max, Instant now) {
        Duration[] retryAfter = new Duration[1];
        failures.computeIfPresent(key, (k, times) -> {
            dropExpired(times, now);
            if (times.size() >= max) {
                retryAfter[0] = Duration.between(now, times.peekFirst().plus(window));
            }
            return times.isEmpty() ? null : times;
        });
        if (retryAfter[0] != null) {
            throw new TooManyAttemptsException(retryAfter[0]);
        }
    }

    private void add(String key, Instant now) {
        failures.compute(key, (k, times) -> {
            Deque<Instant> result = times == null ? new ArrayDeque<>() : times;
            dropExpired(result, now);
            result.addLast(now);
            return result;
        });
    }

    /** Removes the key once all its failures are older than the window. */
    private void prune(String key, Instant now) {
        failures.computeIfPresent(key, (k, times) -> {
            dropExpired(times, now);
            return times.isEmpty() ? null : times;
        });
    }

    private void dropExpired(Deque<Instant> times, Instant now) {
        while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
            times.removeFirst();
        }
    }
}
