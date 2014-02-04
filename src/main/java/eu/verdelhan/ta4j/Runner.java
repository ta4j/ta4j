package eu.verdelhan.ta4j;

import java.util.List;

/**
 * A runner.
 */
public interface Runner {

	/**
	 * @param slicePosition
	 * @return the list of trades corresponding to slicePosition
	 */
	List<Trade> run(int slicePosition);
}
