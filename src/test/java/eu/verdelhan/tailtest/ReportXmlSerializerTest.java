package net.sf.tail;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.EMAIndicator;
import net.sf.tail.io.ReportSerializer;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.report.Report;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;

import org.joda.time.Period;

public class ReportXmlSerializerTest {

	public void testXMLSerialize() {

		CedroTimeSeriesLoader ctsl = new CedroTimeSeriesLoader();
		TimeSeries timeSeries = null;

		try {
			timeSeries = ctsl.load(new FileInputStream("BaseBovespa/15min/ambv4.csv"), "AMBV4 IntraDAY");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		TimeSeriesSlicer slicer = new RegularSlicer(timeSeries, new Period().withDays(1));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		
		

		Set<Strategy> strategies = new HashSet<Strategy>();

		for (int i = 4; i < 20; i++) {
			Indicator<Double> close = new ClosePriceIndicator(timeSeries);
			Indicator<Double> tracker = new EMAIndicator(close, i);
			Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
			strategies.add(strategy);
		}

		Walker w = new WalkForward(new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		Report r = w.walk(new JavaStrategiesSet( strategies), slicer, criterion);
		ReportSerializer serializer = new ReportSerializer();
		String xml = serializer.toXML(r);

		try {
			FileOutputStream fos = new FileOutputStream("xml/ReportTest.xml");
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
		ReportXmlSerializerTest xmlSerializer = new ReportXmlSerializerTest();
		xmlSerializer.testXMLSerialize();
	}
}
