
package net.sf.tail.graphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.tail.Indicator;
import net.sf.tail.Operation;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.indicator.simple.ClosePriceIndicator;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;


@SuppressWarnings("unchecked")
public class SeriesDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private final int DATASET_SIZE;

	private List<Indicator<? extends Number>> indicators;

	private List<Trade> trades;

	private int firstIndex;

	private int lastIndex;

	private TimeSeries series;

	public SeriesDataset(TimeSeries series, List<Indicator<? extends Number>> indicators, int firstIndex, int lastIndex) {
		this.data = new DefaultKeyedValues2D();
		this.indicators = indicators;
		this.firstIndex = firstIndex;
		this.lastIndex = lastIndex;
		this.series = series;
		this.DATASET_SIZE = lastIndex - firstIndex + 1;
		this.trades = new ArrayList<Trade>();

		loadValues(firstIndex, lastIndex);
	}

	public SeriesDataset(TimeSeries timeSeries, int seriesSize) {
		this(timeSeries, Collections.<Indicator<? extends Number>>nCopies(1, new ClosePriceIndicator(timeSeries)), timeSeries.getBegin(), timeSeries.getBegin() + seriesSize - 1);
	}
	
	public SeriesDataset(TimeSeries timeSeries, List<Indicator<? extends Number>> indicators, List<Trade> trades) {
		this(timeSeries, indicators, timeSeries.getBegin(), timeSeries.getEnd(), trades);
	}
	
	public SeriesDataset(TimeSeries timeSeries, List<Indicator<? extends Number>> indicators, int firstIndex,
			int lastIndex, List<Trade> trades) {

		this(timeSeries, indicators, firstIndex, lastIndex);
		this.trades = trades;

		loadTrade(firstIndex, lastIndex);
	}

	private void loadTrade(int firstIndex, int lastIndex) {
		for (Trade trade : trades) {

			Operation buy = trade.getEntry();
			if (buy.getIndex() >= firstIndex && buy.getIndex() <= lastIndex)
				data.addValue(series.getTick(buy.getIndex()).getClosePrice(), "Buy", series.getTick(buy.getIndex())
						.getDate().toString("hh:mm d/M/yyyy"));
			Operation sell = trade.getExit();
			if (sell.getIndex() >= firstIndex && sell.getIndex() <= lastIndex)
				data.addValue(series.getTick(sell.getIndex()).getClosePrice(), "Sell", series.getTick(sell.getIndex())
						.getDate().toString("hh:mm d/M/yyyy"));
		}
	}

	private void loadValues(int firstIndex, int lastIndex) {
		data = new DefaultKeyedValues2D();
		for (int i = 0; i < indicators.size(); i++) {
			for (int j = firstIndex; j <= lastIndex; j++) {
				data.addValue(indicators.get(i).getValue(j), indicators.get(i).getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(j).getDate()
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
				for (Indicator<? extends Number> indicator : indicators) {
					data.addValue(indicator.getValue(lastIndex), indicator.getName() + ": "+ series.getTick(series.getBegin()).getDateName() + " - " + series.getTick(series.getEnd()).getDateName(), series.getTick(lastIndex).getDate()
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
			if(trades.size() > 0)
			{
				loadTrade(firstIndex, lastIndex);
			}
			fireDatasetChanged();
		}
	}

	public void moveLeft(int size) {
		if (firstIndex > 1) {
			int steps = firstIndex - 1;
			if (steps > size)
			{
				steps = size;
			}
			firstIndex -= steps;
			lastIndex -= steps;

			loadValues(firstIndex, lastIndex);
			if(trades.size() > 0)
			{
				loadTrade(firstIndex, lastIndex);
			}

			fireDatasetChanged();
		}
	}
}