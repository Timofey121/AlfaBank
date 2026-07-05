package com.alfabank.crypto.service;

import com.alfabank.crypto.dto.request.FetchRequest;
import com.alfabank.crypto.dto.response.FetchResponse;
import com.alfabank.crypto.exception.NetworkException;
import com.alfabank.crypto.model.FetchDetail;
import com.alfabank.crypto.model.OperationType;
import com.alfabank.crypto.repository.FetchDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private static final long MAX_RESPONSE_SIZE_BYTES = 10L * 1024 * 1024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final AuditedOperation auditedOperation;
    private final FetchDetailRepository fetchDetailRepository;

    public FetchService(AuditedOperation auditedOperation, FetchDetailRepository fetchDetailRepository) {
        this.auditedOperation = auditedOperation;
        this.fetchDetailRepository = fetchDetailRepository;
    }

    @Transactional
    public FetchResponse fetch(FetchRequest request) {
        log.info("Fetching: {}", request.url());
        URI uri = validateUrl(request.url());

        return auditedOperation.run(OperationType.FETCH, request.url().getBytes(), null, "FETCH_FAILED",
                (msg, cause) -> new NetworkException("Fetch failed for URL: " + request.url(), cause),
                op -> {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(uri)
                            .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                            .GET().build();
                    HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        response.body().close();
                        throw new NetworkException("Server responded with redirect status " + response.statusCode()
                                + " for " + request.url() + "; redirects are not followed");
                    }

                    long declaredLength = parseContentLength(response);
                    if (declaredLength > MAX_RESPONSE_SIZE_BYTES) {
                        response.body().close();
                        throw new NetworkException("Response for " + request.url() + " declared size " + declaredLength
                                + " bytes, exceeding the maximum allowed " + MAX_RESPONSE_SIZE_BYTES + " bytes");
                    }

                    byte[] body = response.body().readAllBytes();
                    String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
                    log.info("[{}] got HTTP {} from {}, size={} bytes, contentType={}",
                            op.getId(), response.statusCode(), request.url(), body.length, contentType);

                    saveFetchDetail(op.getId(), request.url(), response.statusCode(), contentType, body.length);

                    FetchResponse fetchResponse = new FetchResponse(op.getId(),
                            Base64.getEncoder().encodeToString(body),
                            contentType, body.length, response.statusCode(), Instant.now());
                    return AuditedOperation.Outcome.of(fetchResponse, body);
                });
    }

    private URI validateUrl(String url) {
        URI uri = URI.create(url);
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid host");
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve host: " + host);
        }
        if (isDisallowedAddress(address)) {
            throw new IllegalArgumentException("URL host resolves to a disallowed network address");
        }
        return uri;
    }

    private boolean isDisallowedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] addr = address.getAddress();
        return addr.length == 4 && (addr[0] & 0xFF) == 169 && (addr[1] & 0xFF) == 254;
    }

    private long parseContentLength(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Length")
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return -1L;
                    }
                })
                .orElse(-1L);
    }

    private void saveFetchDetail(String opId, String url, int status, String contentType, long size) {
        FetchDetail d = new FetchDetail();
        d.setOperationId(opId);
        d.setSourceUrl(url);
        d.setHttpStatus(status);
        d.setContentType(contentType);
        d.setSizeBytes(size);
        fetchDetailRepository.save(d);
    }
}
