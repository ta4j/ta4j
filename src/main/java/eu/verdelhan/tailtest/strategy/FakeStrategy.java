package net.sf.tail.strategy;

import java.util.Arrays;

import net.sf.tail.Operation;

public class FakeStrategy extends AbstractStrategy {

	private Operation[] enter;

	private Operation[] exit;

	public FakeStrategy(Operation[] enter, Operation[] exit) {
		this.enter = enter;
		this.exit = exit;
	}

	public String getName() {
		return "Fake Strategy:0";
	}

	public boolean shouldEnter(int index) {
		if (enter[index] != null)
			return true;
		return false;
	}

	public boolean shouldExit(int index) {
		if (exit[index] != null)
			return true;
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(enter);
		result = prime * result + Arrays.hashCode(exit);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final FakeStrategy other = (FakeStrategy) obj;
		if (!Arrays.equals(enter, other.enter))
			return false;
		if (!Arrays.equals(exit, other.exit))
			return false;
		return true;
	}
}
