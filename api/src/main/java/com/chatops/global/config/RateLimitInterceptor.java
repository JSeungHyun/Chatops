package com.chatops.global.config;

import com.chatops.global.common.annotation.RateLimit;
import com.chatops.domain.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    /** Atomic INCR + conditional EXPIRE via Lua to prevent orphaned keys */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local c = redis.call('INCR', KEYS[1]); " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; " +
            "return c"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String key = buildKey(request, rateLimit);
        if (key == null) {
            return true;
        }

        try {
            Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(rateLimit.windowSeconds())
            );
            if (count != null && count > rateLimit.maxRequests()) {
                log.warn("Rate limit exceeded: key={}, count={}", key, count);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\"}");
                return false;
            }
        } catch (Exception e) {
            log.warn("Rate limiting unavailable — allowing request: {}", e.getMessage());
        }

        return true;
    }

    private String buildKey(HttpServletRequest request, RateLimit rateLimit) {
        String identifier;
        if (rateLimit.byUser()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                identifier = "user:" + user.getId();
            } else {
                // Not authenticated yet — fall back to IP
                identifier = "ip:" + getClientIp(request);
            }
        } else {
            identifier = "ip:" + getClientIp(request);
        }
        return "rate:" + request.getRequestURI() + ":" + identifier;
    }

    private String getClientIp(HttpServletRequest request) {
        // Use X-Real-IP first (set by nginx to $remote_addr — trusted)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        // Fallback: last entry in X-Forwarded-For (appended by nginx, not client-controlled)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
