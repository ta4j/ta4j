package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.ta4j.core.num.Num;

/**
 * An object pool for {@link Bar} instances to reduce memory usage and garbage collection.
 * This class maintains a pool of {@link BaseBar} objects that can be reused instead of
 * creating new instances each time.
 */
public class BarPool {

    /** Singleton instance of the BarPool */
    private static final BarPool INSTANCE = new BarPool();

    /** Map of pools for different time periods */
    private final Map<Duration, ConcurrentLinkedQueue<BaseBar>> pools = new ConcurrentHashMap<>();

    /** Maximum size for each pool */
    private static final int MAX_POOL_SIZE = 10_000;

    /**
     * Private constructor to enforce singleton pattern
     */
    private BarPool() {
    }

    /**
     * Gets the singleton instance of the BarPool
     * 
     * @return the singleton instance
     */
    public static BarPool getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a Bar from the pool or creates a new one if none is available
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the total traded volume of the bar period
     * @param amount     the total traded amount of the bar period
     * @param trades     the number of trades of the bar period
     * @return a Bar instance (either from the pool or newly created)
     */
    public Bar getBar(Duration timePeriod, Instant endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice,
            Num volume, Num amount, long trades) {
        
        // Get or create the pool for this time period
        ConcurrentLinkedQueue<BaseBar> pool = pools.computeIfAbsent(timePeriod, 
                k -> new ConcurrentLinkedQueue<>());
        
        // Try to get a bar from the pool
        BaseBar bar = pool.poll();
        
        if (bar == null) {
            // If no bar is available in the pool, create a new one
            return new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        } else {
            // Reset the bar with the new values
            bar.resetBar(endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
            return bar;
        }
    }

    /**
     * Returns a Bar to the pool for reuse
     * 
     * @param bar the Bar to return to the pool
     */
    public void returnBar(Bar bar) {
        if (bar instanceof BaseBar) {
            BaseBar baseBar = (BaseBar) bar;
            Duration timePeriod = bar.getTimePeriod();
            
            ConcurrentLinkedQueue<BaseBar> pool = pools.get(timePeriod);
            if (pool != null && pool.size() < MAX_POOL_SIZE) {
                pool.offer(baseBar);
            }
        }
    }

    /**
     * Clears all pools
     */
    public void clear() {
        pools.clear();
    }
}