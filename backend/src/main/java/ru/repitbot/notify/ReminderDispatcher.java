package ru.repitbot.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Раз в минуту: напоминания о занятиях и ежедневные задачи.
 * Ничего не планируется заранее — всё вычисляется из расписания, поэтому переносы,
 * новые ученики и сон ноутбука обрабатываются сами собой.
 */
@Component
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);
    private static final Duration LOOKAHEAD = ReminderRules.DAY.plusMinutes(5);

    private final ScheduleService schedule;
    private final LessonNotifier notifier;
    private final DailyJobs dailyJobs;
    private final HomeworkNotifier homeworkNotifier;
    private final WeeklyReports weeklyReports;

    public ReminderDispatcher(ScheduleService schedule, LessonNotifier notifier, DailyJobs dailyJobs,
                              HomeworkNotifier homeworkNotifier, WeeklyReports weeklyReports) {
        this.homeworkNotifier = homeworkNotifier;
        this.weeklyReports = weeklyReports;
        this.schedule = schedule;
        this.notifier = notifier;
        this.dailyJobs = dailyJobs;
    }

    @Scheduled(initialDelay = 20, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        try {
            sendDueReminders(Instant.now());
        } catch (Exception e) {
            log.error("Ошибка рассылки напоминаний", e);
        }
        try {
            homeworkNotifier.sendDueReminders(Instant.now());
        } catch (Exception e) {
            log.error("Ошибка напоминаний о сроке ДЗ", e);
        }
        try {
            sendEndedPings(Instant.now());
        } catch (Exception e) {
            log.error("Ошибка сообщений о конце занятия", e);
        }
        try {
            weeklyReports.runDue();
        } catch (Exception e) {
            log.error("Ошибка недельных отчётов", e);
        }
        try {
            dailyJobs.runDue();
        } catch (Exception e) {
            log.error("Ошибка ежедневных задач", e);
        }
    }

    void sendDueReminders(Instant now) {
        for (Lesson lesson : schedule.scheduled(now, now.plus(LOOKAHEAD))) {
            ReminderRules.due(lesson.getStartsAt(), lesson.getTimeSetAt(), now)
                    .ifPresent(kind -> notifier.sendReminder(lesson, kind));
        }
    }

    void sendEndedPings(Instant now) {
        Instant from = now.minus(LessonEndRules.WINDOW).minus(LessonEndRules.MAX_DURATION);
        for (Lesson lesson : schedule.scheduled(from, now)) {
            if (LessonEndRules.ended(lesson.getStartsAt(), lesson.getDurationMin(), now)) {
                notifier.lessonEnded(lesson);
            }
        }
    }
}
