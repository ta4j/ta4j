package net.sf.tail.graphics;

import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.flow.CashFlow;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;

@SuppressWarnings("unchecked")
public class CashFlowDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private final int DATASET_SIZE;

	private CashFlow cashFlow;

	private int firstIndex;

	private int lastIndex;

	private String name;

	private TimeSeries series;

	public CashFlowDataset(TimeSeries series, CashFlow cashFlow, int firstIndex, int lastIndex) {
		this.data = new DefaultKeyedValues2D();
		this.cashFlow = cashFlow;
		this.firstIndex = firstIndex;
		this.lastIndex = lastIndex;
		this.series = series;
		this.DATASET_SIZE = lastIndex - firstIndex;
		this.name = "Money Amount";

		loadValues(firstIndex, lastIndex);
	}

	private void loadValues(int firstIndex, int lastIndex) {
		data = new DefaultKeyedValues2D();
		for (int j = firstIndex; j <= lastIndex; j++) {
			data.addValue(cashFlow.getValue(j), this.getName(), series.getTick(j).getDate().toString("hh:mm d/M/yyyy"));
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

	public void moveRight() {

		if (lastIndex + 1 < series.getSize()) {
			lastIndex++;

			data.addValue(cashFlow.getValue(lastIndex), this.getName(), series.getTick(lastIndex).getDate().toString(
					"hh:mm d/M/yyyy"));

			if (data.getColumnCount() >= DATASET_SIZE) {
				firstIndex++;
				data.removeColumn(0);
			}
			fireDatasetChanged();
		}
	}

	public void moveLeft() {
		if (firstIndex > 0) {
			firstIndex--;
			lastIndex--;

			loadValues(firstIndex, lastIndex);

			fireDatasetChanged();
		}
	}

	public String getName() {
		return name;
	}
}
