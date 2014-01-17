package eu.verdelhan.tailtest;

/**
 * Indicator é toda a classe que recebe um {@link TimeSeries} ou
 * 
 * @link {@link Indicator} como parâmetro e retorna um valor interpretado T.
 * 
 * @param <T> the type of returned value
 */
public interface Indicator<T> {

	/**
	 * @param index the index
	 * @return the value of the indicator
	 */
    T getValue(int index);
}
