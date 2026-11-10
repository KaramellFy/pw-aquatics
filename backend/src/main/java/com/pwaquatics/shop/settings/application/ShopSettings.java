package com.pwaquatics.shop.settings.application;

import com.pwaquatics.shop.settings.domain.ShopSetting;
import com.pwaquatics.shop.settings.infrastructure.ShopSettingRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Read access to shop-wide settings for other modules. Settings are edited in the admin
 * area (a later phase); until then they can only be changed in the database.
 */
@Service
public class ShopSettings {

    private static final Logger log = LoggerFactory.getLogger(ShopSettings.class);

    private final ShopSettingRepository repository;
    private final ObjectMapper json;

    ShopSettings(ShopSettingRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /**
     * The setting stored under {@code key}, read as {@code type}. A missing or unreadable
     * value yields an empty result (and a log entry), so a typo in a setting can never take
     * the shop down.
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> find(String key, Class<T> type) {
        return repository.findById(key).map(ShopSetting::getValue).flatMap(value -> {
            try {
                return Optional.ofNullable(json.readValue(value, type));
            } catch (JacksonException e) {
                log.warn("Shop setting '{}' cannot be read as {}: {}", key, type.getSimpleName(), e.getOriginalMessage());
                return Optional.empty();
            }
        });
    }
}
