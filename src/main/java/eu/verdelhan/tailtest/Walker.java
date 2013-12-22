package net.sf.tail;

import net.sf.tail.report.Report;

public interface Walker {

	Report walk(StrategiesSet strategies, TimeSeriesSlicer splittedSeries, AnalysisCriterion criterion);

}