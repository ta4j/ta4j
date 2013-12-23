package eu.verdelhan.tailtest.graphics;

import java.awt.Color;
import java.awt.Font;

import eu.verdelhan.tailtest.graphics.forms.ShapeFactory;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;

public class CombinedSeriesAndFlowChart implements DatasetChangeListener {

	private CategoryDataset seriesDataSet;

	private CategoryDataset flowDataSet;

	private CategoryPlot seriesPlot;

	private CategoryPlot flowPlot;

	private LineAndShapeRenderer seriesLineAndShapeRenderer;

	private LineAndShapeRenderer flowLineAndShapeRenderer;

	public CombinedSeriesAndFlowChart(CategoryDataset seriesDataSet, CategoryDataset flowDataSet) {
		this.seriesDataSet = seriesDataSet;
		this.flowDataSet = flowDataSet;
		this.seriesDataSet.addChangeListener(this);
		this.flowDataSet.addChangeListener(this);
		this.seriesLineAndShapeRenderer = new LineAndShapeRenderer(true, true);
		this.flowLineAndShapeRenderer = new LineAndShapeRenderer(true, true);
	}

	public JFreeChart createChart(String chartName) {
		NumberAxis seriesNumberAxis = new NumberAxis("Stock Value");
		seriesNumberAxis.setAutoRangeIncludesZero(false);
		seriesPlot = new CategoryPlot(seriesDataSet, null, seriesNumberAxis, seriesLineAndShapeRenderer);
		seriesPlot.setBackgroundPaint(Color.WHITE);

		NumberAxis flowNumberAxis = new NumberAxis("Money Amount");
		flowNumberAxis.setAutoRangeIncludesZero(false);
		flowPlot = new CategoryPlot(flowDataSet, null, flowNumberAxis, flowLineAndShapeRenderer);
		flowPlot.setBackgroundPaint(Color.WHITE);

		CombinedDomainCategoryPlot combinedplot = new CombinedDomainCategoryPlot();
		combinedplot.setGap(10D);
		combinedplot.add(seriesPlot);
		combinedplot.add(flowPlot);
		combinedplot.setOrientation(PlotOrientation.VERTICAL);
		JFreeChart jfreechart = new JFreeChart("Combined Series and Flow Chart", JFreeChart.DEFAULT_TITLE_FONT,
				combinedplot, true);

		// Setando labels em 90 graus
		CategoryAxis categoryAxis = seriesPlot.getDomainAxis();
		categoryAxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_90);

		// Setando tamanho do grafico
		NumberAxis rangeAxis = (NumberAxis) seriesPlot.getRangeAxis();
		rangeAxis.setAutoRangeIncludesZero(false);

		// Setando tamanho do label
		categoryAxis.setLabelFont(new Font("SansSerif", 0, 10));

		// Setando tamanho do label de cada tick
		categoryAxis.setTickLabelFont(new Font("SansSerif", 0, 10));

		// gerando pontos
		setSeriesShapes();

		return jfreechart;
	}

	/**
	 * @param dataSet
	 * @param plot
	 */
	private void setSeriesShapes() {

		for (int i = 0; i < seriesDataSet.getRowCount(); i++) {
			if (seriesDataSet.getRowKey(i).equals("Buy"))
				seriesLineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getDownArrow());
			else if (seriesDataSet.getRowKey(i).equals("Sell"))
				seriesLineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getUpperArrow());
			else
				seriesLineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getPoint());
		}
		for (int i = 0; i < flowDataSet.getRowCount(); i++) {
			flowLineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getPoint());
		}
		seriesPlot.setRenderer(seriesLineAndShapeRenderer);
		flowPlot.setRenderer(flowLineAndShapeRenderer);
	}

	public void datasetChanged(DatasetChangeEvent event) {
		setSeriesShapes();
	}
}
