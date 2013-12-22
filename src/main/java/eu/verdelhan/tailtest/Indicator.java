package net.sf.tail;

/**
 * Indicator é toda a classe que recebe um {@link TimeSeries} ou
 * 
 * @link {@link Indicator} como parâmetro e retorna um valor interpretado T.
 * 
 * 
 * @author Marcio
 * 
 * @param <T>
 */
public interface Indicator<T> {
	public T getValue(int index);

	public String getName();
}
