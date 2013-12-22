package net.sf.tail;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

import net.sf.tail.analysis.StockAnalysis;
import net.sf.tail.analysis.criteria.AverageProfitCriterion;
import net.sf.tail.analysis.criteria.MaximumDrawDownCriterion;
import net.sf.tail.analysis.criteria.NumberOfTicksCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.evaluator.StrategyEvaluatorFactory;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.EMAIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;
import net.sf.tail.indicator.tracker.WilliamsRIndicator;
import net.sf.tail.io.StockAnalysisSerializer;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.series.FullyMemorizedSlicer;
import net.sf.tail.series.SerializableTimeSeries;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;

import org.joda.time.Period;

public class StockAnalysisXmlSerializerTest {

	public void testXMLSerialize() {
		try {
			SerializableTimeSeries stock = new SerializableTimeSeries("test", "BaseBovespa/15min/ambv4.csv", new CedroTimeSeriesLoader());

			AnalysisCriterion applyedCriterion = new TotalProfitCriterion();

			TimeSeriesSlicer slicer = new FullyMemorizedSlicer(stock, new Period().withDays(1));
			

			

			Set<Strategy> strategies = new HashSet<Strategy>();

			for (int i = 4; i < 20; i++) {
				Indicator<Double> close = new ClosePriceIndicator(stock);
				Indicator<Double> tracker = new EMAIndicator(close, i);
				Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
				strategies.add(strategy);
			}
			StrategyEvaluatorFactory evaluator = new HigherValueEvaluatorFactory();
			StockAnalysis stockAnalysis = new StockAnalysis(stock, applyedCriterion, slicer, evaluator, new HistoryRunnerFactory());

			stockAnalysis.addCriterion(new MaximumDrawDownCriterion());
			stockAnalysis.addCriterion(new AverageProfitCriterion());
			stockAnalysis.addCriterion(new NumberOfTicksCriterion());
			stockAnalysis.addReport("", new JavaStrategiesSet( strategies));
			

			strategies = new HashSet<Strategy>();

			for (int i = 4; i < 20; i++) {
				Indicator<Double> close = new ClosePriceIndicator(stock);
				Indicator<Double> tracker = new SMAIndicator(close, i);
				Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
				strategies.add(strategy);
			}

			stockAnalysis.addReport("", new JavaStrategiesSet( strategies));

			strategies = new HashSet<Strategy>();

			for (int i = 4; i < 20; i++) {
				Indicator<Double> close = new ClosePriceIndicator(stock);
				Indicator<Double> tracker = new WilliamsRIndicator(stock, i);
				Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
				strategies.add(strategy);
			}

			stockAnalysis.addReport("", new JavaStrategiesSet( strategies));

			StockAnalysisSerializer serializer = new StockAnalysisSerializer();
			String xml = serializer.toXML(stockAnalysis);

			FileOutputStream fos = new FileOutputStream("xml/StockAnalysisTest.xml");
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			BufferedWriter bw = new BufferedWriter(osw);
			bw.write(xml);
			bw.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		StockAnalysisXmlSerializerTest xmlSerializer = new StockAnalysisXmlSerializerTest();
		xmlSerializer.testXMLSerialize();
	}
}
