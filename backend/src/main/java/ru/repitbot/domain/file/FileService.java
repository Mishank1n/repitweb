package ru.repitbot.domain.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.homework.ItemKind;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Файлы заданий, ответов и материалов. Описание — в таблице files, байты — на диске в FILES_DIR/&lt;id&gt;.
 * Доступ к файлу проверяет вызывающий (через ДЗ, материал или сдачу, к которым файл привязан).
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int MAX_NAME = 255;
    /** Ученик или родитель за сутки: защита диска от случайного или нарочного забивания. */
    static final int DAILY_LIMIT = 100;
    /** Непривязанный файл (загрузили и не отправили форму) живёт сутки. */
    private static final Duration ORPHAN_AFTER = Duration.ofDays(1);

    /** Файл на диске и его описание. */
    public record Stored(String id, String name, String mime, long size, Path path) {
        public boolean isImage() {
            return mime.startsWith("image/");
        }
    }

    /** Ответ на загрузку: kind — как часть задания (PHOTO показывается превью, остальное скачивается). */
    public record Uploaded(String id, String name, String mime, long size, ItemKind kind) {
    }

    private final JdbcTemplate jdbc;
    private final Path dir;

    public FileService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.dir = Path.of(props.filesDir() == null || props.filesDir().isBlank() ? "/data/files" : props.filesDir());
    }

    public Optional<Stored> find(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT name, mime, size_bytes FROM files WHERE id = ?",
                        (rs, i) -> new Stored(id, rs.getString(1), rs.getString(2), rs.getLong(3), dir.resolve(id)), id)
                .stream()
                .filter(f -> Files.isRegularFile(f.path()))
                .findFirst();
    }

    /**
     * Принимает файл: тип по содержимому (см. {@link FileTypes}), запись во временный файл и атомарное
     * переименование — недописанный файл никогда не окажется под настоящим именем.
     *
     * @param limited true для ученика и родителя: не больше {@link #DAILY_LIMIT} загрузок за сутки
     */
    public Uploaded store(MultipartFile file, long uploaderId, boolean limited) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        if (limited && uploadsToday(uploaderId) >= DAILY_LIMIT) {
            throw new IllegalArgumentException("Слишком много файлов за сутки. Попробуйте завтра или напишите репетитору.");
        }
        String name = cleanName(file.getOriginalFilename());
        FileTypes.Detected type;
        try (InputStream in = file.getInputStream()) {
            type = FileTypes.detect(in.readNBytes(FileTypes.HEAD), name)
                    .orElseThrow(() -> new IllegalArgumentException("Такой тип файла не поддерживается: " + name
                            + ". Можно фото, PDF, документы Word/Excel/PowerPoint, текст, аудио и видео."));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String id = UUID.randomUUID().toString();
        Path target = dir.resolve(id);
        Path tmp = dir.resolve(id + ".part");
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, tmp);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException(e);
        }
        long size = file.getSize();
        jdbc.update("INSERT INTO files (id, name, mime, size_bytes, uploaded_by) VALUES (?, ?, ?, ?, ?)",
                id, name, type.mime(), size, uploaderId);
        return new Uploaded(id, name, type.mime(), size, type.kind());
    }

    /** Загрузил ли пользователь этот файл: прикрепить к ДЗ, материалу или ответу можно только свой. */
    public boolean ownedBy(String fileId, long userId) {
        return ownKind(fileId, userId).isPresent();
    }

    /** Вид части для своего файла; чужой или несуществующий id — пусто. */
    public Optional<ItemKind> ownKind(String fileId, long userId) {
        if (fileId == null || !ID.matcher(fileId).matches()) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT mime FROM files WHERE id = ? AND uploaded_by = ?", String.class, fileId, userId)
                .stream()
                .findFirst()
                .map(FileTypes::kindOf);
    }

    /** Имя файла для показа (без проверки доступа: имя видит тот, кто уже видит часть с этим файлом). */
    public Optional<String> name(String fileId) {
        if (fileId == null || !ID.matcher(fileId).matches()) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT name FROM files WHERE id = ?", String.class, fileId).stream().findFirst();
    }

    /**
     * Уборка: файлы, которые за сутки так и не прикрепили (загрузили и не отправили форму, или часть удалили),
     * и файлы на диске без строки в базе (прерванная загрузка). Возвращает число удалённых.
     */
    public int purgeOrphans() {
        List<String> orphans = jdbc.queryForList("""
                SELECT f.id FROM files f
                WHERE f.created_at < now() - interval '1 day'
                  AND NOT EXISTS (SELECT 1 FROM homework_items h WHERE h.file_id = f.id)
                  AND NOT EXISTS (SELECT 1 FROM submission_items s WHERE s.file_id = f.id)
                  AND NOT EXISTS (SELECT 1 FROM lesson_materials m WHERE m.file_id = f.id)
                """, String.class);
        for (String id : orphans) {
            jdbc.update("DELETE FROM files WHERE id = ?", id);
            deleteQuietly(dir.resolve(id));
        }
        return orphans.size() + purgeStray();
    }

    /** Файлы на диске, которых нет в базе, старше суток. */
    private int purgeStray() {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        Set<String> known = new HashSet<>(jdbc.queryForList("SELECT id FROM files", String.class));
        FileTime before = FileTime.from(Instant.now().minus(ORPHAN_AFTER));
        int removed = 0;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.toList()) {
                String name = p.getFileName().toString();
                if (!known.contains(name) && Files.isRegularFile(p) && Files.getLastModifiedTime(p).compareTo(before) < 0) {
                    deleteQuietly(p);
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("Не удалось просмотреть папку файлов: {}", e.getMessage());
        }
        return removed;
    }

    private int uploadsToday(long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM files WHERE uploaded_by = ? AND created_at > now() - interval '1 day'",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Имя для показа и скачивания: без пути, управляющих символов и лишних пробелов, до 255 символов. */
    static String cleanName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "файл";
        }
        if (name.length() > MAX_NAME) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return name;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Не удалось удалить файл {}: {}", p.getFileName(), e.getMessage());
        }
    }
}
