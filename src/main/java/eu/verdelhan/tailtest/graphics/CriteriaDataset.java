package net.sf.tail.graphics;

import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.TimeSeries;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.report.Report;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;

@SuppressWarnings("unchecked")
public class CriteriaDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private final int DATASET_SIZE;

	private List<Report> reports;

	private int firstIndex;

	private int lastIndex;

	private TimeSeries series;
	
	private final AnalysisCriterion totalProfit;

	public CriteriaDataset(List<Report> reports, TimeSeries series, int datasetSize) {
		this.reports = reports;
		this.series = series;
		this.DATASET_SIZE = datasetSize;
		this.firstIndex = series.getBegin();
		this.lastIndex = series.getBegin() + DATASET_SIZE - 1;
		this.totalProfit = new TotalProfitCriterion();

		loadValues(firstIndex, lastIndex);
	}

	private void loadValues(int firstIndex, int lastIndex) {
		this.data = new DefaultKeyedValues2D();
		for (int i = 0; i < reports.size(); i++) {
			for (int j = firstIndex; j <= lastIndex; j++) {
				data.addValue(totalProfit.calculate(series, reports.get(i).getTradesUntilIndex(j)), reports.get(i).getName(), series.getTick(j).getDate()
						.toString("hh:mm d/M/yyyy"));
			}
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


	public void moveRight(int size) {
		if (lastIndex + 1 < series.getSize()) {
			int steps = series.getSize() - lastIndex - 1;
			if (steps > size)
			{
				steps = size;
			}
			
			for (int i = 0; i < steps; i++)
			{
				lastIndex ++;
				for (Report report : reports) {
					data.addValue(totalProfit.calculate(series, report.getTradesUntilIndex(lastIndex)), report.getName(), series.getTick(lastIndex).getDate()
							.toString("hh:mm d/M/yyyy"));
				}
			}

			if (data.getColumnCount() >= DATASET_SIZE) {
				firstIndex += steps;
				for (int i = 0; i < steps; i++)
				{
					data.removeColumn(0);
				}
			}
			
			fireDatasetChanged();
		}
	}

	public void moveLeft(int size) {
		if (firstIndex > 0) {
			int steps = firstIndex;
			if (steps > size)
			{
				steps = size;
			}
			firstIndex -= steps;
			lastIndex -= steps;

			loadValues(firstIndex, lastIndex);
			

			fireDatasetChanged();
		}
	}
}
