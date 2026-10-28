package com.oneshop.security;

import com.oneshop.config.LoginThrottleProperties;
import com.oneshop.exception.TooManyLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard for the login endpoints.
 * <ul>
 *   <li><b>Per account</b>: an attempt is counted <em>before</em> the password is checked, so a burst of parallel
 *       guesses cannot slip past the limit. After {@code maxAttempts} in a window further attempts are refused
 *       (even with the right password) for {@code lockMinutes}. A successful login clears the account's counter.</li>
 *   <li><b>Per client IP</b>: wrong logins only, so many customers behind one NAT are not locked out by their own
 *       successful logins. It stops one address from sweeping many accounts.</li>
 * </ul>
 * State lives in this JVM: it resets on restart and is not shared between instances (fine for one app server).
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final int MAX_TRACKED_KEYS = 20_000;
    private static final int MAX_KEY_LENGTH = 254;

    private static final class Entry {
        int count;
        Instant windowStart;
        Instant lockedUntil;
    }

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> addresses = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Counts one attempt for the account and refuses it when a limit is already exceeded.
     *
     * @param clientIp may be null (no address known: only the account limit applies)
     * @throws TooManyLoginAttemptsException when the caller must wait
     */
    public void acquire(String identifier, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        if (clientIp != null) {
            Entry ip = addresses.get(clientIp);
            if (ip != null) {
                synchronized (ip) {
                    refuseWhileLocked(ip, now);
                }
            }
        }
        String key = accountKey(identifier);
        Entry entry = entryFor(accounts, key, now);
        if (entry == null) {
            return; // table full of live entries: do not track (never lock everybody out)
        }
        synchronized (entry) {
            refuseWhileLocked(entry, now);
            if (isExpired(entry, now)) {
                entry.count = 0;
                entry.windowStart = now;
            }
            if (entry.count >= properties.maxAttempts()) {
                entry.lockedUntil = now.plus(Duration.ofMinutes(properties.lockMinutes()));
                log.warn("Login locked for an account after {} attempts", entry.count);
                throw new TooManyLoginAttemptsException(Duration.ofMinutes(properties.lockMinutes()).toSeconds());
            }
            entry.count++;
        }
    }

    /** A wrong password: counts against the client address. */
    public void recordFailure(String clientIp) {
        if (!properties.enabled() || clientIp == null) {
            return;
        }
        Instant now = clock.instant();
        Entry entry = entryFor(addresses, clientIp, now);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (isExpired(entry, now)) {
                entry.count = 0;
                entry.windowStart = now;
            }
            entry.count++;
            if (entry.count >= properties.ipMaxFailures() && entry.lockedUntil == null) {
                entry.lockedUntil = now.plus(Duration.ofMinutes(properties.lockMinutes()));
                log.warn("Login locked for a client address after {} wrong logins", entry.count);
            }
        }
    }

    /** A correct password: the account starts from zero again. */
    public void recordSuccess(String identifier) {
        if (properties.enabled()) {
            accounts.remove(accountKey(identifier));
        }
    }

    private void refuseWhileLocked(Entry entry, Instant now) {
        if (entry.lockedUntil == null) {
            return;
        }
        if (entry.lockedUntil.isAfter(now)) {
            throw new TooManyLoginAttemptsException(Duration.between(now, entry.lockedUntil).toSeconds() + 1);
        }
        entry.lockedUntil = null;
        entry.count = 0;
        entry.windowStart = now;
    }

    private boolean isExpired(Entry entry, Instant now) {
        return entry.windowStart == null
                || !entry.windowStart.plus(Duration.ofMinutes(properties.windowMinutes())).isAfter(now);
    }

    private Entry entryFor(ConcurrentHashMap<String, Entry> table, String key, Instant now) {
        Entry existing = table.get(key);
        if (existing != null) {
            return existing;
        }
        if (table.size() >= MAX_TRACKED_KEYS) {
            table.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Entry v = e.getValue();
                    boolean locked = v.lockedUntil != null && v.lockedUntil.isAfter(now);
                    return !locked && isExpired(v, now);
                }
            });
            if (table.size() >= MAX_TRACKED_KEYS) {
                log.warn("Login throttle table is full; new keys are not tracked");
                return null;
            }
        }
        return table.computeIfAbsent(key, k -> new Entry());
    }

    private static String accountKey(String identifier) {
        String key = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }
}
