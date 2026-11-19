package com.akhana.akhana_admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * INT-01: authenticates the WhatsApp bot on /api/integrations/** with its own key (header X-Api-Key), never with a
 * user. The bot gets only the INTEGRATION_CATALOG authority, which no role has, so it reaches nothing else.
 * Without CATALOG_API_KEY the filter authenticates nobody and the endpoint answers 401.
 */
public class IntegrationApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    public static final String AUTHORITY = "INTEGRATION_CATALOG";
    public static final String PRINCIPAL = "integration:whatsapp-bot";
    static final String PATH_PREFIX = "/api/integrations/";

    /** SHA-256 of the configured key, compared in constant time; null when the integration is off. */
    private final byte[] keyHash;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public IntegrationApiKeyFilter(String apiKey) {
        this.keyHash = apiKey == null || apiKey.isBlank() ? null : sha256(apiKey.trim());
    }

    public boolean isEnabled() {
        return keyHash != null;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (keyHash != null && given != null && MessageDigest.isEqual(keyHash, sha256(given.trim()))) {
            var authToken = UsernamePasswordAuthenticationToken.authenticated(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authToken);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
