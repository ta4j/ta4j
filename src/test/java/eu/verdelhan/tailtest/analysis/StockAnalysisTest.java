package net.sf.tail.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeries;
import net.sf.tail.analysis.criteria.NumberOfTicksCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.report.Report;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.series.SerializableTimeSeries;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class StockAnalysisTest {

	private SerializableTimeSeries stock;

	private TotalProfitCriterion applyedCriterion;

	private StockAnalysis stockAnalysis;

	private RegularSlicer slicer;

	private HashSet<Strategy> strategies;

	private Report report;

	private WalkForward walker;

	@Before
	public void setUp() throws Exception {
		this.stock = new SerializableTimeSeries("Teste", "BaseBovespa/15min/ambv4.csv", new CedroTimeSeriesLoader());

		this.applyedCriterion = new TotalProfitCriterion();
		Period period = new Period().withYears(1);
		DateTime date = new DateTime();
		TimeSeries series = new SampleTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 2, 1), date.withDate(
				2000, 3, 1), date.withDate(2001, 1, 1), date.withDate(2001, 2, 1), date.withDate(2001, 12, 12), date
				.withDate(2002, 1, 1), date.withDate(2002, 2, 1), date.withDate(2002, 3, 1), date.withDate(2002, 5, 1),
				date.withDate(2003, 3, 1));

		slicer = new RegularSlicer(series, period, date.withYear(2000).withMonthOfYear(7));
		
		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null,
				new Operation(2, OperationType.BUY), null, null, null, null, null, null, null, null };
		Operation[] exit = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null,
				new Operation(5, OperationType.SELL), null, null, null, null, null };

		strategies = new HashSet<Strategy>();
		strategies.add(new FakeStrategy(enter, exit));

		walker = new WalkForward(new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		report = walker.walk(new JavaStrategiesSet( strategies), slicer, applyedCriterion);
		report.setName("");
		this.stockAnalysis = new StockAnalysis(stock, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
	}

	@Test
	public void testCreateReport() {
		assertEquals(report, stockAnalysis.addReport("", new JavaStrategiesSet( strategies)));
	}

	@Test
	public void testAddCriterionAndCriteria() {
		stockAnalysis.addReport("", new JavaStrategiesSet( strategies));
		stockAnalysis.addCriterion(new NumberOfTicksCriterion());
		assertEquals(1, stockAnalysis.getAdditionalCriteria().size());
		List<AnalysisCriterion> criteria = new ArrayList<AnalysisCriterion>();
		criteria.add(new NumberOfTicksCriterion());
		stockAnalysis.addCriteria(criteria);
		assertEquals(2, stockAnalysis.getAdditionalCriteria().size());
		assertEquals(1, stockAnalysis.getReports().size());
	}

	@Test
	public void testGetSlicer() {
		assertEquals(slicer, stockAnalysis.getSlicer());
	}

	@Test
	public void testGetApplyedCriterion() {
		assertEquals(applyedCriterion, stockAnalysis.getApplyedCriterion());
	}

	@Test
	public void testGetStock() {
		assertEquals(stock, stockAnalysis.getStock());
	}

	@Test
	public void testGetEvaluator() {
		assertEquals(new HigherValueEvaluatorFactory(), stockAnalysis.getEvaluatorFactory());
	}

	
	@Test
	public void testGetWalker() {
		assertEquals(walker.getClass(), stockAnalysis.getWalker().getClass());
	}
	
	@Test
	public void testEquals()
	{
		StockAnalysis analysis = new StockAnalysis(stock, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		assertTrue(analysis.equals(analysis));
		assertTrue(analysis.equals(new StockAnalysis(stock, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory())));
		assertFalse(analysis.equals(new TotalProfitCriterion()));
		
		assertFalse(analysis.equals(new StockAnalysis(stock, null, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory())));
		assertFalse((new StockAnalysis(stock, null, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory()).equals(analysis)));
		assertFalse(analysis.equals(new StockAnalysis(stock, applyedCriterion, slicer,null, null)));
		assertFalse((new StockAnalysis(stock, applyedCriterion, slicer, null, null).equals(analysis)));
		assertFalse(analysis.equals(new StockAnalysis(stock, applyedCriterion, null, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory())));
		assertFalse((new StockAnalysis(stock, applyedCriterion, null, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory()).equals(analysis)));
		assertFalse(analysis.equals(new StockAnalysis(null, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory())));
		assertFalse((new StockAnalysis(null, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory()).equals(analysis)));
		
		analysis.addReport("Teste", new JavaStrategiesSet( strategies));
		StockAnalysis analysis2 = new StockAnalysis(stock, applyedCriterion, slicer, new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		StockAnalysis analysis3 = new StockAnalysis(null, null, null, null, null);
		assertFalse(analysis.equals(analysis2));
		assertFalse(analysis2.equals(analysis));
		assertFalse(analysis.hashCode() == analysis2.hashCode());
		assertFalse(analysis.hashCode() == analysis3.hashCode());
		
		assertFalse(analysis.equals(5d));
		assertFalse(analysis.equals(null));
	}
}
