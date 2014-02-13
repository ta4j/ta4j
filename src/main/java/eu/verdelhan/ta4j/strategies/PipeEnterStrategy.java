package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.helpers.CrossIndicator;

/**
 * PipeEnterStrategy baseia a compra em um {@link Indicator} lower, no momento
 * em que o {@link Indicator} value o ultrapassa em sentido ascendente, e baseia
 * a venda em um {@link Indicator} upper, no momento em que o {@link Indicator}
 * value o ultrapassa em sentido descendente. Estratégia muito comum para
 * indicadores que criam "tubos" de suporte e resistência, como por exemplo o
 * Bollinger Bands.
 * 
 */
public class PipeEnterStrategy extends AbstractStrategy {

    private final Indicator<Boolean> crossUp;

    private final Indicator<Boolean> crossDown;

    public PipeEnterStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower,
            Indicator<? extends Number> value) {
        crossUp = new CrossIndicator(value, upper);
        crossDown = new CrossIndicator(lower, value);
    }

    @Override
    public boolean shouldEnter(int index) {
        return (crossDown.getValue(index));
    }

    @Override
    public boolean shouldExit(int index) {
        return (crossUp.getValue(index));
    }

    @Override
    public String toString() {
        return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), crossUp,
                crossDown);
    }
}
