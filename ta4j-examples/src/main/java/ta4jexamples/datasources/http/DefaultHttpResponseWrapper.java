/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
        if (httpResponse == null) {
            throw new IllegalArgumentException("HttpResponse cannot be null");
        }
        this.httpResponse = httpResponse;
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
