package com.pwaquatics.shop.media;

import com.pwaquatics.shop.common.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded media from the local media directory under {@code /media/**}.
 * In production Caddy may serve this directory directly; this handler keeps
 * development and single-container deployments working.
 */
@Configuration(proxyBeanMethods = false)
class MediaConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    MediaConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.media().localPath()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(properties.media().publicPath() + "/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                // Renditions are immutable: a changed image gets a new storage key.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
