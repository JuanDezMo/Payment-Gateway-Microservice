package com.paymentgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.config.PaymentProperties;
import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exige Authorization: Bearer <api-key> en /payments, antes de validar el cuerpo. */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String PROTECTED_PATH = "/payments";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(PaymentProperties properties, ObjectMapper objectMapper) {
        this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isValid(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            log.warn("Request rejected: invalid or missing API key");
            ErrorCode code = ErrorCode.INVALID_API_KEY;
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    new PaymentResponse(code.status(), null, code.name(), code.message()));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] received = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(received, expectedKey);
    }
}
