package net.sf.tail.graphics;

import static junit.framework.Assert.assertEquals;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.report.Report;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.sample.SampleIndicator;
import net.sf.tail.series.DefaultTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class CriteriaDatasetTest {

	private TimeSeries series;

	private List<Report> reports;

	private List<DefaultTick> ticks;
	
	private Set<Strategy> strategies;
	
	private Set<Strategy> strategies2;

	private SampleIndicator indicator1;

	private SampleIndicator indicator2;

	@Before
	public void setUp() throws Exception {

		ticks = new LinkedList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 10), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 11), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 12), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 13), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 14), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 15), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 16), 7d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 17), 8d));
		
		series = new DefaultTimeSeries(ticks);
		reports = new LinkedList<Report>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d, 5d, 3d, 4d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d, 2d, 4d, 3d});
		
		strategies = new HashSet<Strategy>();
		strategies2 = new HashSet<Strategy>();
		
		strategies.add(new IndicatorCrossedIndicatorStrategy(indicator1, indicator2));
		strategies2.add(new IndicatorCrossedIndicatorStrategy(indicator2, indicator1));
		
		
		WalkForward walk = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		TimeSeriesSlicer slice = new RegularSlicer(series, new Period().withDays(2));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		Report report1 = walk.walk(new JavaStrategiesSet( strategies), slice, criterion);
		report1.setName("rep1");
		Report report2 = walk.walk(new JavaStrategiesSet( strategies2), slice, criterion);
		report2.setName("rep2");
		reports.add(report1);
		reports.add(report2);
	}

	@Test
	public void testDefaultConstructor() {
		CriteriaDataset dataset = new CriteriaDataset(reports, series, 4);
		assertEquals(reports.size(), dataset.getRowCount());
		assertEquals(4, dataset.getColumnCount());
		assertEquals(4d/3, dataset.getValue(1, 3));
	}

	@Test
	public void testMoveRightWhenIndicatorDontHaveMoreDataUnmappedShouldDoNothing() {
		CriteriaDataset dataset = new CriteriaDataset(reports, series, 12);
		dataset.moveRight(1);
		assertEquals(reports.size(), dataset.getRowCount());
		assertEquals(12, dataset.getColumnCount());
		assertEquals(1d, dataset.getValue(1, 0));
	}

	@Test
	public void testMoveLeftWhenIndicatorDontHaveMoreDataUnmappedShouldDoNothing() {
		CriteriaDataset dataset = new CriteriaDataset(reports, series, 4);
		dataset.moveLeft(1);
		assertEquals(reports.size(), dataset.getRowCount());
		assertEquals(4, dataset.getColumnCount());
		assertEquals(1d, dataset.getValue(1, 1));
	}
}
