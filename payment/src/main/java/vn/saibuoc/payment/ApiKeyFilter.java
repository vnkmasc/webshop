package vn.saibuoc.payment;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires the shared API key on /api/** and carries the caller's X-Request-Id into the logs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${gateway.api-key:}") String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rid = request.getHeader("X-Request-Id");
        MDC.put("requestId", rid == null || rid.isBlank() || rid.length() > 64 ? UUID.randomUUID().toString() : rid);
        try {
            if (request.getRequestURI().startsWith("/api/") && apiKey.length > 0) {
                String header = request.getHeader("Authorization");
                byte[] given = header != null && header.startsWith("Bearer ")
                        ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
                if (!MessageDigest.isEqual(apiKey, given)) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Invalid API key\"}");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
