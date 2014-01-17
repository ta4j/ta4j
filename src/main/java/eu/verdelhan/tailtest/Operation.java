package eu.verdelhan.tailtest;

/**
 * A decision (buy/sell) taken from a {@link Strategy}.
 * 
 */
public class Operation {

	/** Type of the operation */
    private OperationType type;

	/** The index the operation was executed */
    private int index;

	/**
	 * @param index the index the operation was executed
	 * @param type the type of the operation
	 */
    public Operation(int index, OperationType type) {
        this.type = type;
        this.index = index;
    }

    public OperationType getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public int hashCode() {
        return index + (type.hashCode() * 31);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Operation) {
            Operation o = (Operation) obj;
            return type.equals(o.getType()) && (index == o.getIndex());
        }
        return false;
    }

    @Override
    public String toString() {
        return " Index: " + index + " type: " + type.toString();
    }

}
