package org.ta4j.core;

import java.time.Duration;

public enum TradeInterval {

    W1 {
        @Override
        public Duration duration() {
            return Duration.ofDays(7);
        }
    }, D3 {
        @Override
        public Duration duration() {
            return Duration.ofDays(3);
        }
    }, D1 {
        @Override
        public Duration duration() {
            return Duration.ofDays(1);
        }
    }, H12 {
        @Override
        public Duration duration() {
            return Duration.ofHours(12);
        }
    }, H6 {
        @Override
        public Duration duration() {
            return Duration.ofHours(6);
        }
    }, H4 {
        @Override
        public Duration duration() {
            return Duration.ofHours(4);
        }
    }, H2 {
        @Override
        public Duration duration() {
            return Duration.ofHours(2);
        }
    }, H1 {
        @Override
        public Duration duration() {
            return Duration.ofHours(1);
        }
    }, M30 {
        @Override
        public Duration duration() {
            return Duration.ofMinutes(30);
        }
    }, M15 {
        @Override
        public Duration duration() {
            return Duration.ofMinutes(15);
        }
    }, M5 {
        @Override
        public Duration duration() {
            return Duration.ofMinutes(5);
        }
    }, M3 {
        @Override
        public Duration duration() {
            return Duration.ofMinutes(3);
        }
    }, M1 {
        @Override
        public Duration duration() {
            return Duration.ofMinutes(1);
        }
    };

    public abstract Duration duration();
}
