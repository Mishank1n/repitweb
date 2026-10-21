package ru.repitbot.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.repitbot.config.AppProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAuthTest {

    private final AppProperties props = new AppProperties("https://repit.ru/", "Europe/Moscow", 7, "08:00", "SUNDAY",
            "18:00", "/data/files");
    private final SessionAuth auth = new SessionAuth(null, null, props);

    private static MockHttpServletRequest post(String origin, String host) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/tutor/queue");
        if (origin != null) {
            r.addHeader("Origin", origin);
        }
        r.addHeader("Host", host);
        return r;
    }

    @Test
    void acceptsOwnSite() {
        assertTrue(auth.sameSite(post("https://repit.ru", "repit.ru")));
    }

    @Test
    void acceptsTunnelAddressMatchingHost() {
        assertTrue(auth.sameSite(post("https://abc123.lhr.life", "abc123.lhr.life")));
    }

    @Test
    void rejectsForeignSite() {
        assertFalse(auth.sameSite(post("https://evil.example", "repit.ru")));
        assertFalse(auth.sameSite(post("https://repit.ru.evil.example", "repit.ru")));
        assertFalse(auth.sameSite(post("null", "repit.ru")), "песочница / file:// присылают Origin: null");
    }

    @Test
    void requestsWithoutOriginAreNotFromBrowserPages() {
        assertTrue(auth.sameSite(post(null, "repit.ru")));
    }

    @Test
    void siteSettings() {
        assertEquals("https://repit.ru", props.site());
        assertEquals("https://repit.ru", props.siteOrigin());
        assertTrue(props.secureCookies());
    }

    @Test
    void sessionTokenIsStoredHashed() {
        String h = SessionStore.hash("token");
        assertEquals(64, h.length());
        assertNotEquals("token", h);
        assertEquals(h, SessionStore.hash("token"));
    }
}
