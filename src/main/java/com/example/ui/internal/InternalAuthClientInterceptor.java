package com.example.ui.internal;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Signs every outbound call to a catalogue / the User service / the crawler with
 * {@link InternalAuth#HEADER}. The acting user is whatever
 * {@link ActingUser#get()} holds for this request (the logged-in user, or none).
 */
public final class InternalAuthClientInterceptor implements ClientHttpRequestInterceptor {

    private final String secret;

    public InternalAuthClientInterceptor(String secret) {
        this.secret = secret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(InternalAuth.HEADER, InternalAuth.header(
                secret, request.getMethod().name(), request.getURI().getRawPath(), ActingUser.get()));
        return execution.execute(request, body);
    }
}
