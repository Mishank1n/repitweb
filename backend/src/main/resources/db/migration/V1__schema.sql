-- Сайт репетитора: схема с нуля (версия с Telegram-ботом — в архиве RepitBotTG-telegram-2026-10-19).
-- Все FK на пользователей и группы с ON DELETE CASCADE: удаление чистит всё связанное.

-- ---------- пользователи и вход ----------

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    -- логин хранится в нижнем регистре: «Anna» и «anna» — один логин
    login               VARCHAR(32)  NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100),
    role                VARCHAR(16)  NOT NULL CHECK (role IN ('TUTOR', 'STUDENT', 'PARENT')),
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'Europe/Moscow',
    consent_at          TIMESTAMPTZ,
    delete_requested_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Репетитор один.
CREATE UNIQUE INDEX uq_users_tutor ON users (role) WHERE role = 'TUTOR';

-- Сессия = вход на одном устройстве. В базе только sha-256 от токена из cookie:
-- утечка базы не даёт войти чужими сессиями.
CREATE TABLE sessions (
    token_hash   CHAR(64)     PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    user_agent   VARCHAR(200)
);

CREATE INDEX idx_sessions_user ON sessions (user_id);
CREATE INDEX idx_sessions_expires ON sessions (expires_at);

-- ---------- группы и приглашения ----------

CREATE TABLE study_groups (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    location_type VARCHAR(16) CHECK (location_type IN ('ONLINE', 'OFFLINE')),
    meeting_url   VARCHAR(500),
    address       VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES study_groups (id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, student_id)
);

CREATE INDEX idx_group_members_student ON group_members (student_id);

CREATE TABLE parent_links (
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parent_id, student_id)
);

CREATE INDEX idx_parent_links_student ON parent_links (student_id);

-- Одноразовые ссылки: STUDENT — вступить в группу, PARENT — привязаться к ученику,
-- RESET — задать новый пароль (выдаёт репетитор, если пароль забыт).
CREATE TABLE invites (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    type       VARCHAR(16) NOT NULL CHECK (type IN ('STUDENT', 'PARENT', 'RESET')),
    group_id   BIGINT REFERENCES study_groups (id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES users (id) ON DELETE CASCADE,
    user_id    BIGINT REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    used_by    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    CHECK ((type = 'STUDENT' AND group_id IS NOT NULL)
        OR (type = 'PARENT' AND student_id IS NOT NULL)
        OR (type = 'RESET' AND user_id IS NOT NULL))
);

-- ---------- файлы ----------

-- Файлы заданий, ответов и материалов. Байты лежат на диске (FILES_DIR/<id>), здесь — описание.
-- Файл, на который ничего не ссылается дольше суток, удаляет ежедневная уборка.
CREATE TABLE files (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    mime        VARCHAR(100) NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    uploaded_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- расписание ----------

-- Регулярное занятие: «еженедельно, пн в 18:00, 60 мин».
CREATE TABLE lesson_series (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT      NOT NULL REFERENCES study_groups (id) ON DELETE CASCADE,
    day_of_week  INT         NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time   TIME        NOT NULL,
    duration_min INT         NOT NULL CHECK (duration_min BETWEEN 15 AND 300),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Конкретное занятие. Из серии создаются на 4 недели вперёд.
CREATE TABLE lessons (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT      NOT NULL REFERENCES study_groups (id) ON DELETE CASCADE,
    series_id    BIGINT REFERENCES lesson_series (id) ON DELETE SET NULL,
    -- исходная дата в серии: перенесённое занятие не будет создано заново
    series_date  DATE,
    starts_at    TIMESTAMPTZ NOT NULL,
    duration_min INT         NOT NULL CHECK (duration_min BETWEEN 15 AND 300),
    status       VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED', 'DONE')),
    -- когда время занятия было назначено в последний раз (создание или перенос)
    time_set_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (series_id, series_date)
);

CREATE INDEX idx_lessons_status_starts ON lessons (status, starts_at);
CREATE INDEX idx_lessons_group_starts ON lessons (group_id, starts_at);

-- Что уже отправлено: напоминания H24/H1, «занятие закончилось» (ENDED), отметки «не смогу прийти» (SKIP).
CREATE TABLE reminder_log (
    lesson_id BIGINT      NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind      VARCHAR(16) NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, user_id, kind)
);

-- Ежедневные задачи выполняются один раз в день, даже если сервер был выключен в назначенное время.
CREATE TABLE daily_job_log (
    job      VARCHAR(32) NOT NULL,
    run_date DATE        NOT NULL,
    ran_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (job, run_date)
);

-- ---------- домашние задания ----------

CREATE TABLE homework (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT       NOT NULL REFERENCES study_groups (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL DEFAULT '',
    max_score  INT          NOT NULL DEFAULT 10 CHECK (max_score BETWEEN 1 AND 1000),
    due_at     TIMESTAMPTZ,
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('DRAFT', 'ISSUED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    issued_at  TIMESTAMPTZ
);

CREATE INDEX idx_homework_group ON homework (group_id, issued_at);
CREATE INDEX idx_homework_due ON homework (status, due_at);

-- Кому выдано ДЗ. Состав фиксируется при выдаче.
CREATE TABLE homework_assignees (
    homework_id BIGINT NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (homework_id, student_id)
);

CREATE INDEX idx_homework_assignees_student ON homework_assignees (student_id);

-- Содержимое задания: текст и файлы (file_id — наш файл из таблицы files).
CREATE TABLE homework_items (
    id          BIGSERIAL PRIMARY KEY,
    homework_id BIGINT      NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    position    INT         NOT NULL,
    kind        VARCHAR(16) NOT NULL CHECK (kind IN ('TEXT', 'PHOTO', 'DOCUMENT', 'VIDEO', 'AUDIO')),
    file_id     VARCHAR(36) REFERENCES files (id),
    text        TEXT
);

-- Одна сдача на ученика и ДЗ; пересдача после доработки — следующая попытка (attempt).
CREATE TABLE submissions (
    id           BIGSERIAL PRIMARY KEY,
    homework_id  BIGINT      NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    student_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'CHECKED', 'RETURNED')),
    attempt      INT         NOT NULL DEFAULT 1,
    submitted_at TIMESTAMPTZ,
    late         BOOLEAN     NOT NULL DEFAULT FALSE,
    score        INT,
    comment      TEXT,
    checked_at   TIMESTAMPTZ,
    UNIQUE (homework_id, student_id)
);

CREATE INDEX idx_submissions_status ON submissions (status, submitted_at);

CREATE TABLE submission_items (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    attempt       INT         NOT NULL,
    position      INT         NOT NULL,
    kind          VARCHAR(16) NOT NULL CHECK (kind IN ('TEXT', 'PHOTO', 'DOCUMENT', 'VIDEO', 'AUDIO')),
    file_id       VARCHAR(36) REFERENCES files (id),
    text          TEXT
);

-- Отправленные напоминания о сроке (DUE24, OVERDUE): защита от дублей.
CREATE TABLE homework_log (
    homework_id BIGINT      NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(16) NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (homework_id, user_id, kind)
);

-- ---------- материалы занятий ----------

CREATE TABLE lesson_materials (
    id         BIGSERIAL PRIMARY KEY,
    lesson_id  BIGINT      NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    position   INT         NOT NULL,
    kind       VARCHAR(16) NOT NULL CHECK (kind IN ('TEXT', 'PHOTO', 'DOCUMENT', 'VIDEO', 'AUDIO')),
    file_id    VARCHAR(36) REFERENCES files (id),
    text       TEXT,
    -- false — добавлено, но ещё не отправлено ученикам (черновик до «Готово»)
    published  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lesson_materials_lesson ON lesson_materials (lesson_id, position);

-- ---------- абонементы и посещаемость ----------

-- Абонемент ученика: оплачено N занятий, счётчик проведённых. Открыт, пока closed_at IS NULL.
-- Счётчик хранится, а не считается по attendance: удаление группы (каскадом её занятий и отметок)
-- не должно незаметно менять оплаченные абонементы.
CREATE TABLE subscriptions (
    id             BIGSERIAL PRIMARY KEY,
    student_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lessons_total  INT         NOT NULL CHECK (lessons_total BETWEEN 1 AND 100),
    lessons_used   INT         NOT NULL DEFAULT 0,
    amount         INT         NOT NULL CHECK (amount BETWEEN 0 AND 10000000),
    paid_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at      TIMESTAMPTZ,
    -- уведомления «осталось 1 занятие» и «абонемент закончился» уже отправлены: повторно не шлём
    low_warned     BOOLEAN     NOT NULL DEFAULT FALSE,
    close_notified BOOLEAN     NOT NULL DEFAULT FALSE,
    CHECK (lessons_used BETWEEN 0 AND lessons_total)
);

-- У ученика не больше одного открытого абонемента.
CREATE UNIQUE INDEX uq_subscriptions_open ON subscriptions (student_id) WHERE closed_at IS NULL;
CREATE INDEX idx_subscriptions_student ON subscriptions (student_id, paid_at);

-- Отметка ученика на занятии. subscription_id заполнен, если занятие списано из абонемента.
CREATE TABLE attendance (
    lesson_id       BIGINT      NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    student_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT_CHARGED', 'ABSENT_FREE')),
    subscription_id BIGINT REFERENCES subscriptions (id) ON DELETE SET NULL,
    marked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (lesson_id, student_id)
);

CREATE INDEX idx_attendance_student ON attendance (student_id);
CREATE INDEX idx_attendance_subscription ON attendance (subscription_id);

-- ---------- отчёты родителям ----------

-- Строка создаётся, когда репетитору предлагают дописать комментарии; sent_at — после отправки.
CREATE TABLE weekly_reports (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- первый день отчётного периода (7 дней, последний — день отчёта)
    week_start DATE        NOT NULL,
    comment    TEXT,
    sent_at    TIMESTAMPTZ,
    UNIQUE (student_id, week_start)
);

-- ---------- уведомления ----------

-- Лента уведомлений на сайте: всё, что раньше бот писал в чат. Push — дополнительный канал,
-- поэтому даже недошедший push остаётся здесь.
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL DEFAULT '',
    -- экран сайта, который открывается по нажатию (/hw/12, /lesson/5…)
    url        VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- Подписки браузеров на web push (одна на устройство и браузер).
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   VARCHAR(1000) NOT NULL UNIQUE,
    p256dh     VARCHAR(200) NOT NULL,
    auth       VARCHAR(100) NOT NULL,
    user_agent VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
