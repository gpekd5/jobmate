package com.jobmate.common.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.presentation.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class LocalPrivacyFilter extends OncePerRequestFilter {

    private final Environment environment;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path;
        try {
            path = UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8)
                    .replaceAll(";[^/]*", "");
        } catch (IllegalArgumentException exception) {
            return false;
        }
        boolean publicRead = ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && ("/api/v1/jobs".equals(path) || path.matches("/api/v1/jobs/[0-9]+"));
        return !path.startsWith("/api/v1/") || publicRead;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            reject(response, 404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다.");
            return;
        }
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (!loopbackAddress(request.getRemoteAddr()) || !localHost(request.getServerName())
                || (origin != null && !localOrigin(origin)) || "cross-site".equals(fetchSite)) {
            reject(response, 403, "LOCAL_ONLY", "개인정보 기능은 신뢰할 수 있는 루프백 로컬 환경에서만 사용할 수 있습니다.");
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(request, response);
    }

    private boolean loopbackAddress(String value) {
        try {
            return value != null && InetAddress.getByName(value).isLoopbackAddress();
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean localHost(String value) {
        return "localhost".equalsIgnoreCase(value) || "127.0.0.1".equals(value)
                || "::1".equals(value) || "[::1]".equals(value);
    }

    private boolean localOrigin(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && localHost(uri.getHost()) && uri.getUserInfo() == null
                    && (uri.getPort() == 5173 || uri.getPort() == 8080);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message, List.of()));
    }
}
