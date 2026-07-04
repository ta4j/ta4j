/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Default implementation of {@link HttpClientWrapper} that delegates to a
 * {@link HttpClient}.
 */
public class DefaultHttpClientWrapper implements HttpClientWrapper {

    private final HttpClient httpClient;

    /**
     * Creates a new wrapper around the specified HttpClient.
     *
     * @param httpClient the HttpClient to wrap
     */
    public DefaultHttpClientWrapper(HttpClient httpClient) {
        this(new ClientConfig(validatedHttpClient(httpClient)));
    }

    /**
     * Creates a new wrapper with a default HttpClient.
     */
    public DefaultHttpClientWrapper() {
        this(new ClientConfig(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()));
    }

    private DefaultHttpClientWrapper(ClientConfig config) {
        this.httpClient = config.httpClient();
    }

    private static HttpClient validatedHttpClient(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient cannot be null");
        }
        return httpClient;
    }

    private record ClientConfig(HttpClient httpClient) {
    }

    @Override
    public <T> HttpResponseWrapper<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpResponse<T> response = httpClient.send(request, handler);
        return new DefaultHttpResponseWrapper<>(response);
    }
}
