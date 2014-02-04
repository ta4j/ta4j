package eu.verdelhan.ta4j;


/**
 * Parameter: an {@link Indicator} or another {@link Strategy}
 * Returns an {@link Operation} when giving an index
 */
public interface Strategy {

	/**
	 * @param trade a trade
	 * @param index
	 * @return true to recommend an operation, false otherwise (no recommendation)
	 */
	boolean shouldOperate(Trade trade, int index);

	boolean shouldEnter(int index);

	boolean shouldExit(int index);
	
	Strategy and(Strategy strategy);
	
	Strategy or(Strategy strategy);
}
