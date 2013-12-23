package eu.verdelhan.tailtest;

/**
 * Conjunto fechado de {@link Operation} disjuntas de uma única ação.
 * 
 * @author Marcio
 * 
 */
public class Trade {

	private Operation entry;

	private Operation exit;

	private OperationType startingType;

	public Trade() {
		this(OperationType.BUY);
	}

	public Trade(OperationType startingType) {
		if (startingType != null)
			this.startingType = startingType;
		else
			throw new NullPointerException();
	}

	public Trade(Operation entry, Operation exit) {
		this.entry = entry;
		this.exit = exit;
	}

	public Operation getEntry() {
		return this.entry;
	}

	public Operation getExit() {
		return this.exit;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Trade) {
			Trade t = (Trade) obj;
			return entry.equals(t.getEntry()) && exit.equals(t.getExit());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (entry.hashCode() * 31) + (exit.hashCode() * 17);
	}

	public void operate(int i) {
		if (this.isNew()) {
			this.entry = new Operation(i, this.startingType);
		} else if (this.isOpened()) {
			if (i < this.entry.getIndex())
				throw new IllegalStateException("The index i is less than the entryOperation index");
			this.exit = new Operation(i, this.startingType.complementType());
		}
	}

	public boolean isClosed() {
		return this.entry != null && this.exit != null;
	}

	public boolean isOpened() {
		return this.entry != null && this.exit == null;
	}

	public boolean isNew() {
		return this.entry == null && this.exit == null;
	}

	@Override
	public String toString() {
		return "Entry: " + entry + " exit: " + exit;
	}
}
