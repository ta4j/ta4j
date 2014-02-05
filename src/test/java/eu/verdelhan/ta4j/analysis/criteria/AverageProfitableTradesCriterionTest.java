package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import eu.verdelhan.ta4j.mocks.MockDecision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
public class AverageProfitableTradesCriterionTest {

	@Test
	public void testCalculate()
	{
		TimeSeries series = new MockTimeSeries(new double[]{100d, 95d, 102d, 105d, 97d, 113d});
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.BUY)));
		
		AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
		
		assertThat(average.calculate(series, trades)).isEqualTo(2d/3);
	}

	@Test
	public void testCalculateWithOneTrade()
	{
		TimeSeries series = new MockTimeSeries(new double[]{100d, 95d, 102d, 105d, 97d, 113d});
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.BUY));
			
		AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
		assertThat(average.calculate(series, trade)).isEqualTo(0d);
		
		trade = new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.BUY));
		assertThat(average.calculate(series, trade)).isEqualTo(1d);
	}
	
	@Test
	public void testSummarize() {
		
		TimeSeries series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		List<Decision> decisions = new LinkedList<Decision>();

		List<Trade> trades = new LinkedList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
		Decision dummy1 = new MockDecision(trades, slicer);
		decisions.add(dummy1);

		List<Trade> trades2 = new LinkedList<Trade>();
		trades2.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new MockDecision(trades2, slicer);
		decisions.add(dummy2);

		AnalysisCriterion averateProfitable = new AverageProfitableTradesCriterion();
		assertThat(averateProfitable.summarize(series, decisions)).isEqualTo(2d/3);
	}	
}
