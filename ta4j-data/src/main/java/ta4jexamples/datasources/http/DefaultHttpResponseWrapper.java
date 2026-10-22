/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.http;

import java.net.http.HttpResponse;

/**
 * Default implementation of {@link HttpResponseWrapper} that delegates to a
 * {@link HttpResponse}.
 *
 * @param <T> the response body type
 */
public class DefaultHttpResponseWrapper<T> implements HttpResponseWrapper<T> {

    private final HttpResponse<T> httpResponse;

    /**
     * Creates a new wrapper around the specified HttpResponse.
     *
     * @param httpResponse the HttpResponse to wrap
     */
    public DefaultHttpResponseWrapper(HttpResponse<T> httpResponse) {
        this(new ResponseConfig<>(validatedHttpResponse(httpResponse)));
    }

    private DefaultHttpResponseWrapper(ResponseConfig<T> config) {
        this.httpResponse = config.httpResponse();
    }

    private static <T> HttpResponse<T> validatedHttpResponse(HttpResponse<T> httpResponse) {
        if (httpResponse == null) {
            throw new IllegalArgumentException("HttpResponse cannot be null");
        }
        return httpResponse;
    }

    private record ResponseConfig<T>(HttpResponse<T> httpResponse) {
    }

    @Override
    public int statusCode() {
        return httpResponse.statusCode();
    }

    @Override
    public T body() {
        return httpResponse.body();
    }
}
