package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Three black crows indicator.
 * </p>
 * @see <a href="http://www.investopedia.com/terms/t/three_black_crows.asp">
 *     http://www.investopedia.com/terms/t/three_black_crows.asp</a>
 */
public class ThreeBlackCrowsIndicator extends CachedIndicator<Boolean> {

    /** Lower shadow */
    private final LowerShadowIndicator lowerShadowInd;
    /** Average lower shadow */
    private final SMAIndicator averageLowerShadowInd;
    /** Factor used when checking if a candle has a very short lower shadow */
    private final Num factor;

    private int whiteCandleIndex = -1;

    /**
     * Constructor.
     * @param series a time series
     * @param barCount the number of bars used to calculate the average lower shadow
     * @param factor the factor used when checking if a candle has a very short lower shadow
     */
    public ThreeBlackCrowsIndicator(TimeSeries series, int barCount, double factor) {
        super(series);
        lowerShadowInd = new LowerShadowIndicator(series);
        averageLowerShadowInd = new SMAIndicator(lowerShadowInd, barCount);
        this.factor = numOf(factor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 3) {
            // We need 4 candles: 1 white, 3 black
            return false;
        }
        whiteCandleIndex = index - 3;
        return getTimeSeries().getBar(whiteCandleIndex).isBullish()
                && isBlackCrow(index - 2)
                && isBlackCrow(index - 1)
                && isBlackCrow(index);
    }

    /**
     * @param index the bar/candle index
     * @return true if the bar/candle has a very short lower shadow, false otherwise
     */
    private boolean hasVeryShortLowerShadow(int index) {
        Num currentLowerShadow = lowerShadowInd.getValue(index);
        // We use the white candle index to remove to bias of the previous crows
        Num averageLowerShadow = averageLowerShadowInd.getValue(whiteCandleIndex);

        return currentLowerShadow.isLessThan(averageLowerShadow.multipliedBy(factor));
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is declining, false otherwise
     */
    private boolean isDeclining(int index) {
        Bar prevBar = getTimeSeries().getBar(index - 1);
        Bar currBar = getTimeSeries().getBar(index);
        final Num prevOpenPrice = prevBar.getOpenPrice();
        final Num prevClosePrice = prevBar.getClosePrice();
        final Num currOpenPrice = currBar.getOpenPrice();
        final Num currClosePrice = currBar.getClosePrice();

        // Opens within the body of the previous candle
        return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
                // Closes below the previous close price
                && currClosePrice.isLessThan(prevClosePrice);
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is a black crow, false otherwise
     */
    private boolean isBlackCrow(int index) {
        Bar prevBar = getTimeSeries().getBar(index - 1);
        Bar currBar = getTimeSeries().getBar(index);
        if (currBar.isBearish()) {
            if (prevBar.isBullish()) {
                // First crow case
                return hasVeryShortLowerShadow(index)
                        && currBar.getOpenPrice().isLessThan(prevBar.getHighPrice());
            } else {
                return hasVeryShortLowerShadow(index) && isDeclining(index);
            }
        }
        return false;
    }
}
