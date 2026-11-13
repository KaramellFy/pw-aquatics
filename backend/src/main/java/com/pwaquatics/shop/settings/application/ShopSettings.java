package com.pwaquatics.shop.settings.application;

import com.pwaquatics.shop.settings.domain.ShopSetting;
import com.pwaquatics.shop.settings.infrastructure.ShopSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Shop-wide settings for other modules: they read them here and save the ones they own (the
 * shipping module saves {@code shipping.pause}, for example). Who changed a setting is
 * recorded in the row and, by the caller, in the audit log.
 */
@Service
public class ShopSettings {

    private static final Logger log = LoggerFactory.getLogger(ShopSettings.class);

    private final ShopSettingRepository repository;
    private final ObjectMapper json;

    private final Clock clock;

    ShopSettings(ShopSettingRepository repository, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Stores {@code value} as JSON under {@code key}, replacing what was there.
     *
     * @param updatedBy the administrator making the change
     */
    @Transactional
    public void save(String key, Object value, UUID updatedBy) {
        String text = json.writeValueAsString(value);
        Instant now = clock.instant();
        repository.findById(key).ifPresentOrElse(
                setting -> setting.change(text, updatedBy, now),
                () -> repository.save(new ShopSetting(key, text, updatedBy, now)));
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
