package ru.repitbot.domain.file;

import org.junit.jupiter.api.Test;
import ru.repitbot.domain.homework.ItemKind;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypesTest {

    private static byte[] bytes(int... b) {
        byte[] out = new byte[Math.max(b.length, 16)];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static FileTypes.Detected detected(byte[] head, String name) {
        Optional<FileTypes.Detected> d = FileTypes.detect(head, name);
        assertTrue(d.isPresent(), "должен приниматься: " + name);
        return d.get();
    }

    private static void rejected(byte[] head, String name) {
        assertTrue(FileTypes.detect(head, name).isEmpty(), "должен отклоняться: " + name);
    }

    @Test
    void photos() {
        assertEquals(new FileTypes.Detected("image/jpeg", ItemKind.PHOTO), detected(bytes(0xFF, 0xD8, 0xFF, 0xE0), "IMG_1.JPG"));
        assertEquals("image/png", detected(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), "доска.png").mime());
        assertEquals("image/webp", detected(ascii("RIFF\0\0\0\0WEBPVP8 "), "a.webp").mime());
    }

    @Test
    void typeComesFromContentNotName() {
        // Фото с «неправильным» именем — всё равно фото; HTML под видом фото — отказ.
        assertEquals(ItemKind.PHOTO, detected(bytes(0xFF, 0xD8, 0xFF, 0xE1), "снимок").kind());
        rejected(ascii("<!doctype html><script>alert(1)</script>"), "photo.jpg");
        rejected(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>"), "a.svg");
    }

    @Test
    void documents() {
        assertEquals("application/pdf", detected(ascii("%PDF-1.7\n"), "задание.pdf").mime());
        assertEquals(ItemKind.DOCUMENT, detected(bytes('P', 'K', 3, 4), "эссе.docx").kind());
        assertEquals("application/msword", detected(bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1), "old.doc").mime());
        assertEquals("text/plain", detected("Упражнение 3, стр. 45".getBytes(StandardCharsets.UTF_8), "ответ.txt").mime());
    }

    @Test
    void archivesAndBinariesAreRejected() {
        rejected(bytes('P', 'K', 3, 4), "архив.zip");
        rejected(bytes('M', 'Z', 0x90, 0), "setup.exe");
        rejected(bytes('M', 'Z', 0x90, 0), "notes.txt");
        rejected(new byte[]{(byte) 0xC3, 0x28, 'a', 'b', 'c', 'd'}, "broken.txt");
    }

    @Test
    void audioAndVideo() {
        assertEquals(ItemKind.AUDIO, detected(ascii("ID3\u0004\0\0"), "запись.mp3").kind());
        assertEquals(ItemKind.AUDIO, detected(ascii("\0\0\0\u0020ftypM4A \0\0"), "voice.m4a").kind());
        assertEquals(ItemKind.VIDEO, detected(ascii("\0\0\0\u0018ftypisom\0\0"), "урок.mp4").kind());
        assertEquals("video/quicktime", detected(ascii("\0\0\0\u0014ftypqt  \0\0"), "IMG_2.MOV").mime());
        assertEquals("audio/ogg", detected(ascii("OggS\0\u0002"), "a.ogg").mime());
    }

    @Test
    void heicIsKeptAsDocument() {
        FileTypes.Detected d = detected(ascii("\0\0\0\u0018ftypheic\0\0"), "IMG_3.HEIC");
        assertEquals("image/heic", d.mime());
        assertEquals(ItemKind.DOCUMENT, d.kind(), "большинство браузеров HEIC не показывает");
    }

    @Test
    void namesAreCleaned() {
        assertEquals("passwd", FileService.cleanName("../../etc/passwd"));
        assertEquals("evil.pdf", FileService.cleanName("C:\\Users\\x\\evil.pdf"));
        assertEquals("ab.pdf", FileService.cleanName("a\u0000b\u202e.pdf"));
        assertEquals("файл", FileService.cleanName(".."));
        assertEquals("файл", FileService.cleanName(null));
        String longName = FileService.cleanName("я".repeat(400) + ".docx");
        assertEquals(255, longName.length());
        assertTrue(longName.endsWith(".docx"), "расширение сохраняется");
    }

    @Test
    void kindFromStoredMimeMatchesDetection() {
        for (String mime : new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"}) {
            assertEquals(ItemKind.PHOTO, FileTypes.kindOf(mime), mime);
        }
        assertEquals(ItemKind.DOCUMENT, FileTypes.kindOf("image/heic"), "HEIC — документ: браузеры его не покажут");
        assertEquals(ItemKind.DOCUMENT, FileTypes.kindOf("application/pdf"));
        assertEquals(ItemKind.DOCUMENT, FileTypes.kindOf("text/plain"));
        assertEquals(ItemKind.AUDIO, FileTypes.kindOf("audio/mp4"));
        assertEquals(ItemKind.VIDEO, FileTypes.kindOf("video/quicktime"));
    }
}
