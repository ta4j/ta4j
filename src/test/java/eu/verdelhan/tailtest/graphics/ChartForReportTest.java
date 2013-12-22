package net.sf.tail.graphics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.runner.HistoryRunner;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategy.IndicatorCrossedIndicatorStrategy;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.ui.ApplicationFrame;
import org.joda.time.Period;

public class ChartForReportTest extends ApplicationFrame {

	private static final long serialVersionUID = -6626066873257820845L;

	private SeriesDataset seriesDataset;

	private final List<Indicator<? extends Number>> indicators;

	private final TimeSeries timeSeries;

	private int chartSize = 50;

	public ChartForReportTest(String chartName) {
		super(chartName);

		indicators = new ArrayList<Indicator<? extends Number>>();
		timeSeries = readFileData();

		File htmlChart = new File("reports/reportWithChart.html");

		try {
			OutputStream out = new BufferedOutputStream(new FileOutputStream(htmlChart));
			PrintWriter writer = new PrintWriter(out);
			writer.println("<html>");
			writer.println("<head><title>" + chartName + "</title></head>");
			writer.println("<body><table align=\"center\">");
			for (String fileName : createSmaAndStrategyChartsInPNGFile()) {
				writer.println("<tr><img src=\"PNGCharts/" + fileName + "\" width=\"800\" height=\"600\"></tr>");
			}
			writer.println("</table></body>");
			writer.println("</html>");
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {
		new ChartForReportTest("SMA Chart");
	}

	public List<String> createSmaAndStrategyChartsInPNGFile() {
		List<String> fileNames = new ArrayList<String>();
		ClosePriceIndicator close = new ClosePriceIndicator(timeSeries);
		SMAIndicator sma = new SMAIndicator(close, 8);

		indicators.add(close);
		indicators.add(sma);

		TimeSeriesSlicer slicer = new RegularSlicer(timeSeries,new Period().withYears(2000));
		
		List<Trade> trades = new HistoryRunner(slicer,new IndicatorCrossedIndicatorStrategy(close, sma)).run(0);

		// for (int i = timeSeries.getBegin(); i < timeSeries.getEnd(); i = i +
		// chartSize)
		for (int i = timeSeries.getBegin(); i < 200; i = i + chartSize) {
			if (i + chartSize > timeSeries.getEnd()) {
				seriesDataset = new SeriesDataset(timeSeries, indicators, i, timeSeries.getEnd(), trades);
			} else {
				seriesDataset = new SeriesDataset(timeSeries, indicators, i, i + chartSize, trades);
			}

			SeriesChart seriesChart = new SeriesChart(seriesDataset);
			JFreeChart jfreechart = seriesChart.createChart("Report Chart", false, true);
			String fileName = "chartTeste00" + i + ".png";
			File filePNG = new File("reports/PNGCharts/" + fileName);
			fileNames.add(fileName);
			try {
				ChartUtilities.saveChartAsPNG(filePNG, jfreechart, 800, 600);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return fileNames;
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
}
