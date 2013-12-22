package net.sf.tail.graphics;

import java.awt.Color;
import java.awt.Font;

import net.sf.tail.graphics.forms.ShapeFactory;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;

public class CriteriaChart implements DatasetChangeListener {

	private CategoryDataset dataSet;

	private CategoryPlot plot;

	private LineAndShapeRenderer lineAndShapeRenderer;

	public CriteriaChart(CategoryDataset dataSet) {
		this.dataSet = dataSet;
		this.dataSet.addChangeListener(this);
		this.lineAndShapeRenderer = new LineAndShapeRenderer(true, true);
	}

	public JFreeChart createChart(String chartName, boolean printAll) {
		JFreeChart jfreechart = ChartFactory.createLineChart(chartName, "Date", "Value", dataSet,
				PlotOrientation.VERTICAL, true, true, false);

		plot = (CategoryPlot) jfreechart.getPlot();
		plot.setBackgroundPaint(Color.WHITE);

		// Setando labels em 90 graus
		CategoryAxis categoryAxis = plot.getDomainAxis();
		categoryAxis.setVisible(printAll);
		categoryAxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_90);

		// Setando tamanho do grafico
		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setAutoRangeIncludesZero(false);

		// Setando tamanho do label
		categoryAxis.setLabelFont(new Font("SansSerif", 0, 10));

		// Setando tamanho do label de cada tick
		categoryAxis.setTickLabelFont(new Font("SansSerif", 0, 8));

		if(printAll) {
			// gerando setas
			setSeriesShapes();
		}
		return jfreechart;
	}

	/**
	 * @param dataSet
	 * @param plot
	 */
	private void setSeriesShapes() {

		for (int i = 0; i < dataSet.getRowCount(); i++) {
			lineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getPoint());
		}
		plot.setRenderer(lineAndShapeRenderer);
	}

	public void datasetChanged(DatasetChangeEvent event) {
		setSeriesShapes();
	}
}
