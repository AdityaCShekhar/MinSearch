package com.aditya.minsearch.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.aditya.minsearch.auth.domain.TooManyAuthenticationAttemptsException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationRateLimiter {

	private static final int MAX_ATTEMPTS = 10;
	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final Clock clock;
	private final java.util.concurrent.ConcurrentMap<String, Bucket> buckets =
			com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(100_000)
				.expireAfterAccess(Duration.ofMinutes(2)).<String, Bucket>build().asMap();

	public AuthenticationRateLimiter(Clock clock) {
		this.clock = clock;
	}

	public void enforce(String key) {
		Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(clock.instant(), new AtomicInteger()));
		Instant now = clock.instant();
		synchronized (bucket) {
			if (Duration.between(bucket.windowStart(), now).compareTo(WINDOW) >= 0) {
				bucket.windowStart(now);
				bucket.attempts().set(0);
			}
			if (bucket.attempts().incrementAndGet() > MAX_ATTEMPTS) {
				throw new TooManyAuthenticationAttemptsException();
			}
		}
	}

	private static final class Bucket {
		private volatile Instant windowStart;
		private final AtomicInteger attempts;

		private Bucket(Instant windowStart, AtomicInteger attempts) {
			this.windowStart = windowStart;
			this.attempts = attempts;
		}

		Instant windowStart() {
			return windowStart;
		}

		void windowStart(Instant value) {
			windowStart = value;
		}

		AtomicInteger attempts() {
			return attempts;
		}
	}
}
