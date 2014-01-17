package eu.verdelhan.tailtest;

/**
 * Type of operations.
 * 
 * @todo add ASK and BID as aliases
 */
public enum OperationType {
    BUY {
        @Override
        public OperationType complementType() {
            return SELL;
        }
    },
    SELL {
        @Override
        public OperationType complementType() {
            return BUY;
        }
    };

	/**
	 * @return the complementary operation type
	 */
    public abstract OperationType complementType();
}
