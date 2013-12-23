package eu.verdelhan.tailtest.graphics;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

import javax.swing.JPanel;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.flow.CashFlow;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.indicator.tracker.SMAIndicator;
import eu.verdelhan.tailtest.io.reader.CedroTimeSeriesLoader;
import eu.verdelhan.tailtest.runner.HistoryRunner;
import eu.verdelhan.tailtest.series.RegularSlicer;
import eu.verdelhan.tailtest.strategy.IndicatorCrossedIndicatorStrategy;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.joda.time.Period;

public class StockAndCashFlowChartTest extends ApplicationFrame {

	private static final long serialVersionUID = -6626066873257820845L;

	private StockAndCashFlowDataset seriesDataset;

	private Indicator<? extends Number> indicator;

	private CashFlow cashFlow;

	private final TimeSeries timeSeries;

	public StockAndCashFlowChartTest(String chartName) {
		super(chartName);

		timeSeries = readFileData();

		JPanel chartPanel = createSmaAndStrategyChart();
		chartPanel.setPreferredSize(new Dimension(800, 600));

		JPanel mainPanel = new JPanel(new FlowLayout());
		mainPanel.add(chartPanel, "North");

		mainPanel.setPreferredSize(new Dimension(1280, 1024));

		setContentPane(mainPanel);
	}

	public static void main(String args[]) {
		StockAndCashFlowChartTest chart = new StockAndCashFlowChartTest("Chart test");
		chart.pack();
		RefineryUtilities.centerFrameOnScreen(chart);
		chart.setVisible(true);
	}

	public JPanel createSmaAndStrategyChart() {
		indicator = new ClosePriceIndicator(timeSeries);
		SMAIndicator sma = new SMAIndicator(indicator, 8);

		TimeSeriesSlicer slicer = new RegularSlicer(timeSeries,new Period().withYears(2000));
		
		List<Trade> trades = new HistoryRunner(slicer,new IndicatorCrossedIndicatorStrategy(indicator,
				sma)).run(0);
		cashFlow = new CashFlow(timeSeries, trades);

		seriesDataset = new StockAndCashFlowDataset(timeSeries, indicator, cashFlow, new Period().withMonths(6));

		StockAndCashFlowChart seriesAndFlowChart = new StockAndCashFlowChart(seriesDataset, false);
		JFreeChart jfreechart = seriesAndFlowChart.createChart("Test Chart");

		return new ChartPanel(jfreechart);
	}

	/**
	 * @return
	 */
	private TimeSeries readFileData() {
		CedroTimeSeriesLoader ctsl = new CedroTimeSeriesLoader();
		TimeSeries timeSeries = null;
		try {
			timeSeries = ctsl.load(new FileInputStream("BaseBovespa/diario/aces4Dia.csv"), "");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return timeSeries;
	}
}
