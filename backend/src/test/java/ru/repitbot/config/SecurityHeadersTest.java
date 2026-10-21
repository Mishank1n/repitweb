package ru.repitbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersTest {

    private static MockHttpServletResponse run(String siteUrl) throws Exception {
        AppProperties props = new AppProperties(siteUrl, "Europe/Moscow", 7, "08:00", "SUNDAY", "18:00", "/tmp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeaders(props).doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());
        return response;
    }

    @Test
    void strictPolicyOnEveryResponse() throws Exception {
        MockHttpServletResponse r = run("https://repit.ru");
        String csp = r.getHeader("Content-Security-Policy");
        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertFalse(csp.contains("unsafe-inline"), "встроенные скрипты и стили запрещены");
        assertFalse(csp.contains("unsafe-eval"));
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", r.getHeader("X-Frame-Options"));
        assertEquals("max-age=31536000", r.getHeader("Strict-Transport-Security"));
    }

    @Test
    void noHstsWithoutHttps() throws Exception {
        MockHttpServletResponse r = run("http://localhost:8080");
        assertNull(r.getHeader("Strict-Transport-Security"), "на localhost HSTS сломал бы другие сайты на этом адресе");
        assertTrue(r.getHeader("Content-Security-Policy").startsWith("default-src 'self'"));
    }
}
