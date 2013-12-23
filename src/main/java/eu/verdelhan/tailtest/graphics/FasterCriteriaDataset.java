
package eu.verdelhan.tailtest.graphics;

import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.tailtest.report.Report;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;

@SuppressWarnings("unchecked")
public class FasterCriteriaDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private List<Report> reports;

	private TimeSeries series;
	
	private final AnalysisCriterion totalProfit;	

	public FasterCriteriaDataset(TimeSeries series, List<Report> reports) {
		this.data = new DefaultKeyedValues2D();
		this.reports = reports;
		this.series = series;
		this.totalProfit = new TotalProfitCriterion();
				
		if(series.getSize() > 800)
			loadValuesFaster(series.getBegin(), series.getEnd());
		else
			loadValues(series.getBegin(), series.getEnd());
	}
	
	public FasterCriteriaDataset(TimeSeries series, List<Report> reports, boolean doFast) {
		this.data = new DefaultKeyedValues2D();
		this.reports = reports;
		this.series = series;
		this.totalProfit = new TotalProfitCriterion();
				
		if(doFast)
			loadValuesFaster(series.getBegin(), series.getEnd());
		else
			loadValues(series.getBegin(), series.getEnd());
	}

	private void loadValues(int firstIndex, int lastIndex) {
		
		for (Report report : reports) {
		 	for (int j = firstIndex; j <= lastIndex; j++) {
				data.addValue(totalProfit.calculate(series, report.getTradesUntilIndex(j)), report.getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(j).getDate()
						.toString("hh:mm d/M/yyyy"));
			}
		}
	}

	private void loadValuesFaster(int firstIndex, int lastIndex) {
		double firstValue = 0;
		double secondValue = 0;
		double thirdValue = 0;
		int counter = 0;
		int j;
		for (Report report : reports){
			for (j = firstIndex; j <= firstIndex + 2; j++) {
				data.addValue(totalProfit.calculate(series, report.getTradesUntilIndex(j)), report.getName() , series.getTick(j).getDate()
						.toString("hh:mm d/M/yyyy"));
			}
		}
		boolean drawPoint = false;
		for (j = firstIndex + 3; j < lastIndex; j++) {
			for (Report report : reports) {
				firstValue = totalProfit.calculate(series, report.getTradesUntilIndex(j - 2));
				secondValue = totalProfit.calculate(series, report.getTradesUntilIndex(j - 1));;
				thirdValue = totalProfit.calculate(series, report.getTradesUntilIndex(j));;
				if((firstValue > secondValue && thirdValue > secondValue) || (firstValue < secondValue && thirdValue < secondValue) || counter > 10){
					drawPoint = true;
					counter = 0;
					break;
				}
			}
			if(drawPoint) {
				for (Report report : reports) {
					data.addValue(totalProfit.calculate(series, report.getTradesUntilIndex(j - 1)), report.getName(), series.getTick(j-1).getDate()
							.toString("hh:mm d/M/yyyy"));
				}
				drawPoint = false;
			}
			
		}
				
		for (Report report : reports) {
			data.addValue(totalProfit.calculate(series, report.getTradesUntilIndex(lastIndex)), report.getName(), series.getTick(lastIndex).getDate()
					.toString("hh:mm d/M/yyyy"));
		}
	}

	public int getColumnIndex(Comparable key) {
		return this.data.getColumnIndex(key);
	}

	public Comparable getColumnKey(int column) {
		return this.data.getColumnKey(column);
	}

	public List getColumnKeys() {
		return this.data.getColumnKeys();
	}

	public int getRowIndex(Comparable key) {
		return this.data.getRowIndex(key);
	}

	public Comparable getRowKey(int row) {
		return this.data.getRowKey(row);
	}

	public List getRowKeys() {
		return this.data.getRowKeys();
	}

	public Number getValue(Comparable rowKey, Comparable columnKey) {
		return this.data.getValue(rowKey, columnKey);
	}

	public int getColumnCount() {
		return this.data.getColumnCount();
	}

	public int getRowCount() {
		return this.data.getRowCount();
	}

	public Number getValue(int row, int column) {
		return this.data.getValue(row, column);
	}
}