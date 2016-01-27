package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * The Class IchimokuSenkouSpanAIndicator.
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Decimal> {

    /** The kijun sen indicator. */
    private final IchimokuKijunSenIndicator conversionLine;

    /** The tenkan sen indicator. */
    private final IchimokuTenkanSenIndicator baseLine;

    /**
     * Instantiates a new Ichimoku Senkou Span A indicator.
     *
     * @param series the series
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series) {
        this(series, new IchimokuKijunSenIndicator(series), new IchimokuTenkanSenIndicator(series));
    }
    
    /**
     * Instantiates a new Ichimoku Senkou Span A indicator.
     *
     * @param series the series
     * @param timeFrameConversionLine the time frame conversion line (usually 26)
     * @param timeFrameBaseLine the time frame base line (usually 9)
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, int timeFrameConversionLine, int timeFrameBaseLine) {
        this(series, new IchimokuKijunSenIndicator(series, timeFrameConversionLine), new IchimokuTenkanSenIndicator(series, timeFrameBaseLine));
    }
    
    /**
     * Instantiates a new Ichimoku Senkou Span A indicator.
     *
     * @param series the series
     * @param conversionLine the conversion line
     * @param baseLine the base line
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, IchimokuKijunSenIndicator conversionLine, IchimokuTenkanSenIndicator baseLine) {
        super(series);
        this.baseLine = baseLine;
        this.conversionLine = conversionLine;
    }

    @Override
    protected Decimal calculate(int index) {
        return conversionLine.getValue(index).plus(baseLine.getValue(index)).dividedBy(Decimal.TWO);
    }

}
