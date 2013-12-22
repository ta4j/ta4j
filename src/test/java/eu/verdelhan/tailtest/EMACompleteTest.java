package net.sf.tail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sf.tail.analysis.criteria.MaximumDrawDownCriterion;
import net.sf.tail.analysis.criteria.NumberOfTicksCriterion;
import net.sf.tail.analysis.criteria.NumberOfTradesCriterion;
import net.sf.tail.analysis.criteria.RewardRiskRatioCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.flow.CashFlow;
import net.sf.tail.graphics.StockAndCashFlowChart;
import net.sf.tail.graphics.StockAndCashFlowDataset;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.EMAIndicator;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.report.Report;
import net.sf.tail.report.html.ReportHTMLGenerator;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.series.FullyMemorizedSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.joda.time.Period;

public class EMACompleteTest {

	public void testCompleteSMAGenerate() throws IOException {
		CedroTimeSeriesLoader ctsl = new CedroTimeSeriesLoader();
		TimeSeries series = ctsl.load(new FileInputStream("BaseBovespa/15min/petr4_15min_05102007.csv"), "Ambev (ambv4)");
		
		Set<Strategy> strategies = new HashSet<Strategy>();
		Indicator<Double> close = new ClosePriceIndicator(series);
		for (int i = 4; i < 20; i++) {
			Indicator<Double> tracker = new EMAIndicator(close, i);
			Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
			strategies.add(strategy);
		}

		
		TimeSeriesSlicer slicer = new FullyMemorizedSlicer(series, new Period().withMonths(1));
		AnalysisCriterion criterion = new TotalProfitCriterion();
		
		Walker w = new WalkForward(new HigherValueEvaluatorFactory(),new HistoryRunnerFactory());
		Report r = w.walk(new JavaStrategiesSet(strategies)  ,slicer, criterion);
		List<AnalysisCriterion> criteria = new LinkedList<AnalysisCriterion>();
		criteria.add(new NumberOfTradesCriterion());
		criteria.add(new NumberOfTicksCriterion());
		criteria.add(new MaximumDrawDownCriterion());
		criteria.add(new RewardRiskRatioCriterion());

		CashFlow cashflow = new CashFlow(series, r.getAllTrades());
		StockAndCashFlowDataset stockData = new StockAndCashFlowDataset(series, close, cashflow, new Period()
				.withMonths(1));
		StockAndCashFlowChart stockChart = new StockAndCashFlowChart(stockData, false);
		JFreeChart jfreechart = stockChart.createChart("");

		ChartUtilities.saveChartAsPNG(new File("src/templates/ambev.png"), jfreechart, 800, 300);

		StringBuffer html = new ReportHTMLGenerator().generate(r, criteria, "ambev.png");
		File reportHtml = new File("src/templates/report.html");
		OutputStream out = new BufferedOutputStream(new FileOutputStream(reportHtml));
		PrintWriter write = new PrintWriter(out);
		write.print(html);
		write.close();
	}

	public static void main(String[] args) {
		EMACompleteTest emaTest = new EMACompleteTest();
		try {
			emaTest.testCompleteSMAGenerate();
		} catch (IOException e) {
		}
	}

}
