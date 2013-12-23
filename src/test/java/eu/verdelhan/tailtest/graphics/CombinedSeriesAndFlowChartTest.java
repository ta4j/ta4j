package eu.verdelhan.tailtest.graphics;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
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

public class CombinedSeriesAndFlowChartTest extends ApplicationFrame implements ActionListener {

	private static final long serialVersionUID = -6626066873257820845L;

	private SeriesDataset seriesDataset;

	private CashFlowDataset flowDataset;

	private final List<Indicator<? extends Number>> indicators;

	private CashFlow cashFlow;

	private final TimeSeries timeSeries;

	private int seriesEndIndex = 50;

	public CombinedSeriesAndFlowChartTest(String chartName) {
		super(chartName);

		indicators = new ArrayList<Indicator<? extends Number>>();
		timeSeries = readFileData();

		JButton button2 = new JButton("Move Left");
		button2.setActionCommand("Move Left");
		button2.addActionListener(this);

		JButton button = new JButton("Move Right");
		button.setActionCommand("Move Right");
		button.addActionListener(this);

		JPanel chartPanel = createSmaAndStrategyChart();
		chartPanel.setPreferredSize(new Dimension(800, 600));

		JPanel mainPanel = new JPanel(new FlowLayout());
		mainPanel.add(button2, "South");
		mainPanel.add(button, "South");
		mainPanel.add(chartPanel, "North");

		mainPanel.setPreferredSize(new Dimension(1280, 1024));

		setContentPane(mainPanel);
	}

	public static void main(String args[]) {
		CombinedSeriesAndFlowChartTest chart = new CombinedSeriesAndFlowChartTest("Chart test");
		chart.pack();
		RefineryUtilities.centerFrameOnScreen(chart);
		chart.setVisible(true);
	}

	public JPanel createSmaAndStrategyChart() {
		ClosePriceIndicator close = new ClosePriceIndicator(timeSeries);
		SMAIndicator sma = new SMAIndicator(close, 8);

		indicators.add(close);
		indicators.add(sma);

		TimeSeriesSlicer slicer = new RegularSlicer(timeSeries,new Period().withYears(2000));
		
		List<Trade> trades = new HistoryRunner(slicer,new IndicatorCrossedIndicatorStrategy(close, sma)).run(0);
		cashFlow = new CashFlow(timeSeries, trades);

		seriesDataset = new SeriesDataset(timeSeries, indicators, 0, seriesEndIndex, trades);
		flowDataset = new CashFlowDataset(timeSeries, cashFlow, 0, seriesEndIndex);

		CombinedSeriesAndFlowChart seriesAndFlowChart = new CombinedSeriesAndFlowChart(seriesDataset, flowDataset);
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
			timeSeries = ctsl.load(new FileInputStream("BaseBovespa/15min/ambv4.csv"), "");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return timeSeries;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("Move Right")) {
			flowDataset.moveRight();
			seriesDataset.moveRight(1);
		} else if (e.getActionCommand().equals("Move Left")) {
			flowDataset.moveLeft();
			seriesDataset.moveLeft(1);
		}
	}
}
