package com.jobmate.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalPrivacyFilterTests {
    @Test
    void 외부주소와_악성호스트와_외부오리진은_거절한다() throws Exception {
        // given
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var filter = new LocalPrivacyFilter(environment, new ObjectMapper());
        for (String boundary : new String[]{"remote", "host", "origin", "fetch"}) {
            var request = localRequest("/api/v1/me/profile");
            if (boundary.equals("remote")) request.setRemoteAddr("192.168.0.10");
            if (boundary.equals("host")) request.setServerName("evil.example");
            if (boundary.equals("origin")) request.addHeader("Origin", "https://evil.example");
            if (boundary.equals("fetch")) request.addHeader("Sec-Fetch-Site", "cross-site");
            var response = new MockHttpServletResponse();
            // when
            filter.doFilter(request, response, (a, b) -> { throw new AssertionError("접근이 차단되어야 한다"); });
            // then
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void 로컬이_아닌_프로파일에서는_개인정보_API를_노출하지_않는다() throws Exception {
        // given
        var filter = new LocalPrivacyFilter(new MockEnvironment(), new ObjectMapper());
        var response = new MockHttpServletResponse();
        // when
        filter.doFilter(localRequest("/api/v1/me/profile"), response, (a, b) -> { throw new AssertionError(); });
        // then
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void 정상_루프백_요청은_캐시를_금지하고_통과시킨다() throws Exception {
        // given
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var request = localRequest("/api/v1/me/profile");
        request.addHeader("Origin", "http://localhost:5173");
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean();
        // when
        new LocalPrivacyFilter(environment, new ObjectMapper()).doFilter(request, response, (a, b) -> passed.set(true));
        // then
        assertThat(passed).isTrue();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void 공개공고_조회는_유지하되_인코딩과_매트릭스로_개인경로를_우회하지_못한다() throws Exception {
        // given
        var filter = new LocalPrivacyFilter(new MockEnvironment(), new ObjectMapper());
        for (String path : new String[]{"/api/v1/jobs", "/api/v1/jobs/1"}) {
            var passed = new AtomicBoolean();
            // when
            filter.doFilter(localRequest(path), new MockHttpServletResponse(), (a, b) -> passed.set(true));
            // then
            assertThat(passed).isTrue();
        }
        for (String path : new String[]{"/api/v1/%6de/profile", "/api/v1/jobs;ignored/recommendations"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(localRequest(path), response, (a, b) -> { throw new AssertionError(); });
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    private MockHttpServletRequest localRequest(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        request.setServerName("localhost");
        return request;
    }
}
