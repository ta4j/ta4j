package net.sf.tail.graphics;

import java.awt.Color;
import java.awt.Font;

import net.sf.tail.graphics.forms.ShapeFactory;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryMarker;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;

public class StockAndCashFlowChart implements DatasetChangeListener {

	private StockAndCashFlowDataset dataSet;

	private CategoryPlot plot;

	private LineAndShapeRenderer lineAndShapeRenderer;

	private boolean drawShapes;

	
	public StockAndCashFlowChart(StockAndCashFlowDataset dataSet, boolean drawShapes) {
		this.dataSet = dataSet;
		this.dataSet.addChangeListener(this);
		this.lineAndShapeRenderer = new LineAndShapeRenderer(true, true);
		this.drawShapes = drawShapes;
	}

	public JFreeChart createChart(String chartName) {
		JFreeChart jfreechart = ChartFactory.createLineChart(chartName, "Date", "Value", dataSet,
				PlotOrientation.VERTICAL, true, true, false);

		plot = (CategoryPlot) jfreechart.getPlot();
		plot.setBackgroundPaint(Color.WHITE);
		
		// Setando labels em 90 graus
		CategoryAxis categoryAxis = plot.getDomainAxis();
		categoryAxis.setVisible(false);

		// Setando tamanho do grafico
		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setAutoRangeIncludesZero(false);

		// Setando tamanho do label
		categoryAxis.setLabelFont(new Font("Arial", 0, 10));

		// Setando tamanho do label de cada tick
		categoryAxis.setTickLabelFont(new Font("Arial", 0, 10));

		// gerando setas
		setSeriesShapes();

		return jfreechart;
	}

	/**
	 * @param dataSet
	 * @param plot
	 */
	private void setSeriesShapes() {
		if (drawShapes) {
			for (int i = 0; i < dataSet.getRowCount(); i++) {

				if (dataSet.getRowKey(i).equals("Buy"))
					lineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getDownArrow());
				else if (dataSet.getRowKey(i).equals("Sell"))
					lineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getUpperArrow());
				else
					lineAndShapeRenderer.setSeriesShape(i, ShapeFactory.getPoint());
			}
		} 
		else {
			lineAndShapeRenderer.setSeriesShapesVisible(0, false);
			lineAndShapeRenderer.setSeriesShapesVisible(1, false);
			Integer sliceNumber = 1;
			for (Integer index : dataSet.getValueMarkers()) {
				CategoryMarker marker = new CategoryMarker(dataSet.getColumnKey(index));
				marker.setPaint(Color.GRAY);
				marker.setLabel(sliceNumber.toString());
				marker.setLabelPaint(Color.GRAY);
				sliceNumber++;
					
				plot.addDomainMarker(marker);
			}
		}
		
		plot.setRenderer(lineAndShapeRenderer);
	}

	public void datasetChanged(DatasetChangeEvent event) {
		setSeriesShapes();
	}
}
