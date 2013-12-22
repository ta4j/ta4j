package net.sf.tail.indicator.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.tail.Indicator;

/**
 * Cacheia o {@link Indicator} do construtor, impedindo que seja calculado duas
 * vezes o mesmo índice index do Indicator.
 * 
 * @author Marcio
 * 
 */
public abstract class CachedIndicator<T> implements Indicator<T> {

	private transient List<T> results;

	public CachedIndicator() {
		results = new ArrayList<T>();
	}

	public T getValue(int index) {
		

		if(results == null)
			results = new ArrayList<T>();
		
		increaseLength(index);
		if (results.get(index) == null) {
			int i = index;
			while (i > 0 && results.get(i--) == null)
				;
			for (; i <= index; i++) {
				if(results.get(i) == null)
					results.set(i, calculate(i));
			}
		}
		return results.get(index);
	}

	private void increaseLength(int index) {
		if (results.size() <= index) {
			results.addAll(Collections.<T> nCopies(index - results.size() + 1, null));
		}
	}

	@Override
	public String toString() {
		String[] name = getClass().getName().split("\\.");
		return name[name.length - 1];
	}

	public abstract String getName();

	protected abstract T calculate(int index);
}
