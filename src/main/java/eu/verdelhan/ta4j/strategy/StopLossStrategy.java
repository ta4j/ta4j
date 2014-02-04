package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;

/**
 * MinValueStopperStrategy baseia a compra em uma {@link Strategy} enviada como
 * parâmetro, registrando o valor do {@link Indicator} enviado como parâmetro no
 * mesmo índice de compra, e baseia a venda nessa mesma {@link Strategy} desde
 * que o valor registrado do {@link Indicator} na compra não tenha um decréscimo
 * maior que loss %
 * 
 */
public class StopLossStrategy extends AbstractStrategy {

    private Strategy strategy;

    private double loss;

    private Indicator<? extends Number> indicator;

    private double value;

    public StopLossStrategy(Indicator<? extends Number> indicator, Strategy strategy, int loss) {
        this.strategy = strategy;
        this.loss = loss;
        this.indicator = indicator;
    }

    @Override
    public boolean shouldEnter(int index) {
        if (strategy.shouldEnter(index)) {
            value = indicator.getValue(index).doubleValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExit(int index) {
        if ((value - (value * (loss / 100))) > indicator.getValue(index).doubleValue()) {
            return true;
        }
        return strategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s stoper: %s over %s", this.getClass().getSimpleName(), "" + loss, strategy);
    }
}
