package eu.verdelhan.tailtest;

/**
 * Operation é toda decisão tomada da classe {@link Strategy}.
 * 
 * Contém um enum {@link OperationType} representando o tipo de operação e um
 * 
 * int index em que a operação foi efetuada.
 * 
 */
public class Operation {

    private OperationType type;

    private int index;

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
