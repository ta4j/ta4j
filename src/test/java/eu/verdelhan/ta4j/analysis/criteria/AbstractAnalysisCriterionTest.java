package eu.verdelhan.ta4j.analysis.criteria;

import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class AbstractAnalysisCriterionTest {

	@Test
	public void testToString() {
		AbstractAnalysisCriterion c1 = new AverageProfitCriterion();
		assertThat(c1.toString()).isEqualTo("Average Profit");
		AbstractAnalysisCriterion c2 = new BuyAndHoldCriterion();
		assertThat(c2.toString()).isEqualTo("Buy And Hold");
		AbstractAnalysisCriterion c3 = new RewardRiskRatioCriterion();
		assertThat(c3.toString()).isEqualTo("Reward Risk Ratio");
	}

}
