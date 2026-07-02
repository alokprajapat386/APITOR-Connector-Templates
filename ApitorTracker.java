
//TODO: REPLACE THIS WITH YOUR PACKAGE NAME
// package org.example.your_app/**;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  APITOR SPRING BOOT INTERCEPTOR
 *  1.  Set 'APITOR_PROJECT_TOKEN' in your environment variables.
 *  2.  Register this Filter class as a @Bean in your configuration.
 *  3.  Add it to your Security Filter Chain configuration.
 */

// TODO: ADD YOUR APITOR_PROJECT_TOKEN IN ENV VARIABLES
// TODO: ADD THIS CLASS TO YOUR SECURITY FILTER CHAIN CONFIGURATION


record MetricsInfoDTO(
        String ipAddress,
        String requestId,
        String endpointPath,
        String httpMethod,
        Integer statusCode,
        Double latency,
        Long payloadSize,
        String createdAt

){}

@Component
public class ApitorTracker extends OncePerRequestFilter {

    private final RestTemplate restTemplate;
    private final String apitorUrl = "https://apitor-backend.onrender.com/aggregator";

    @Value("${APITOR_PROJECT_TOKEN}")
    private String projectToken;

    private final Executor customFilterExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public ApitorTracker() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.nanoTime();
        String createdAt = Instant.now().toString();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long endTime = System.nanoTime();
            double latencyInMs = (endTime - startTime) / 1_000_000.0;



            if (projectToken != null && !projectToken.isEmpty()) {

                String ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty()) {
                    ipAddress = request.getRemoteAddr();
                }

                String requestId = request.getHeader("X-Request-Id");
                if (requestId == null || requestId.isEmpty()) {
                    requestId = UUID.randomUUID().toString();
                }

                String endpointPath = request.getRequestURI();
                String httpMethod = request.getMethod();
                int statusCode = response.getStatus();

                final String finalIp = ipAddress;
                final String finalReqId = requestId;
                final int finalStatus = statusCode;
                String contentLengthStr = response.getHeader("Content-Length");
                long payloadSize;
                if (contentLengthStr != null && !contentLengthStr.isEmpty()) {
                    payloadSize = Long.parseLong(contentLengthStr);
                } else {
                    payloadSize = 0L;
                }

                CompletableFuture.runAsync(() -> {
                    try {
                        MetricsInfoDTO metricsInfoDTO = new MetricsInfoDTO(
                                finalIp, finalReqId, endpointPath, httpMethod, finalStatus,
                                latencyInMs, payloadSize, createdAt
                        );


                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("X-Project-Token", projectToken);

                        HttpEntity<MetricsInfoDTO> entity = new HttpEntity<>(metricsInfoDTO, headers);
                        restTemplate.postForEntity(apitorUrl, entity, Void.class);
                    } catch (Exception ignored) {

                    }
                }, customFilterExecutor);
            }
        }
    }
}