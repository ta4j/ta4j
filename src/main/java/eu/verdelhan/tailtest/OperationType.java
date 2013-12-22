package net.sf.tail;

/**
 * Enum com os tipos de operações.
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

	public abstract OperationType complementType();
}
