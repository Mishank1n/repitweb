package ru.repitbot.domain.material;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.LessonRepository;
import ru.repitbot.domain.schedule.LessonStatus;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Материалы занятия: репетитор добавляет части (черновик), «Готово» публикует их для группы.
 * Видят опубликованное текущие участники группы занятия и их родители.
 */
@Service
public class MaterialService {

    public static final int MAX_PER_LESSON = 50;

    /** Сколько частей уже видят ученики и сколько ждут «Готово». */
    public record Counts(long published, long pending) {
        public long total() {
            return published + pending;
        }
    }

    /**
     * Итог публикации.
     *
     * @param added     сколько частей опубликовано сейчас
     * @param firstTime до этого у занятия не было опубликованных материалов
     */
    public record Published(Lesson lesson, int added, boolean firstTime) {
    }

    private final LessonMaterialRepository materials;
    private final LessonRepository lessons;
    private final GroupService groups;
    private final UserService users;

    public MaterialService(LessonMaterialRepository materials, LessonRepository lessons, GroupService groups,
                           UserService users) {
        this.materials = materials;
        this.lessons = lessons;
        this.groups = groups;
        this.users = users;
    }

    /** Добавляет неопубликованную часть; возвращает число частей, ждущих «Готово». */
    @Transactional
    public long addPart(long lessonId, Part part) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NoSuchElementException("Занятие не найдено"));
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new IllegalStateException("Занятие отменено");
        }
        if (materials.countByLesson_Id(lessonId) >= MAX_PER_LESSON) {
            throw new IllegalStateException("Не больше " + MAX_PER_LESSON + " частей материалов на одно занятие");
        }
        materials.save(new LessonMaterial(lesson, materials.maxPosition(lessonId) + 1, part));
        return materials.countByLesson_IdAndPublished(lessonId, false);
    }

    @Transactional
    public Published publish(long lessonId) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NoSuchElementException("Занятие не найдено"));
        boolean firstTime = materials.countByLesson_IdAndPublished(lessonId, true) == 0;
        List<LessonMaterial> pending = materials.findByLesson_IdAndPublishedOrderByPositionAsc(lessonId, false);
        pending.forEach(LessonMaterial::publish);
        return new Published(lesson, pending.size(), firstTime);
    }

    /** «Отмена» при сборе: удаляет то, что ещё не отправлено ученикам. */
    @Transactional
    public void discardPending(long lessonId) {
        materials.deleteAll(materials.findByLesson_IdAndPublishedOrderByPositionAsc(lessonId, false));
    }

    @Transactional(readOnly = true)
    public Optional<LessonMaterial> find(long materialId) {
        return materials.findById(materialId);
    }

    @Transactional
    public Optional<LessonMaterial> delete(long materialId) {
        Optional<LessonMaterial> m = materials.findById(materialId);
        m.ifPresent(materials::delete);
        return m;
    }

    /** Все части занятия для репетитора, включая неопубликованные. */
    @Transactional(readOnly = true)
    public List<LessonMaterial> all(long lessonId) {
        return materials.findByLesson_IdOrderByPositionAsc(lessonId);
    }

    @Transactional(readOnly = true)
    public List<Part> publishedParts(long lessonId) {
        return materials.findByLesson_IdAndPublishedOrderByPositionAsc(lessonId, true).stream()
                .map(LessonMaterial::toPart)
                .toList();
    }

    @Transactional(readOnly = true)
    public Counts counts(long lessonId) {
        return new Counts(materials.countByLesson_IdAndPublished(lessonId, true),
                materials.countByLesson_IdAndPublished(lessonId, false));
    }

    @Transactional(readOnly = true)
    public List<Lesson> lessonsWithMaterials(Collection<Long> groupIds, int limit) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return materials.lessonsWithPublished(groupIds, PageRequest.of(0, limit));
    }

    /**
     * Может ли пользователь открыть материалы занятия: ученик — участник группы занятия,
     * родитель — родитель такого участника. Оба с действующим согласием. Callback можно подделать,
     * поэтому проверка здесь, а не в кнопках.
     */
    @Transactional(readOnly = true)
    public boolean canView(User user, Lesson lesson) {
        if (!users.canMessage(user)) {
            return false;
        }
        long groupId = lesson.getGroup().getId();
        if (user.getRole() == Role.STUDENT) {
            return groups.isMember(groupId, user.getId());
        }
        return user.getRole() == Role.PARENT && groups.childrenOf(user.getId()).stream()
                .anyMatch(child -> users.canMessage(child) && groups.isMember(groupId, child.getId()));
    }
}
