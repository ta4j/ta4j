package org.ta4j.core.criteria;

import java.time.temporal.ChronoUnit;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

public class InPositionPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = DecimalNumFactory.getInstance();
        var totalDuration = totalTradingDuration(series);
        if (series.isEmpty() || totalDuration == 0) {
            return numFactory.zero();
        }
        var positionDuration = positionDuration(series, position);
        var percentage = (double) positionDuration / totalDuration * 100;
        return numFactory.numOf(percentage);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var totalDuration = totalTradingDuration(series);
        if (series.isEmpty() || totalDuration == 0 || tradingRecord.getPositionCount() == 0) {
            return numFactory.zero();
        }
        var positionDuration = tradingRecord.getPositions().stream()
                .mapToLong(p -> positionDuration(series, p))
                .sum();
        var percentage = (double) positionDuration / totalDuration * 100;
        return numFactory.numOf(percentage);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    private static long totalTradingDuration(BarSeries series) {
        var start = series.getFirstBar().getBeginTime();
        var end = series.getLastBar().getEndTime();
        return ChronoUnit.NANOS.between(start, end);
    }

    private static long positionDuration(BarSeries series, Position position) {
        var entryStart = series.getBar(position.getEntry().getIndex()).getBeginTime();
        var exitIndex = position.isClosed() ? position.getExit().getIndex() : series.getEndIndex();
        var exitEnd = series.getBar(exitIndex).getEndTime();
        return ChronoUnit.NANOS.between(entryStart, exitEnd);
    }
}

