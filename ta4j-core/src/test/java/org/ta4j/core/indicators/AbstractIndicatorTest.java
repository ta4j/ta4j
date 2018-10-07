package org.ta4j.core.indicators;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.Indicator;
import org.ta4j.core.IndicatorFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract test class to extend TimeSeries, Indicator an other test cases.
 * The extending class will be called twice. First time with {@link BigDecimalNum#valueOf},
 * second time with {@link DoubleNum#valueOf} as <code>Function<Number, Num></></code> parameter.
 * This should ensure that the defined test case is valid for both data types.
 *
 * @param <D> Data source of test object, needed for Excel-Sheet validation
 *           (could be <code>Indicator<Num></code> or <code>TimeSeries</code>, ...)
 * @param <I> The generic class of the test indicator (could be <code>Num</code>, <code>Boolean</code>, ...)
 */
@RunWith(Parameterized.class)
public abstract class AbstractIndicatorTest<D, I> {

        public final Function<Number, Num> numFunction;

    @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=PrecisionNum)")
        public static List<Function<Number, Num>> function(){
        return Arrays.asList(DoubleNum::valueOf, PrecisionNum::valueOf);
        }

    private final IndicatorFactory<D, I> factory;

    /**
     * Constructor.
     * 
     * @param factory IndicatorFactory for building an Indicator given data and
     *            parameters.
     * @param numFunction the function to convert a Number into a Num implementation (automatically insertet by Junit)
     */
    public AbstractIndicatorTest(IndicatorFactory<D, I> factory, Function<Number, Num> numFunction) {
        this.numFunction = numFunction;
        this.factory = factory;
    }

    /**
     * Constructor<p/>
     *
     * @param numFunction the function to convert a Number into a Num implementation (automatically insertet by Junit)
     */
    public AbstractIndicatorTest(Function<Number, Num> numFunction){
        this.numFunction = numFunction;
        this.factory = null;
    }

    /**
     * Generates an Indicator from data and parameters.
     * 
     * @param data indicator data
     * @param params indicator parameters
     * @return Indicator<I> from data given parameters
     */
    public Indicator<I> getIndicator(D data, Object... params) {
        assert factory != null;
        return factory.getIndicator(data, params);
    }

    protected Num numOf(Number n){
        return numFunction.apply(n);
    }

    public Num numOf(String string, int precision) {
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        return this.numOf(new BigDecimal(string, mathContext));
    }

}
