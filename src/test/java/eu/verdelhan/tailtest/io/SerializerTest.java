package net.sf.tail.io;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;

import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.analysis.StockAnalysis;
import net.sf.tail.analysis.criteria.AverageProfitCriterion;
import net.sf.tail.analysis.criteria.MaximumDrawDownCriterion;
import net.sf.tail.analysis.criteria.NumberOfTicksCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.series.FullyMemorizedSlicer;
import net.sf.tail.series.SerializableTimeSeries;
import net.sf.tail.strategiesSet.RubyStrategiesSet;

import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class SerializerTest {

	private TotalProfitCriterion applyedCriterion;

	private TimeSeriesSlicer slicer;

	private SerializableTimeSeries stock;

	@Before
	public void setUp() throws Exception {
		stock = new SerializableTimeSeries("test", "BaseBovespa/tests/Cedro-ReaderTest.csv", new CedroTimeSeriesLoader());
		slicer = new FullyMemorizedSlicer(stock, new Period().withDays(1));

//		runner = new HistoryRunner(OperationType.BUY);
//		evaluator = new HigherValueEvaluator(runner);

		applyedCriterion = new TotalProfitCriterion();

	}

	@Test
	public void testStockSerializer() throws FileNotFoundException, IOException {
		StockSerializer serializer = new StockSerializer();
		String serializedStock = serializer.toXML(stock);
		SerializableTimeSeries newStock = serializer.fromXML(serializedStock);
		assertEquals(stock, newStock);
		assertEquals(stock.getSeries(), newStock.getSeries());
	}

	@Test
	public void testStockAnalysisSerializer() throws FileNotFoundException, IOException {

		StockAnalysis stockAnalysis = new StockAnalysis(stock, applyedCriterion, slicer,new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());

		stockAnalysis.addCriterion(new MaximumDrawDownCriterion());
		stockAnalysis.addCriterion(new AverageProfitCriterion());
		stockAnalysis.addCriterion(new NumberOfTicksCriterion());

		
		String script = "(4..10).each{|numero| strategies.add(cross(ema(numero), close))}";

		stockAnalysis.addReport("", new RubyStrategiesSet(script,slicer));
		script = "(4..10).each{|numero|strategies.add(notSoFast(cross( parabolicSAR(numero), close), 3))}";

	
		stockAnalysis.addReport("", new RubyStrategiesSet(script,slicer));

		script = "(4..10).each{|numero|	strategies.add(notSoFast(cross(sma(numero), close), 3))}";

		stockAnalysis.addReport("", new RubyStrategiesSet(script,slicer));

		StockAnalysisSerializer serializer = new StockAnalysisSerializer();
		String xml = serializer.toXML(stockAnalysis);

		StockAnalysis afterStockAnalysis = serializer.fromXML(xml);

		assertEquals(stockAnalysis, afterStockAnalysis);
		assertEquals(stockAnalysis.getStock().getSeries(), afterStockAnalysis.getStock().getSeries());

	}
}
