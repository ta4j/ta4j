
package net.sf.tail.graphics;

import java.util.Collections;
import java.util.List;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.simple.ClosePriceIndicator;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;


@SuppressWarnings("unchecked")
public class FasterDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private List<Indicator<? extends Number>> indicators;

	private TimeSeries series;

	public FasterDataset(TimeSeries series, List<Indicator<? extends Number>> indicators) {
		this.data = new DefaultKeyedValues2D();
		this.indicators = indicators;
		this.series = series;
				
		if(series.getSize() > 800)
			loadValuesFaster(series.getBegin(), series.getEnd());
		else
			loadValues(series.getBegin(), series.getEnd());
	}

	public FasterDataset(TimeSeries timeSeries) {
		this(timeSeries, Collections.<Indicator<? extends Number>>nCopies(1, new ClosePriceIndicator(timeSeries)));
	}
	public FasterDataset(TimeSeries series, boolean doFast) {
		this.data = new DefaultKeyedValues2D();
		this.indicators = Collections.<Indicator<? extends Number>>nCopies(1, new ClosePriceIndicator(series));
		this.series = series;
		if(doFast)
			loadValuesFaster(series.getBegin(), series.getEnd());
		else
			loadValues(series.getBegin(), series.getEnd());
	}


	private void loadValues(int firstIndex, int lastIndex) {
		
		for (Indicator<? extends Number> indicator : indicators) {
		 	for (int j = firstIndex; j <= lastIndex; j++) {
				data.addValue(indicator.getValue(j), indicator.getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(j).getDate()
						.toString("hh:mm d/M/yyyy"));
			}
		}
	}

	private void loadValuesFaster(int firstIndex, int lastIndex) {
		double firstValue;
		double secondValue;
		double thirdValue;
		int counter = 0;
		
		for (Indicator<? extends Number> indicator : indicators){
			for (int j = firstIndex; j <= firstIndex + 2; j++) {
				data.addValue(indicator.getValue(j), indicator.getName() + ": "
						+ series.getTick(series.getBegin()).getDateName() + " - "
						+ series.getTick(series.getEnd()).getDateName(), series.getTick(j).getDate().toString(
						"hh:mm d/M/yyyy"));
			}
		}
		
		for (Indicator<? extends Number> indicator : indicators) {
			for (int j = firstIndex + 3; j < lastIndex; j++) {
				
				firstValue = indicator.getValue(j - 2).doubleValue();
				secondValue = indicator.getValue(j - 1).doubleValue();
				thirdValue = indicator.getValue(j).doubleValue();
				
				if((firstValue > secondValue && thirdValue > secondValue) || (firstValue < secondValue && thirdValue < secondValue) || counter > 10){
					data.addValue(secondValue, indicator.getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(j-1).getDate()
						.toString("hh:mm d/M/yyyy"));
					counter = 0;	
				}
			}
		}
		
		for (Indicator<? extends Number> indicator : indicators) {
			data.addValue(indicator.getValue(lastIndex), indicator.getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(lastIndex).getDate()
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