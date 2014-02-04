package eu.verdelhan.ta4j;

/**
 * Set of {@link Operation}. Not a single operation.
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
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
    }

    public Trade(Operation entry, Operation exit) {
        this.entry = entry;
        this.exit = exit;
    }

    public Operation getEntry() {
        return entry;
    }

    public Operation getExit() {
        return exit;
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
        if (isNew()) {
            entry = new Operation(i, startingType);
        } else if (isOpened()) {
            if (i < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryOperation index");
            }
            exit = new Operation(i, startingType.complementType());
        }
    }

    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }
}
