/*
 * SPDX-License-Identifier: MIT
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
