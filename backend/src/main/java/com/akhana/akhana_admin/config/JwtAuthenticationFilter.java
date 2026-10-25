package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    /** Only endpoints reachable while the user must still change the password. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS = Set.of("/api/auth/me", "/api/auth/change-password");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7).trim();

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByUsername(username).orElse(null);

                if (user != null && user.isActive() && user.getDeletedAt() == null && jwtService.isTokenValid(token, user.getUsername())) {
                    if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED_PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()))) {
                        writePasswordChangeRequired(response);
                        return;
                    }
                    // Authorities are the per-action permissions of the role; endpoints check them with hasAuthority.
                    List<SimpleGrantedAuthority> authorities = user.getPermissions().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    var authToken = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authToken);
                    SecurityContextHolder.setContext(context);
                    securityContextRepository.saveContext(context, request, response);

                    log.debug("Authenticated user '{}' with role '{}' and {} permissions via JWT",
                            username, user.getRole(), authorities.size());
                } else {
                    log.debug("User '{}' is inactive, logically deleted, or token is invalid", username);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT authentication error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"error\":\"PasswordChangeRequired\",\"message\":\"Debe cambiar su contraseña antes de continuar\",\"timestamp\":\"%s\"}",
            Instant.now()));
    }
}
