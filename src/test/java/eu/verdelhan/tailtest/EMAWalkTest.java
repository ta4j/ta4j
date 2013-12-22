package net.sf.tail;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.sf.tail.analysis.criteria.AverageProfitCriterion;
import net.sf.tail.analysis.criteria.MaximumDrawDownCriterion;
import net.sf.tail.analysis.criteria.RewardRiskRatioCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.EMAIndicator;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.report.Report;
import net.sf.tail.report.html.ReportHTMLGenerator;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.series.FullyMemorizedSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class EMAWalkTest {

	public static void main(String[] args) throws Exception {

		// Petrobras by year
		CedroTimeSeriesLoader loader = new CedroTimeSeriesLoader();
		TimeSeries series = loader.load(new FileInputStream("BaseBovespa/diario/petr4Dia.csv"), "Petrobras Anual");
		// Generates a Set of EMAs, that trigger an ENTER/EXIT when
		// crossing the close price
		Set<Strategy> strategies = new LinkedHashSet<Strategy>();
		for (int i = 4; i < 20; i++) {
			Indicator<Double> close = new ClosePriceIndicator(series);
			Indicator<Double> tracker = new EMAIndicator(close, i);
			Strategy strategy = new IndicatorCrossedIndicatorStrategy(close, tracker);
			strategies.add(strategy);
		}

		// slice the series by each year, since 1999
		TimeSeriesSlicer slicer = new FullyMemorizedSlicer(series, new Period().withYears(1), new DateTime()
				.withYear(1999));

		// walks year by year, forgetting the past and generates the report in
		// /tmp/petr4
		Walker forward = new WalkForward(new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		Report report = forward.walk(new JavaStrategiesSet( strategies), slicer, new TotalProfitCriterion());
		ReportHTMLGenerator generator = new ReportHTMLGenerator();
		// talvez tenha de colocar outros criterions
		List<AnalysisCriterion> criterions = new ArrayList<AnalysisCriterion>();

		criterions.add(new AverageProfitCriterion());
		criterions.add(new MaximumDrawDownCriterion());
		criterions.add(new RewardRiskRatioCriterion());

		StringBuffer buffer = generator.generate(report, criterions, "");
		System.out.println(buffer);
	}
}
