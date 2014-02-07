package eu.verdelhan.ta4j;

import java.math.MathContext;
import java.math.RoundingMode;

/**
 * TA utils.
 */
public class TAUtils {

	/** Scale of the TA decimals */
	public static final int PRECISION = 10;
	/** Rounding mode for the TA decimals operations */
	public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
	/** Math context for the TA decimals operations */
	public static final MathContext MATH_CONTEXT = new MathContext(PRECISION, ROUNDING_MODE);

}
