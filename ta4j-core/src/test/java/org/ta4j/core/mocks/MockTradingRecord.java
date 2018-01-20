package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Decimal;

public class MockTradingRecord extends BaseTradingRecord {

    /*
     * Constructor. Builds a TradingRecord from a list of states. Initial state
     * value is zero. Then at each index where the state value changes, the
     * TradingRecord operates at that index.
     * 
     * @param states List<Decimal> of state values
     */
    public MockTradingRecord(List<Decimal> states) {
        super();
        double lastState = 0d;
        for (int i = 0; i < states.size(); i++) {
            double state = states.get(i).doubleValue();
            if (state != lastState) {
                this.operate(i);
            }
            lastState = state;
        }
    }
}
