package net.sf.tail.strategy;

public class ParabolicSarAndDMIStrategy extends AbstractStrategy {

	
	private IndicatorCrossedIndicatorStrategy parabolicStrategy;
	private IndicatorOverIndicatorStrategy dmiStrategy;

	public String getName() {
		return getClass().getSimpleName();
	}

	public ParabolicSarAndDMIStrategy(IndicatorCrossedIndicatorStrategy indicatorCrossedIndicatorStrategy, IndicatorOverIndicatorStrategy indicatorOverIndicatorStrategy) {
		this.parabolicStrategy = indicatorCrossedIndicatorStrategy;
		this.dmiStrategy = indicatorOverIndicatorStrategy;
	}
	public boolean shouldEnter(int index) {
		return parabolicStrategy.shouldEnter(index);
	}

	public boolean shouldExit(int index) {
		return dmiStrategy.and(parabolicStrategy).shouldExit(index);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dmiStrategy == null) ? 0 : dmiStrategy.hashCode());
		result = prime * result + ((parabolicStrategy == null) ? 0 : parabolicStrategy.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ParabolicSarAndDMIStrategy other = (ParabolicSarAndDMIStrategy) obj;
		if (dmiStrategy == null) {
			if (other.dmiStrategy != null)
				return false;
		} else if (!dmiStrategy.equals(other.dmiStrategy))
			return false;
		if (parabolicStrategy == null) {
			if (other.parabolicStrategy != null)
				return false;
		} else if (!parabolicStrategy.equals(other.parabolicStrategy))
			return false;
		return true;
	}

}
