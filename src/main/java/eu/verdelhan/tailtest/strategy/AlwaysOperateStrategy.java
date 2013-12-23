package eu.verdelhan.tailtest.strategy;

public class AlwaysOperateStrategy extends AbstractStrategy {

	public boolean shouldEnter(int index) {
		return true;
	}

	public boolean shouldExit(int index) {
		return true;
	}

	public String getName() {
		return this.getClass().getSimpleName();

	}
}
