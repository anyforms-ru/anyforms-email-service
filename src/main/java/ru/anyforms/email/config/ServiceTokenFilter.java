package ru.anyforms.email.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String SERVICE_HEADER = "X-Auth-Token";
    public static final String SERVICE_ROLE = "SERVICE";

    private static final String AUTH_HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final byte[] token;

    public ServiceTokenFilter(@Value("${service.token}") String token) {
        this.token = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (matches(request.getHeader(SERVICE_HEADER)) || matches(extractBearer(request.getHeader(AUTH_HEADER)))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "service", null, List.of(new SimpleGrantedAuthority("ROLE_" + SERVICE_ROLE))));
        }
        filterChain.doFilter(request, response);
    }

    boolean matches(String presented) {
        if (token.length == 0 || !StringUtils.hasText(presented)) {
            return false;
        }
        return MessageDigest.isEqual(token, presented.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String extractBearer(String header) {
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
