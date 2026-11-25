package com.sujan.tripitaka.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Caps how many questions reach the model, so a stranger with the link cannot run up the OpenAI bill: a limit per
 * person per hour and per day, and a total per day for everyone. Only model answers count; answers from the cache
 * are free and never limited. Counts are kept in memory (one server), so a restart resets them.
 */
@Component
public class UsageGuard {

    private static final Logger log = LoggerFactory.getLogger(UsageGuard.class);

    /** Thrown when a limit is reached; {@code retryAfterSeconds} is when the person's window reopens. */
    public static class LimitReached extends RuntimeException {
        private final long retryAfterSeconds;
        private final boolean everyone;

        LimitReached(long retryAfterSeconds, boolean everyone) {
            super(everyone ? "daily limit for everyone" : "limit for this person");
            this.retryAfterSeconds = retryAfterSeconds;
            this.everyone = everyone;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        public boolean everyone() {
            return everyone;
        }
    }

    private record Counts(long hour, int inHour, long day, int inDay) {
    }

    private final int perPersonPerHour;
    private final int perPersonPerDay;
    private final int everyonePerDay;
    private final Clock clock;
    private final Map<String, Counts> people = new HashMap<>();
    private long day = -1;
    private int everyoneToday;

    @Autowired
    public UsageGuard(@Value("${rag.limits.per-person-per-hour:30}") int perPersonPerHour,
                      @Value("${rag.limits.per-person-per-day:100}") int perPersonPerDay,
                      @Value("${rag.limits.everyone-per-day:1000}") int everyonePerDay) {
        this(perPersonPerHour, perPersonPerDay, everyonePerDay, Clock.systemUTC());
    }

    UsageGuard(int perPersonPerHour, int perPersonPerDay, int everyonePerDay, Clock clock) {
        this.perPersonPerHour = perPersonPerHour;
        this.perPersonPerDay = perPersonPerDay;
        this.everyonePerDay = everyonePerDay;
        this.clock = clock;
    }

    /**
     * Counts one model answer for {@code person}, or throws {@link LimitReached} without counting it.
     * A null person (internal callers such as the evaluations) is not limited.
     */
    public synchronized void acquire(String person) {
        if (person == null) {
            return;
        }
        Instant now = clock.instant();
        long hourNow = now.getEpochSecond() / 3600;
        long dayNow = now.getEpochSecond() / 86400;
        if (dayNow != day) {
            day = dayNow;
            everyoneToday = 0;
            people.clear();
        }
        long secondsToNextDay = (dayNow + 1) * 86400 - now.getEpochSecond();
        if (everyoneToday >= everyonePerDay) {
            log.warn("Daily question limit for everyone reached limit={}", everyonePerDay);
            throw new LimitReached(secondsToNextDay, true);
        }
        Counts c = people.getOrDefault(person, new Counts(hourNow, 0, dayNow, 0));
        int inHour = c.hour() == hourNow ? c.inHour() : 0;
        if (c.inDay() >= perPersonPerDay) {
            log.info("Daily question limit reached person={} limit={}", person, perPersonPerDay);
            throw new LimitReached(secondsToNextDay, false);
        }
        if (inHour >= perPersonPerHour) {
            log.info("Hourly question limit reached person={} limit={}", person, perPersonPerHour);
            throw new LimitReached((hourNow + 1) * 3600 - now.getEpochSecond(), false);
        }
        people.put(person, new Counts(hourNow, inHour + 1, dayNow, c.inDay() + 1));
        everyoneToday++;
    }

    /** Questions answered by the model today, for everyone together. */
    public synchronized int answeredToday() {
        return Instant.now(clock).truncatedTo(ChronoUnit.DAYS).getEpochSecond() / 86400 == day ? everyoneToday : 0;
    }
}
