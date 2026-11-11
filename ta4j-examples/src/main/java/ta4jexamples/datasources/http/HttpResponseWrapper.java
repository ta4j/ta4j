/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.http;

/**
 * Wrapper around {@link java.net.http.HttpResponse} to enable easier testing
 * via mocking.
 * <p>
 * This wrapper provides a simple interface for HTTP response operations that
 * can be easily mocked in unit tests, avoiding the need to mock the final
 * {@link java.net.http.HttpResponse} class directly.
 *
 * @param <T> the response body type
 */
public interface HttpResponseWrapper<T> {

    /**
     * Returns the status code for this response.
     *
     * @return the status code
     */
    int statusCode();

    /**
     * Returns the body of this response.
     *
     * @return the response body
     */
    T body();
}
