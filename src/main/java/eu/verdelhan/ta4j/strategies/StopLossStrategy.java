/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.strategies;

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
