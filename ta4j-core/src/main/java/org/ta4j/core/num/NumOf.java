package org.ta4j.core.num;

public enum NumOf {
    ZERO {
        @Override
        public Num get(Num anyNum) {
            if (isDecimalNum(anyNum)) {
                return DecimalNum.ZERO;
            } else if (isDoubleNum(anyNum)) {
                return DoubleNum.ZERO;
            }
            return NaN.NaN;
        }
    },
    ONE {
        @Override
        public Num get(Num anyNum) {
            if (isDecimalNum(anyNum)) {
                return DecimalNum.ONE;
            } else if (isDoubleNum(anyNum)) {
                return DoubleNum.ONE;
            }
            return NaN.NaN;
        }
    },
    HUNDRED {
        @Override
        public Num get(Num anyNum) {
            if (isDecimalNum(anyNum)) {
                return DecimalNum.HUNDRED;
            } else if (isDoubleNum(anyNum)) {
                return DoubleNum.HUNDRED;
            }
            return NaN.NaN;
        }
    };

    private static boolean isDecimalNum(Num anyNum) {
        return anyNum.getClass() == DecimalNum.class;
    }

    private static boolean isDoubleNum(Num anyNum) {
        return anyNum.getClass() == DoubleNum.class;
    }

    /**
     * @param anyNum any Num to determine the Num type
     * @return the Num having the same type as anyNum
     */
    public abstract Num get(Num anyNum);
}
