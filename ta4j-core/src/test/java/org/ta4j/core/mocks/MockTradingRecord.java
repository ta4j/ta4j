package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Decimal;

public class MockTradingRecord extends BaseTradingRecord {

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
