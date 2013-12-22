package net.sf.tail.graphics;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.Indicator;
import net.sf.tail.Operation;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.flow.CashFlow;

import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.AbstractDataset;
import org.joda.time.DateTime;
import org.joda.time.Period;

@SuppressWarnings("unchecked")
public class StockAndCashFlowDataset extends AbstractDataset implements CategoryDataset {

	private static final long serialVersionUID = -7204964027452086107L;

	private DefaultKeyedValues2D data;

	private Indicator<? extends Number> closePrice;

	private CashFlow cashFlow;

	private int firstIndex;

	private int lastIndex;

	private TimeSeries series;

	private Period period;

	private List<Integer> valueMarkers;

	private List<Trade> trades;

	public StockAndCashFlowDataset(TimeSeries series, Indicator<? extends Number> closePrice, CashFlow cashFlow,
			int firstIndex, int lastIndex, Period period) {
		this.data = new DefaultKeyedValues2D();
		this.closePrice = closePrice;
		this.cashFlow = cashFlow;
		this.firstIndex = firstIndex;
		this.lastIndex = lastIndex;
		this.series = series;
		this.period = period;
		this.valueMarkers = new ArrayList<Integer>();

		loadValues();
	}

	public StockAndCashFlowDataset(TimeSeries series, Indicator<? extends Number> closePrice, CashFlow cashFlow,
			int firstIndex, int lastIndex) {
		this(series, closePrice, cashFlow, firstIndex, lastIndex, null);
	}

	public StockAndCashFlowDataset(TimeSeries series, Indicator<? extends Number> closePrice, CashFlow cashFlow,
			Period period) {
		this(series, closePrice, cashFlow, series.getBegin(), series.getEnd(), period);

	}
	
	public StockAndCashFlowDataset(TimeSeries series, Indicator<? extends Number> closePrice, CashFlow cashFlow,
			List<Trade> trades) {
		this(series, closePrice, cashFlow, series.getBegin(), series.getEnd());
		this.trades = trades;
		loadTrade(series.getBegin(), series.getEnd());

	}

	public StockAndCashFlowDataset(TimeSeries series, Indicator<? extends Number> closePrice, CashFlow cashFlow) {
		this(series, closePrice, cashFlow, series.getBegin(), series.getEnd(), null);

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
	
	private void loadValues() {
		data = new DefaultKeyedValues2D();
		double firstValue = closePrice.getValue(firstIndex).doubleValue();
		for (int i = firstIndex; i <= lastIndex; i++) {
			data.addValue(closePrice.getValue(i).doubleValue() / firstValue, closePrice.getName(), series.getTick(i)
					.getDate().toString("hh:mm d/M/yyyy"));
			data.addValue(cashFlow.getValue(i), "Money Amount", series.getTick(i).getDate().toString("hh:mm d/M/yyyy"));
			
		}

		if (period != null) {
			DateTime nextDateTime = series.getTick(series.getBegin()).getDate().plus(period);
			for (int i = firstIndex; i <= lastIndex; i++) {
				
				if (series.getTick(i).getDate().compareTo(nextDateTime) >= 0) {
					valueMarkers.add(i - series.getBegin());
					nextDateTime = nextDateTime.plus(period);
				}
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

	public List<Integer> getValueMarkers() {
		return valueMarkers;
	}
}
