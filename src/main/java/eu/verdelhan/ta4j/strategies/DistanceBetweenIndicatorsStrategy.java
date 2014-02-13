package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;

/**
 * 
 * Estratégia que recebe dois indicadores com uma diferença comum entre eles e
 * toma a decisão de compra do indicador que está representando a ação inferior
 * caso a distance entre elas esteja abaixo de difference, e a decisão de venda
 * caso a distance entre elas esteja acima de difference.
 * 
 */
public class DistanceBetweenIndicatorsStrategy extends AbstractStrategy {

    Indicator<? extends Number> upper;

    Indicator<? extends Number> lower;

    double distance;

    double difference;

	/**
	 * @param upper
	 * @param lower
	 * @param distance
	 * @param difference
	 */
    public DistanceBetweenIndicatorsStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower,
            double distance, double difference) {
        this.upper = upper;
        this.lower = lower;
        this.distance = distance;
        this.difference = difference;
    }

    @Override
    public boolean shouldEnter(int index) {
        if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) >= ((difference + 1.0) * distance)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExit(int index) {
        if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) <= ((1.0 - difference) * distance)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), upper, lower);
    }
}
