package eu.verdelhan.ta4j;

import org.assertj.core.data.Offset;

/**
 * Utility class for tests.
 */
public class TATestsUtils {

	/** Short offset for double equality checking */
	public static final Offset<Double> SHORT_OFFSET = Offset.offset(0.01);
	/** Long offset for double equality checking */
	public static final Offset<Double> LONG_OFFSET = Offset.offset(0.0000000001);

}
