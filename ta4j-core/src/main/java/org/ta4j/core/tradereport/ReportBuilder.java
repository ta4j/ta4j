package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

public interface ReportBuilder<T> {

    T buildReport(TradingRecord tradingRecord, TimeSeries series);
}
