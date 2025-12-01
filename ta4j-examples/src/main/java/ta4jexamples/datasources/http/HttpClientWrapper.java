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

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Wrapper around {@link java.net.http.HttpClient} to enable easier testing via
 * mocking.
 * <p>
 * This wrapper provides a simple interface for HTTP operations that can be
 * easily mocked in unit tests, avoiding the need to mock the final
 * {@link java.net.http.HttpClient} class directly.
 */
public interface HttpClientWrapper {

    /**
     * Sends the given request using this client, blocking if necessary to get the
     * response.
     *
     * @param <T>     the response body type
     * @param request the request
     * @param handler the response body handler
     * @return the response wrapper
     * @throws IOException          if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    <T> HttpResponseWrapper<T> send(HttpRequest request, java.net.http.HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException;
}
