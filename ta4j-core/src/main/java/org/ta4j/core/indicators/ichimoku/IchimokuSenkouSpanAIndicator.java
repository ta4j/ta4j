package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Num> {

    /** The Tenkan-sen indicator */
    private final IchimokuTenkanSenIndicator conversionLine;

    /** The Kijun-sen indicator */
    private final IchimokuKijunSenIndicator baseLine;

    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series) {
        this(series, new IchimokuTenkanSenIndicator(series), new IchimokuKijunSenIndicator(series));
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param barCountConversionLine the time frame for the conversion line (usually 9)
     * @param barCountBaseLine the time frame for the base line (usually 26)
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, int barCountConversionLine, int barCountBaseLine) {
        this(series, new IchimokuTenkanSenIndicator(series, barCountConversionLine), new IchimokuKijunSenIndicator(series, barCountBaseLine));
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param conversionLine the conversion line
     * @param baseLine the base line
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, IchimokuTenkanSenIndicator conversionLine, IchimokuKijunSenIndicator baseLine) {
        super(series);
        this.conversionLine = conversionLine;
        this.baseLine = baseLine;
    }

    @Override
    protected Num calculate(int index) {
        return conversionLine.getValue(index).plus(baseLine.getValue(index)).dividedBy(numOf(2));
    }
}
