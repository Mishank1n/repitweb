package ru.repitbot.domain.file;

import ru.repitbot.domain.homework.ItemKind;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Какие файлы принимаются: тип определяется по первым байтам содержимого, а не по расширению или заголовку
 * браузера — переименованный в .jpg HTML или скрипт не пройдёт. Расширение нужно только там, где сигнатура
 * общая (docx/xlsx/pptx — это zip). Всё остальное (zip, exe, svg, html…) отклоняется.
 */
public final class FileTypes {

    /** Сколько первых байт нужно для проверки. */
    public static final int HEAD = 4096;

    /** Распознанный тип: MIME для отдачи и вид части (фото показываются превью, остальное скачивается). */
    public record Detected(String mime, ItemKind kind) {
    }

    private static final Set<String> OFFICE_ZIP = Set.of("docx", "xlsx", "pptx");
    private static final Set<String> OFFICE_OLD = Set.of("doc", "xls", "ppt");

    private FileTypes() {
    }

    public static Optional<Detected> detect(byte[] head, String filename) {
        String ext = extension(filename);
        if (starts(head, 0xFF, 0xD8, 0xFF)) {
            return of("image/jpeg", ItemKind.PHOTO);
        }
        if (starts(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return of("image/png", ItemKind.PHOTO);
        }
        if (starts(head, 'G', 'I', 'F', '8')) {
            return of("image/gif", ItemKind.PHOTO);
        }
        if (starts(head, 'R', 'I', 'F', 'F') && at(head, 8, 'W', 'E', 'B', 'P')) {
            return of("image/webp", ItemKind.PHOTO);
        }
        if (starts(head, 'R', 'I', 'F', 'F') && at(head, 8, 'W', 'A', 'V', 'E')) {
            return of("audio/wav", ItemKind.AUDIO);
        }
        if (starts(head, '%', 'P', 'D', 'F', '-')) {
            return of("application/pdf", ItemKind.DOCUMENT);
        }
        if (at(head, 4, 'f', 't', 'y', 'p')) {
            return isoMedia(head);
        }
        if (starts(head, 'O', 'g', 'g', 'S')) {
            return of("audio/ogg", ItemKind.AUDIO);
        }
        if (starts(head, 'I', 'D', '3') || (head.length > 1 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0)) {
            return of("audio/mpeg", ItemKind.AUDIO);
        }
        if (starts(head, 0x1A, 0x45, 0xDF, 0xA3)) {
            return of("video/webm", ItemKind.VIDEO);
        }
        if (starts(head, 'P', 'K', 0x03, 0x04) && OFFICE_ZIP.contains(ext)) {
            return of(switch (ext) {
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }, ItemKind.DOCUMENT);
        }
        if (starts(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) && OFFICE_OLD.contains(ext)) {
            return of(switch (ext) {
                case "doc" -> "application/msword";
                case "xls" -> "application/vnd.ms-excel";
                default -> "application/vnd.ms-powerpoint";
            }, ItemKind.DOCUMENT);
        }
        if (ext.equals("txt") && isPlainText(head)) {
            return of("text/plain", ItemKind.DOCUMENT);
        }
        return Optional.empty();
    }

    /** Вид части по сохранённому MIME — тот же, что {@link #detect} дал при загрузке. */
    public static ItemKind kindOf(String mime) {
        if (mime.equals("image/heic")) {
            return ItemKind.DOCUMENT;
        }
        if (mime.startsWith("image/")) {
            return ItemKind.PHOTO;
        }
        if (mime.startsWith("audio/")) {
            return ItemKind.AUDIO;
        }
        return mime.startsWith("video/") ? ItemKind.VIDEO : ItemKind.DOCUMENT;
    }

    /** «Фото 1.JPG» → «jpg». */
    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** MP4, MOV, M4A, HEIC — один контейнер, вид — по «бренду» после ftyp. */
    private static Optional<Detected> isoMedia(byte[] head) {
        String brand = head.length >= 12 ? new String(head, 8, 4, StandardCharsets.US_ASCII) : "";
        return switch (brand) {
            case "M4A ", "M4B " -> of("audio/mp4", ItemKind.AUDIO);
            case "qt  " -> of("video/quicktime", ItemKind.VIDEO);
            // HEIC с iPhone: браузеры, кроме Safari, его не покажут — храним как документ для скачивания.
            case "heic", "heix", "hevc", "mif1", "msf1" -> of("image/heic", ItemKind.DOCUMENT);
            default -> of("video/mp4", ItemKind.VIDEO);
        };
    }

    /** Текст в UTF-8 без нулевых байт (у двоичных файлов они почти всегда есть). */
    private static boolean isPlainText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        // Последний символ мог обрезаться на границе HEAD — проверяем без последних 3 байт.
        byte[] body = head.length > 3 ? Arrays.copyOf(head, head.length - 3) : head;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean starts(byte[] head, int... sig) {
        return at(head, 0, sig);
    }

    private static boolean at(byte[] head, int offset, int... sig) {
        if (head.length < offset + sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((head[offset + i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Detected> of(String mime, ItemKind kind) {
        return Optional.of(new Detected(mime, kind));
    }
}
