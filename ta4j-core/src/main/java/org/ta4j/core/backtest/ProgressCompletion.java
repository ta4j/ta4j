/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Utility class providing default progress completion callbacks for backtest
 * execution.
 * <p>
 * Provides factory methods for common progress reporting patterns:
 * <ul>
 * <li>{@link #noOp()} - No-op callback that does nothing
 * <li>{@link #logging()} - Logs progress using a logger for the calling class
 * (convenience method with automatic caller detection)
 * <li>{@link #logging(int)} - Logs progress using a logger for the calling
 * class at the specified interval (convenience method with automatic caller
 * detection)
 * <li>{@link #logging(String)} - Logs progress to a logger identified by the
 * given name
 * <li>{@link #logging(Class)} - Logs progress to a logger for the given class
 * <li>{@link #logging(Logger)} - Logs progress to the provided logger
 * <li>{@link #logging(String, int)} - Logs progress at specified intervals
 * <li>{@link #logging(Class, int)} - Logs progress at specified intervals
 * <li>{@link #logging(Logger, int)} - Logs progress at specified intervals
 * <li>{@link #loggingWithMemory()} - Logs progress and memory statistics using
 * a logger for the calling class (convenience method with automatic caller
 * detection)
 * <li>{@link #loggingWithMemory(int)} - Logs progress and memory statistics
 * using a logger for the calling class at the specified interval (convenience
 * method with automatic caller detection)
 * <li>{@link #loggingWithMemory(String)} - Logs progress and memory statistics
 * to a logger identified by the given name
 * <li>{@link #loggingWithMemory(Class)} - Logs progress and memory statistics
 * to a logger for the given class
 * <li>{@link #loggingWithMemory(Logger)} - Logs progress and memory statistics
 * to the provided logger
 * <li>{@link #loggingWithMemory(String, int)} - Logs progress and memory
 * statistics at specified intervals
 * <li>{@link #loggingWithMemory(Class, int)} - Logs progress and memory
 * statistics at specified intervals
 * <li>{@link #loggingWithMemory(Logger, int)} - Logs progress and memory
 * statistics at specified intervals
 * </ul>
 * <p>
 * When using logging callbacks, progress is reported at milestones (every 100
 * completions, at 25%, 50%, 75%, and 100% completion) to avoid log spam while
 * providing useful feedback. Progress is logged at TRACE level for minimal
 * performance impact when trace logging is disabled.
 *
 * @since 0.19
 */
public final class ProgressCompletion {

    /**
     * Default interval for logging progress updates. Progress is logged every
     * {@value #DEFAULT_LOG_INTERVAL} completions.
     *
     * @since 0.19
     */
    public static final int DEFAULT_LOG_INTERVAL = 100;

    private ProgressCompletion() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns a no-op progress completion callback that does nothing.
     *
     * @return a Consumer that ignores all progress updates
     * @since 0.19
     */
    public static Consumer<Integer> noOp() {
        return completed -> {
            // No-op
        };
    }

    /**
     * Returns a progress completion callback that logs progress using a logger for
     * the calling class. This is a convenience method that automatically detects
     * the caller class.
     * <p>
     * Progress is logged at milestones: every {@value #DEFAULT_LOG_INTERVAL}
     * completions, and at completion milestones (25%, 50%, 75%, 100%).
     * <p>
     * <b>Note:</b> Caller detection uses stack trace analysis and may not work
     * correctly in all environments (e.g., with proxies, AOP frameworks, or certain
     * JVM optimizations). For maximum reliability, use {@link #logging(Class)} or
     * {@link #logging(String)} to explicitly specify the logger.
     *
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging() {
        return logging(detectCallerClass());
    }

    /**
     * Returns a progress completion callback that logs progress using a logger for
     * the calling class, at the specified interval. This is a convenience method
     * that automatically detects the caller class.
     * <p>
     * Progress is logged every {@code interval} completions, and at completion
     * milestones (25%, 50%, 75%, 100%).
     * <p>
     * <b>Note:</b> Caller detection uses stack trace analysis and may not work
     * correctly in all environments (e.g., with proxies, AOP frameworks, or certain
     * JVM optimizations). For maximum reliability, use {@link #logging(Class, int)}
     * or {@link #logging(String, int)} to explicitly specify the logger.
     *
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(int interval) {
        return logging(detectCallerClass(), interval);
    }

    /**
     * Returns a progress completion callback that logs progress to a logger
     * identified by the given name.
     * <p>
     * Progress is logged at milestones: every {@value #DEFAULT_LOG_INTERVAL}
     * completions, and at completion milestones (25%, 50%, 75%, 100%).
     *
     * @param loggerName the name of the logger to use (e.g., class name)
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(String loggerName) {
        return logging(LoggerFactory.getLogger(loggerName));
    }

    /**
     * Returns a progress completion callback that logs progress to a logger for the
     * given class.
     * <p>
     * Progress is logged at milestones: every {@value #DEFAULT_LOG_INTERVAL}
     * completions, and at completion milestones (25%, 50%, 75%, 100%).
     *
     * @param clazz the class whose logger to use
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(Class<?> clazz) {
        return logging(LoggerFactory.getLogger(clazz));
    }

    /**
     * Returns a progress completion callback that logs progress to the provided
     * logger.
     * <p>
     * Progress is logged at milestones: every {@value #DEFAULT_LOG_INTERVAL}
     * completions, and at completion milestones (25%, 50%, 75%, 100%).
     *
     * @param logger the logger to use for progress updates
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(Logger logger) {
        return logging(logger, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Returns a progress completion callback that logs progress to a logger
     * identified by the given name, at the specified interval.
     * <p>
     * Progress is logged every {@code interval} completions, and at completion
     * milestones (25%, 50%, 75%, 100%).
     *
     * @param loggerName the name of the logger to use (e.g., class name)
     * @param interval   the number of completions between log messages
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(String loggerName, int interval) {
        return logging(LoggerFactory.getLogger(loggerName), interval);
    }

    /**
     * Returns a progress completion callback that logs progress to a logger for the
     * given class, at the specified interval.
     * <p>
     * Progress is logged every {@code interval} completions, and at completion
     * milestones (25%, 50%, 75%, 100%).
     *
     * @param clazz    the class whose logger to use
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(Class<?> clazz, int interval) {
        return logging(LoggerFactory.getLogger(clazz), interval);
    }

    /**
     * Returns a progress completion callback that logs progress to the provided
     * logger, at the specified interval.
     * <p>
     * Progress is logged every {@code interval} completions, and at completion
     * milestones (25%, 50%, 75%, 100%).
     *
     * @param logger   the logger to use for progress updates
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates
     * @since 0.19
     */
    public static Consumer<Integer> logging(Logger logger, int interval) {
        if (logger == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }

        return new LoggingProgressCallback(logger, interval);
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics using a logger for the calling class. This is a convenience method
     * that automatically detects the caller class.
     * <p>
     * Progress and memory statistics are logged at milestones: every
     * {@value #DEFAULT_LOG_INTERVAL} completions, and at completion milestones
     * (25%, 50%, 75%, 100%). Memory statistics include heap used, heap free, and
     * heap max.
     * <p>
     * <b>Note:</b> Caller detection uses stack trace analysis and may not work
     * correctly in all environments (e.g., with proxies, AOP frameworks, or certain
     * JVM optimizations). For maximum reliability, use
     * {@link #loggingWithMemory(Class)} or {@link #loggingWithMemory(String)} to
     * explicitly specify the logger.
     *
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory() {
        return loggingWithMemory(detectCallerClass());
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics using a logger for the calling class, at the specified interval.
     * This is a convenience method that automatically detects the caller class.
     * <p>
     * Progress and memory statistics are logged every {@code interval} completions,
     * and at completion milestones (25%, 50%, 75%, 100%). Memory statistics include
     * heap used, heap free, and heap max.
     * <p>
     * <b>Note:</b> Caller detection uses stack trace analysis and may not work
     * correctly in all environments (e.g., with proxies, AOP frameworks, or certain
     * JVM optimizations). For maximum reliability, use
     * {@link #loggingWithMemory(Class, int)} or
     * {@link #loggingWithMemory(String, int)} to explicitly specify the logger.
     *
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(int interval) {
        return loggingWithMemory(detectCallerClass(), interval);
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to a logger identified by the given name.
     * <p>
     * Progress and memory statistics are logged at milestones: every
     * {@value #DEFAULT_LOG_INTERVAL} completions, and at completion milestones
     * (25%, 50%, 75%, 100%). Memory statistics include heap used, heap free, and
     * heap max.
     *
     * @param loggerName the name of the logger to use (e.g., class name)
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(String loggerName) {
        return loggingWithMemory(LoggerFactory.getLogger(loggerName));
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to a logger for the given class.
     * <p>
     * Progress and memory statistics are logged at milestones: every
     * {@value #DEFAULT_LOG_INTERVAL} completions, and at completion milestones
     * (25%, 50%, 75%, 100%). Memory statistics include heap used, heap free, and
     * heap max.
     *
     * @param clazz the class whose logger to use
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(Class<?> clazz) {
        return loggingWithMemory(LoggerFactory.getLogger(clazz));
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to the provided logger.
     * <p>
     * Progress and memory statistics are logged at milestones: every
     * {@value #DEFAULT_LOG_INTERVAL} completions, and at completion milestones
     * (25%, 50%, 75%, 100%). Memory statistics include heap used, heap free, and
     * heap max.
     *
     * @param logger the logger to use for progress updates
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(Logger logger) {
        return loggingWithMemory(logger, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to a logger identified by the given name, at the specified
     * interval.
     * <p>
     * Progress and memory statistics are logged every {@code interval} completions,
     * and at completion milestones (25%, 50%, 75%, 100%). Memory statistics include
     * heap used, heap free, and heap max.
     *
     * @param loggerName the name of the logger to use (e.g., class name)
     * @param interval   the number of completions between log messages
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(String loggerName, int interval) {
        return loggingWithMemory(LoggerFactory.getLogger(loggerName), interval);
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to a logger for the given class, at the specified interval.
     * <p>
     * Progress and memory statistics are logged every {@code interval} completions,
     * and at completion milestones (25%, 50%, 75%, 100%). Memory statistics include
     * heap used, heap free, and heap max.
     *
     * @param clazz    the class whose logger to use
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(Class<?> clazz, int interval) {
        return loggingWithMemory(LoggerFactory.getLogger(clazz), interval);
    }

    /**
     * Returns a progress completion callback that logs progress and memory
     * statistics to the provided logger, at the specified interval.
     * <p>
     * Progress and memory statistics are logged every {@code interval} completions,
     * and at completion milestones (25%, 50%, 75%, 100%). Memory statistics include
     * heap used, heap free, and heap max.
     *
     * @param logger   the logger to use for progress updates
     * @param interval the number of completions between log messages
     * @return a Consumer that logs progress updates with memory statistics
     * @since 0.19
     */
    public static Consumer<Integer> loggingWithMemory(Logger logger, int interval) {
        if (logger == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }

        return new LoggingWithMemoryProgressCallback(logger, interval);
    }

    /**
     * Detects the calling class by analyzing the stack trace. This method skips
     * internal frames (ProgressCompletion, Thread, etc.) to find the actual caller.
     *
     * @return the calling class, or ProgressCompletion.class as a fallback
     */
    private static Class<?> detectCallerClass() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String thisClassName = ProgressCompletion.class.getName();

        // Start from index 3 to skip:
        // 0: getStackTrace
        // 1: detectCallerClass
        // 2: logging()/logging(int)/loggingWithMemory()/loggingWithMemory(int)
        for (int i = 3; i < stack.length; i++) {
            String className = stack[i].getClassName();

            // Skip internal frames
            if (className.equals(thisClassName) || className.equals(Thread.class.getName())
                    || className.startsWith("java.lang.reflect.") || className.startsWith("sun.reflect.")
                    || className.startsWith("jdk.internal.reflect.")) {
                continue;
            }

            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                // Continue searching if class can't be loaded
            }
        }

        // Fallback to ProgressCompletion if we can't detect the caller
        return ProgressCompletion.class;
    }

    /**
     * Formats memory size in bytes to a human-readable string (e.g., "512 MB").
     *
     * @param bytes the size in bytes
     * @return formatted string
     */
    private static String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Internal implementation of logging progress callback.
     */
    private static final class LoggingProgressCallback implements Consumer<Integer> {

        private final Logger logger;
        private final int interval;
        private volatile int totalStrategies = -1;

        private LoggingProgressCallback(Logger logger, int interval) {
            this.logger = logger;
            this.interval = interval;
        }

        @Override
        public void accept(Integer completed) {
            // Early exit if trace logging is not enabled to avoid unnecessary calculations
            if (!logger.isTraceEnabled()) {
                return;
            }

            int total = totalStrategies;

            // If we don't know the total yet, just log at interval milestones
            if (total < 0) {
                if (completed % interval == 0) {
                    logger.trace("Progress: {} strategies completed", completed);
                }
                return;
            }

            // Log at interval milestones
            if (completed % interval == 0) {
                int percentComplete = (int) (completed * 100.0) / total;
                logger.trace("Progress: {}/{} strategies completed ({}%)", completed, total, percentComplete);
                return;
            }

            // Log at completion milestones (25%, 50%, 75%, 100%)
            int percentComplete = (int) (completed * 100.0) / total;
            int prevPercent = completed > 1 ? (int) ((completed - 1) * 100.0) / total : 0;

            if (percentComplete >= 25 && prevPercent < 25) {
                logger.trace("Progress: {}/{} strategies completed (25%)", completed, total);
            } else if (percentComplete >= 50 && prevPercent < 50) {
                logger.trace("Progress: {}/{} strategies completed (50%)", completed, total);
            } else if (percentComplete >= 75 && prevPercent < 75) {
                logger.trace("Progress: {}/{} strategies completed (75%)", completed, total);
            } else if (completed == total) {
                logger.trace("Progress: {}/{} strategies completed (100%)", completed, total);
            }
        }

        /**
         * Sets the total number of strategies for percentage calculation. This is
         * called internally by BacktestExecutor.
         *
         * @param total the total number of strategies
         */
        void setTotalStrategies(int total) {
            this.totalStrategies = total;
        }
    }

    /**
     * Internal implementation of logging progress callback with memory statistics.
     */
    private static final class LoggingWithMemoryProgressCallback implements Consumer<Integer> {

        private final Logger logger;
        private final int interval;
        private volatile int totalStrategies = -1;

        private LoggingWithMemoryProgressCallback(Logger logger, int interval) {
            this.logger = logger;
            this.interval = interval;
        }

        @Override
        public void accept(Integer completed) {
            // Early exit if trace logging is not enabled to avoid unnecessary calculations
            if (!logger.isTraceEnabled()) {
                return;
            }

            int total = totalStrategies;
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            String memoryInfo = String.format("Heap: %s/%s used (%.1f%%)", formatMemory(usedMemory),
                    formatMemory(maxMemory), (usedMemory * 100.0) / maxMemory);

            // If we don't know the total yet, just log at interval milestones
            if (total < 0) {
                if (completed % interval == 0) {
                    logger.trace("Progress: {} strategies completed | {}", completed, memoryInfo);
                }
                return;
            }

            // Log at interval milestones
            if (completed % interval == 0) {
                int percentComplete = (int) (completed * 100.0) / total;
                logger.trace("Progress: {}/{} strategies completed ({}%) | {}", completed, total, percentComplete,
                        memoryInfo);
                return;
            }

            // Log at completion milestones (25%, 50%, 75%, 100%)
            int percentComplete = (int) (completed * 100.0) / total;
            int prevPercent = completed > 1 ? (int) ((completed - 1) * 100.0) / total : 0;

            if (percentComplete >= 25 && prevPercent < 25) {
                logger.trace("Progress: {}/{} strategies completed (25%) | {}", completed, total, memoryInfo);
            } else if (percentComplete >= 50 && prevPercent < 50) {
                logger.trace("Progress: {}/{} strategies completed (50%) | {}", completed, total, memoryInfo);
            } else if (percentComplete >= 75 && prevPercent < 75) {
                logger.trace("Progress: {}/{} strategies completed (75%) | {}", completed, total, memoryInfo);
            } else if (completed == total) {
                logger.trace("Progress: {}/{} strategies completed (100%) | {}", completed, total, memoryInfo);
            }
        }

        /**
         * Sets the total number of strategies for percentage calculation. This is
         * called internally by BacktestExecutor.
         *
         * @param total the total number of strategies
         */
        void setTotalStrategies(int total) {
            this.totalStrategies = total;
        }
    }

    /**
     * Configures a progress callback with the total strategies count. This allows
     * percentage-based logging to work correctly for logging callbacks.
     * <p>
     * This method only affects logging callbacks ({@link LoggingProgressCallback}
     * and {@link LoggingWithMemoryProgressCallback}). For other callback types, the
     * callback is returned unchanged and the {@code totalStrategies} parameter is
     * ignored.
     *
     * @param callback        the progress callback to configure (must not be null)
     * @param totalStrategies the total number of strategies
     * @return the same callback instance (configured if it's a logging callback)
     * @throws IllegalArgumentException if callback is null
     */
    static Consumer<Integer> withTotalStrategies(Consumer<Integer> callback, int totalStrategies) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (callback instanceof LoggingProgressCallback) {
            ((LoggingProgressCallback) callback).setTotalStrategies(totalStrategies);
        } else if (callback instanceof LoggingWithMemoryProgressCallback) {
            ((LoggingWithMemoryProgressCallback) callback).setTotalStrategies(totalStrategies);
        }
        // For other callback types, return unchanged (totalStrategies is ignored)
        return callback;
    }
}
