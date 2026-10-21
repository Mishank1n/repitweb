# Контекст сборки — корень проекта (см. docker-compose.yml): нужны и backend/, и web/.
# Скачанные npm- и Gradle-зависимости лежат в постоянном кэше сборки (--mount=type=cache): он переживает
# пересборки, поэтому повторная сборка ничего не качает заново, даже если слои образа пересобираются.

# 1. Сайт: проверка типов и сборка Vite.
FROM node:22-alpine3.23 AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,id=repitbot-npm,target=/root/.npm npm ci --ignore-scripts
COPY web/ ./
RUN npm run build

# 2. Бэкенд: тесты и jar; собранный сайт кладётся в статику Spring.
FROM gradle:9.8.1-jdk21-noble AS build
# В образе gradle папка ~/.gradle — том (VOLUME): всё, что сборка туда пишет, Docker выбрасывает
# после шага. Поэтому кэш Gradle — в своей папке, подключённой как постоянный кэш сборки.
ENV GRADLE_USER_HOME=/cache/gradle
WORKDIR /src
COPY backend/settings.gradle backend/build.gradle ./
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN --mount=type=cache,id=repitbot-gradle,target=/cache/gradle gradle test bootJar --no-daemon

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/files && chown app:app /data/files
WORKDIR /app
COPY --from=build /src/build/libs/app.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
