package ru.repitbot.notify.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushTest {

    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s.replaceAll("\\s", ""));
    }

    /** Пример из RFC 8291, раздел 5 и приложение A: те же ключи и соль дают ровно тот же результат. */
    @Test
    void encryptsLikeRfc8291Example() throws Exception {
        byte[] plaintext = b64("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");
        assertEquals("When I grow up, I want to be a watermelon", new String(plaintext, StandardCharsets.US_ASCII));
        ECPublicKey asPublic = PushCrypto.publicKey(b64("""
                BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIg
                Dll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"""));
        ECPrivateKey asPrivate = PushCrypto.privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcx aOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] auth = b64("BTBZMqHH6r4Tts7J_aSIgg");

        byte[] result = PushCrypto.encrypt(plaintext, uaPublic, auth, new KeyPair(asPublic, asPrivate), salt);

        byte[] header = b64("""
                DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z 9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml
                mlMoZIIgDll6e3vCYLocInmYWAmS6Tlz AC8wEqKK6PBru3jl7A8""");
        byte[] ciphertext = b64("""
                8pfeW0KbunFT06SuDKoJH9Ql87S1QUrd irN6GcG7sFz1y1sqLgVi1VhjVkHsUoEs
                bI_0LpXMuGvnzQ""");
        assertEquals(86, header.length);
        assertArrayEquals(PushCrypto.concat(header, ciphertext), result);
    }

    @Test
    void keysSurviveRawRoundTrip() throws Exception {
        for (int i = 0; i < 20; i++) {
            KeyPair pair = PushCrypto.newKeyPair();
            byte[] pub = PushCrypto.rawPublic((ECPublicKey) pair.getPublic());
            byte[] priv = PushCrypto.rawPrivate((ECPrivateKey) pair.getPrivate());
            assertEquals(65, pub.length);
            assertEquals(32, priv.length, "ведущие нули не теряются");
            assertEquals(pair.getPublic(), PushCrypto.publicKey(pub));
            assertEquals(((ECPrivateKey) pair.getPrivate()).getS(), PushCrypto.privateKey(priv).getS());
        }
    }

    /** Подпись JWT — 64 байта R||S и проверяется открытым ключом (так её проверяет push-сервис). */
    @Test
    void vapidSignatureVerifies() throws Exception {
        KeyPair pair = PushCrypto.newKeyPair();
        byte[] sig = Vapid.sign((ECPrivateKey) pair.getPrivate(), "header.claims");
        assertEquals(64, sig.length);
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(pair.getPublic());
        v.update("header.claims".getBytes(StandardCharsets.US_ASCII));
        assertTrue(v.verify(sig));
    }

    @Test
    void onlyKnownPushServices() {
        assertTrue(WebPush.allowed("https://fcm.googleapis.com/fcm/send/abc:def"));
        assertTrue(WebPush.allowed("https://web.push.apple.com/QGxyz"));
        assertTrue(WebPush.allowed("https://updates.push.services.mozilla.com/wpush/v2/gAAA"));
        assertTrue(WebPush.allowed("https://wns2-am3p.notify.windows.com/w/?token=x"));
        assertFalse(WebPush.allowed("http://fcm.googleapis.com/fcm/send/abc"), "только https");
        assertFalse(WebPush.allowed("https://db:5432/"), "внутренние адреса — нет");
        assertFalse(WebPush.allowed("https://169.254.169.254/latest/meta-data"));
        assertFalse(WebPush.allowed("https://evil.com/fcm.googleapis.com"));
        assertFalse(WebPush.allowed("https://fcm.googleapis.com.evil.com/x"));
        assertFalse(WebPush.allowed("https://notpush.apple.com.evil.com/x"));
        assertFalse(WebPush.allowed("https://user@fcm.googleapis.com/x"));
        assertFalse(WebPush.allowed("https://fcm.googleapis.com:8443/x"));
        assertFalse(WebPush.allowed("не адрес"));
    }

    @Test
    void payloadIsValidShortJson() {
        String json = WebPush.json(new WebPush.Message("📚 «ДЗ» \"1\"", "строка 1\nстрока\t2\\", "/hw/5", 3));
        assertEquals("{\"title\":\"📚 «ДЗ» \\\"1\\\"\",\"body\":\"строка 1\\nстрока\\t2\\\\\",\"url\":\"/hw/5\",\"unread\":3}", json);
        String longBody = "😀".repeat(400);
        String cut = WebPush.json(new WebPush.Message("t", longBody, null, 0));
        assertFalse(cut.contains("�"));
        assertTrue(cut.contains("…") && cut.contains("\"url\":\"/\""));
        String body = cut.substring(cut.indexOf("\"body\":\"") + 8, cut.indexOf("…"));
        assertFalse(Character.isHighSurrogate(body.charAt(body.length() - 1)), "эмодзи не разрезан пополам");
    }
}
