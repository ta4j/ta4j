/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting;

/**
 * Bounds for rendered chart images and display panels.
 *
 * <p>
 * Rendering allocates a raster of {@code width * height} pixels, so a caller-
 * or file-supplied size such as {@code 100000 x 100000} would exhaust the heap
 * before any drawing starts. Sizes must be positive, at most
 * {@value #MAX_DIMENSION} pixels on each side and at most {@value #MAX_PIXELS}
 * pixels in total.
 *
 * @since 0.28.1
 */
public final class ChartImageSize {

    /** Largest accepted width or height, in pixels. */
    public static final int MAX_DIMENSION = 16_384;

    /** Largest accepted pixel count ({@code width * height}). */
    public static final long MAX_PIXELS = 50_000_000L;

    private ChartImageSize() {
    }

    /**
     * Validates a chart image or panel size.
     *
     * @param width  width in pixels
     * @param height height in pixels
     * @throws IllegalArgumentException if either side is not positive or the size
     *                                  exceeds the limits above
     */
    public static void validate(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("Image width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Image height must be positive");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Image size " + width + "x" + height + " exceeds the maximum side of " + MAX_DIMENSION + " pixels");
        }
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "Image size " + width + "x" + height + " exceeds the maximum of " + MAX_PIXELS + " pixels");
        }
    }
}
