package net.sf.tail.graphics;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.report.Report;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.sample.SampleIndicator;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;
import net.sf.tail.strategy.IndicatorOverIndicatorStrategy;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;

public class FasterCriteriaDatasetTest {

	@Test
	public void testFasterCriteriaDatasetWithoutDoFast()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		LinkedList<Report> reports;
		SampleIndicator indicator1;
		SampleIndicator indicator2;
		HashSet<Strategy> strategies;
		HashSet<Strategy> strategies2;
		
		reports = new LinkedList<Report>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d});
		
		strategies = new HashSet<Strategy>();
		strategies2 = new HashSet<Strategy>();
		
		strategies.add(new IndicatorCrossedIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorCrossedIndicatorStrategy(indicator2, indicator1));
		strategies.add(new IndicatorOverIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorOverIndicatorStrategy(indicator2, indicator1));

		WalkForward walk = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		TimeSeriesSlicer slice = new RegularSlicer(series, new Period().withDays(2));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		Report report1 = walk.walk(new JavaStrategiesSet( strategies), slice, criterion);
		report1.setName("rep1");
		Report report2 = walk.walk(new JavaStrategiesSet( strategies2), slice, criterion);
		report2.setName("rep2");
		reports.add(report1);
		reports.add(report2);
		
		FasterCriteriaDataset dataset = new FasterCriteriaDataset(series, reports);
		assertEquals(8, dataset.getColumnCount());
	}
	
	@Test
	public void testFasterCriteriaDatasetDoFast()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		LinkedList<Report> reports;
		SampleIndicator indicator1;
		SampleIndicator indicator2;
		HashSet<Strategy> strategies;
		HashSet<Strategy> strategies2;
		
		reports = new LinkedList<Report>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d});
		
		strategies = new HashSet<Strategy>();
		strategies2 = new HashSet<Strategy>();
		
		strategies.add(new IndicatorCrossedIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorCrossedIndicatorStrategy(indicator2, indicator1));
		strategies.add(new IndicatorOverIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorOverIndicatorStrategy(indicator2, indicator1));

		WalkForward walk = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		TimeSeriesSlicer slice = new RegularSlicer(series, new Period().withDays(2));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		Report report1 = walk.walk(new JavaStrategiesSet( strategies), slice, criterion);
		report1.setName("rep1");
		Report report2 = walk.walk(new JavaStrategiesSet( strategies2), slice, criterion);
		report2.setName("rep2");
		reports.add(report1);
		reports.add(report2);
		
		FasterCriteriaDataset dataset = new FasterCriteriaDataset(series, reports, true);
		assertEquals(4, dataset.getColumnCount());
	}
	
	@Test
	public void testFasterCriteriaDatasetOnlyWithGains()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 7d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 8d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		LinkedList<Report> reports;
		SampleIndicator indicator1;
		SampleIndicator indicator2;
		HashSet<Strategy> strategies;
		HashSet<Strategy> strategies2;
		
		reports = new LinkedList<Report>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d});
		
		strategies = new HashSet<Strategy>();
		strategies2 = new HashSet<Strategy>();
		
		strategies.add(new IndicatorCrossedIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorCrossedIndicatorStrategy(indicator2, indicator1));
		strategies.add(new IndicatorOverIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorOverIndicatorStrategy(indicator2, indicator1));

		WalkForward walk = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		TimeSeriesSlicer slice = new RegularSlicer(series, new Period().withDays(2));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		Report report1 = walk.walk(new JavaStrategiesSet( strategies), slice, criterion);
		report1.setName("rep1");
		Report report2 = walk.walk(new JavaStrategiesSet( strategies2), slice, criterion);
		report2.setName("rep2");
		reports.add(report1);
		reports.add(report2);
		
		FasterCriteriaDataset dataset = new FasterCriteriaDataset(series, reports, true);
		assertEquals(4, dataset.getColumnCount());
	}
	@Test
	public void testFasterCriteriaDatasetNotSoFast()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		LinkedList<Report> reports;
		SampleIndicator indicator1;
		SampleIndicator indicator2;
		HashSet<Strategy> strategies;
		HashSet<Strategy> strategies2;
		
		reports = new LinkedList<Report>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d});
		
		strategies = new HashSet<Strategy>();
		strategies2 = new HashSet<Strategy>();
		
		strategies.add(new IndicatorCrossedIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorCrossedIndicatorStrategy(indicator2, indicator1));
		strategies.add(new IndicatorOverIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorOverIndicatorStrategy(indicator2, indicator1));

		WalkForward walk = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		TimeSeriesSlicer slice = new RegularSlicer(series, new Period().withDays(2));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		Report report1 = walk.walk(new JavaStrategiesSet( strategies), slice, criterion);
		report1.setName("rep1");
		Report report2 = walk.walk(new JavaStrategiesSet( strategies2), slice, criterion);
		report2.setName("rep2");
		reports.add(report1);
		reports.add(report2);
		
		FasterCriteriaDataset dataset = new FasterCriteriaDataset(series, reports, true);
		assertEquals(4, dataset.getColumnCount());
	}
	
}
