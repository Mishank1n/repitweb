-- Ключи приложения, которые сервер создаёт сам при первом запуске (пара VAPID для web push).
-- Ключ должен жить долго: подписки браузеров привязаны к открытому ключу, при смене все подписки пропадут.
CREATE TABLE app_keys (
    name       VARCHAR(50) PRIMARY KEY,
    value      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
