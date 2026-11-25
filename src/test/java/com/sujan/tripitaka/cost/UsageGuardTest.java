package com.sujan.tripitaka.cost;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageGuardTest {

    /** A clock the test can move. */
    static class MovableClock extends Clock {
        Instant now;

        MovableClock(String start) {
            now = Instant.parse(start);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void limitsEachPersonPerHourAndTellsWhenToComeBack() {
        var clock = new MovableClock("2026-11-24T10:45:00Z");
        var guard = new UsageGuard(3, 100, 1000, clock);
        for (int i = 0; i < 3; i++) {
            guard.acquire("a");
        }

        var limited = assertThrows(UsageGuard.LimitReached.class, () -> guard.acquire("a"));
        assertEquals(15 * 60, limited.retryAfterSeconds()); // the hour reopens at 11:00
        assertFalse(limited.everyone());
        assertDoesNotThrow(() -> guard.acquire("b")); // others are not affected

        clock.now = Instant.parse("2026-11-24T11:00:00Z");
        assertDoesNotThrow(() -> guard.acquire("a"));
    }

    @Test
    void limitsEachPersonPerDay() {
        var clock = new MovableClock("2026-11-24T01:00:00Z");
        var guard = new UsageGuard(100, 2, 1000, clock);
        guard.acquire("a");
        clock.now = Instant.parse("2026-11-24T05:00:00Z");
        guard.acquire("a");

        assertThrows(UsageGuard.LimitReached.class, () -> guard.acquire("a"));
        clock.now = Instant.parse("2026-11-25T00:00:01Z");
        assertDoesNotThrow(() -> guard.acquire("a"));
    }

    @Test
    void capsEveryoneTogetherPerDay() {
        var guard = new UsageGuard(100, 100, 2, new MovableClock("2026-11-24T10:00:00Z"));
        guard.acquire("a");
        guard.acquire("b");

        var limited = assertThrows(UsageGuard.LimitReached.class, () -> guard.acquire("c"));
        assertTrue(limited.everyone());
        assertEquals(2, guard.answeredToday());
    }

    @Test
    void internalCallersAreNotLimited() {
        var guard = new UsageGuard(0, 0, 0, new MovableClock("2026-11-24T10:00:00Z"));

        assertDoesNotThrow(() -> guard.acquire(null));
    }

    @Test
    void aRefusedQuestionIsNotCounted() {
        var guard = new UsageGuard(1, 100, 1000, new MovableClock("2026-11-24T10:00:00Z"));
        guard.acquire("a");
        assertThrows(UsageGuard.LimitReached.class, () -> guard.acquire("a"));

        assertEquals(1, guard.answeredToday());
    }
}
