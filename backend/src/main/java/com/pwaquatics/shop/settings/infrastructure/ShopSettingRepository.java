package com.pwaquatics.shop.settings.infrastructure;

import com.pwaquatics.shop.settings.domain.ShopSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingRepository extends JpaRepository<ShopSetting, String> {}
