package eu.verdelhan.ta4j.analysis.evaluators;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.List;

/**
 * A decision.
 */
public class Decision {

	private AnalysisCriterion criterion;

	private Strategy strategy;

	private List<Trade> trades;

	private Runner runner;

	private TimeSeriesSlicer slicer;

	private int slicerPosition;

	public Decision(Strategy bestStrategy, TimeSeriesSlicer slicer,int slicerPosition,AnalysisCriterion criterion, List<Trade> trades, Runner runner) {
		this.strategy = bestStrategy;
		this.slicer = new RegularSlicer(slicer.getSeries(), slicer.getPeriod(), slicer.getSlice(0).getTick(slicer.getSlice(0).getBegin()).getEndTime());
		this.criterion = criterion;
		this.trades = trades;
		this.runner = runner;
		this.slicerPosition = slicerPosition;
	}

	public double evaluateCriterion() {
		return criterion.calculate(getActualSlice(), trades);
	}

	public double evaluateCriterion(AnalysisCriterion otherCriterion) {
		return otherCriterion.calculate(getActualSlice(), trades);
	}

	public Strategy getStrategy() {
		return strategy;
	}

	public Decision applyFor(int slicePosition) {
		List<Trade> newTrades = runner.run(slicePosition);
		return new Decision(strategy, slicer, slicerPosition+1, criterion, newTrades, runner);
	}

	public List<Trade> getTrades() {
		return trades;
	}

	public TimeSeries getActualSlice() {
		return slicer.getSlice(slicerPosition);
	}

	@Override
	public String toString() {
		return String.format("[strategy %s, criterion %s, value %.3f]", strategy, criterion.getClass().getSimpleName(), evaluateCriterion());
	}
	
	public String getName() {
		return getActualSlice() + ": " + getActualSlice().getPeriodName();
	}
	
	public String getFileName() {
		return this.getClass().getSimpleName() + getActualSlice().getTick(getActualSlice().getBegin()).getEndTime().toString("hhmmddMMyyyy");
	}

	@Override
	public int hashCode() {
		final int prime = 47;
		int result = 1;
		result = prime * result + ((criterion == null) ? 0 : criterion.hashCode());
		result = prime * result + ((runner == null) ? 0 : runner.hashCode());
		result = prime * result + ((slicer == null) ? 0 : slicer.hashCode());
		result = prime * result + slicerPosition;
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
		result = prime * result + ((trades == null) ? 0 : trades.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Decision other = (Decision) obj;
		if (criterion == null) {
			if (other.criterion != null) {
				return false;
			}
		} else if (!criterion.equals(other.criterion)) {
			return false;
		}
		if (runner == null) {
			if (other.runner != null) {
				return false;
			}
		} else if (!runner.equals(other.runner)) {
			return false;
		}
		if (slicer == null) {
			if (other.slicer != null) {
				return false;
			}
		} else if (!slicer.equals(other.slicer)) {
			return false;
		}
		if (slicerPosition != other.slicerPosition) {
			return false;
		}
		if (strategy == null) {
			if (other.strategy != null) {
				return false;
			}
		} else if (!strategy.equals(other.strategy)) {
			return false;
		}
		if (trades == null) {
			if (other.trades != null) {
				return false;
			}
		} else if (!trades.equals(other.trades)) {
			return false;
		}
		return true;
	}
}
