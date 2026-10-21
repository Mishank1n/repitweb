package ru.repitbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Сайт — одна страница: адреса вроде /join/CODE или /hw/12 открывают index.html, дальше разбирается фронтенд.
 * Существующие файлы (скрипты, шрифты) отдаются как есть; /api/** и адреса с точкой (несуществующие файлы) — 404.
 * Кэш: index.html браузер перепроверяет при каждом открытии, иначе после обновления сайта он показывает старую
 * версию; файлы из /assets/ с хэшем в имени не меняются никогда (новая сборка — новое имя), их можно держать год.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource found = location.createRelative(path);
                        if (found.exists() && found.isReadable()) {
                            return found;
                        }
                        if (path.startsWith("api/") || path.startsWith("actuator/") || path.contains(".")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
