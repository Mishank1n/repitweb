package ru.repitbot.domain.schedule;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.group.StudyGroupRepository;
import ru.repitbot.notify.SendLog;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleService {

    /** На сколько вперёд создаются занятия регулярной серии. */
    public static final int HORIZON_DAYS = 28;

    private final LessonRepository lessons;
    private final LessonSeriesRepository series;
    private final StudyGroupRepository groups;
    private final SendLog sendLog;
    private final ZoneId zone;

    public ScheduleService(LessonRepository lessons, LessonSeriesRepository series, StudyGroupRepository groups,
                           SendLog sendLog, AppProperties props) {
        this.lessons = lessons;
        this.series = series;
        this.groups = groups;
        this.sendLog = sendLog;
        this.zone = props.zone();
    }

    /** Итог переноса: старое время нужно для уведомления «было → стало». */
    public record Moved(Lesson lesson, Instant oldStart) {
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    /** Даты дня недели в полуинтервале [from, toExclusive). */
    static List<LocalDate> occurrences(DayOfWeek day, LocalDate from, LocalDate toExclusive) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from.with(TemporalAdjusters.nextOrSame(day)); d.isBefore(toExclusive); d = d.plusWeeks(1)) {
            dates.add(d);
        }
        return dates;
    }

    @Transactional
    public LessonSeries createSeries(long groupId, DayOfWeek day, LocalTime time, int durationMin) {
        StudyGroup group = groups.findById(groupId).orElseThrow();
        LessonSeries created = series.save(new LessonSeries(group, day, time, durationMin));
        generate(created, Instant.now());
        return created;
    }

    /** Продлевает все активные серии на {@link #HORIZON_DAYS} дней вперёд. Повторный вызов ничего не дублирует. */
    @Transactional
    public int extendAllSeries() {
        Instant now = Instant.now();
        int created = 0;
        for (LessonSeries s : series.findByActiveTrue()) {
            created += generate(s, now);
        }
        return created;
    }

    private int generate(LessonSeries s, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        int created = 0;
        for (LocalDate date : occurrences(s.getDay(), today, today.plusDays(HORIZON_DAYS))) {
            Instant startsAt = toInstant(date, s.getStartTime());
            if (startsAt.isAfter(now) && !lessons.existsBySeries_IdAndSeriesDate(s.getId(), date)) {
                lessons.save(Lesson.fromSeries(s, date, startsAt));
                created++;
            }
        }
        return created;
    }

    /** Отключает серию и удаляет её будущие занятия. Возвращает серию для уведомления участников. */
    @Transactional
    public Optional<LessonSeries> deactivateSeries(long seriesId) {
        Optional<LessonSeries> found = series.findById(seriesId).filter(LessonSeries::isActive);
        found.ifPresent(s -> {
            s.deactivate();
            lessons.deleteAll(lessons.findBySeries_IdAndStatusAndStartsAtAfter(s.getId(), LessonStatus.SCHEDULED, Instant.now()));
        });
        return found;
    }

    @Transactional
    public Lesson createOneOff(long groupId, LocalDate date, LocalTime time, int durationMin) {
        Instant startsAt = toInstant(date, time);
        requireFuture(startsAt);
        StudyGroup group = groups.findById(groupId).orElseThrow();
        return lessons.save(Lesson.oneOff(group, startsAt, durationMin));
    }

    @Transactional
    public Moved reschedule(long lessonId, LocalDate date, LocalTime time) {
        Instant newStart = toInstant(date, time);
        requireFuture(newStart);
        Lesson lesson = lessons.findById(lessonId)
                .filter(l -> l.getStatus() == LessonStatus.SCHEDULED)
                .orElseThrow(() -> new IllegalArgumentException("Занятие уже отменено или проведено"));
        Instant old = lesson.getStartsAt();
        lesson.reschedule(newStart);
        sendLog.clear(lessonId);
        return new Moved(lesson, old);
    }

    @Transactional
    public Optional<Lesson> cancel(long lessonId) {
        Optional<Lesson> found = lessons.findById(lessonId).filter(l -> l.isUpcoming(Instant.now()));
        found.ifPresent(Lesson::cancel);
        return found;
    }

    @Transactional(readOnly = true)
    public Optional<Lesson> find(long lessonId) {
        return lessons.findById(lessonId);
    }

    @Transactional(readOnly = true)
    public Optional<LessonSeries> findSeries(long seriesId) {
        return series.findById(seriesId);
    }

    @Transactional(readOnly = true)
    public List<LessonSeries> activeSeries(long groupId) {
        return series.findByGroup_IdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(groupId);
    }

    /** Запланированные занятия в интервале [from, to]. */
    @Transactional(readOnly = true)
    public List<Lesson> scheduled(Instant from, Instant to) {
        return lessons.findByStatusAndStartsAtBetweenOrderByStartsAtAsc(LessonStatus.SCHEDULED, from, to);
    }

    @Transactional(readOnly = true)
    public List<Lesson> upcomingForGroups(Collection<Long> groupIds, int days) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return lessons.findByGroup_IdInAndStatusAndStartsAtBetweenOrderByStartsAtAsc(
                groupIds, LessonStatus.SCHEDULED, now, now.plus(Duration.ofDays(days)));
    }

    /** Неотменённые занятия (запланированные и проведённые) в интервале [from, to], по времени. */
    @Transactional(readOnly = true)
    public List<Lesson> between(Instant from, Instant to) {
        List<Lesson> result = new ArrayList<>(lessons.findByStatusAndStartsAtBetweenOrderByStartsAtAsc(LessonStatus.SCHEDULED, from, to));
        result.addAll(lessons.findByStatusAndStartsAtBetweenOrderByStartsAtAsc(LessonStatus.DONE, from, to));
        result.sort(Comparator.comparing(Lesson::getStartsAt));
        return result;
    }

    /** Запланированные занятия, которые ещё не закончились (идут сейчас или впереди), до момента to. */
    @Transactional(readOnly = true)
    public List<Lesson> notEnded(Instant to) {
        Instant now = Instant.now();
        return scheduled(now.minus(Lesson.MAX_DURATION), to).stream().filter(l -> !l.hasEnded(now)).toList();
    }

    /** Как {@link #upcomingForGroups}, но вместе с занятиями, которые идут прямо сейчас. */
    @Transactional(readOnly = true)
    public List<Lesson> notEndedForGroups(Collection<Long> groupIds, int days) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return lessons.findByGroup_IdInAndStatusAndStartsAtBetweenOrderByStartsAtAsc(
                        groupIds, LessonStatus.SCHEDULED, now.minus(Lesson.MAX_DURATION), now.plus(Duration.ofDays(days)))
                .stream().filter(l -> !l.hasEnded(now)).toList();
    }

    /**
     * Последние 10 закончившихся неотменённых занятий группы (отмеченные и нет), сначала самые свежие.
     * Идущее сейчас сюда не входит.
     */
    @Transactional(readOnly = true)
    public List<Lesson> pastForGroup(long groupId) {
        Instant now = Instant.now();
        return lessons.findTop15ByGroup_IdAndStatusInAndStartsAtBeforeOrderByStartsAtDesc(
                        groupId, List.of(LessonStatus.SCHEDULED, LessonStatus.DONE), now)
                .stream().filter(l -> l.hasEnded(now)).limit(10).toList();
    }

    /** Закончившиеся за последние days дней занятия без отметки посещаемости. */
    @Transactional(readOnly = true)
    public List<Lesson> unmarked(int days) {
        Instant now = Instant.now();
        return scheduled(now.minus(Duration.ofDays(days)), now).stream().filter(l -> l.hasEnded(now)).toList();
    }

    /** Все занятия дня по часовому поясу репетитора, включая уже прошедшие. */
    @Transactional(readOnly = true)
    public List<Lesson> onDay(LocalDate date) {
        return scheduled(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static void requireFuture(Instant startsAt) {
        if (!startsAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Это время уже прошло");
        }
    }
}
