package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.AppSettingsDto;
import com.shopbilling.model.AppSetting;
import com.shopbilling.repository.AppSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class AppSettingsService {
    private final AppSettingRepository settings;

    public AppSettingsService(AppSettingRepository settings) {
        this.settings = settings;
    }

    public AppSettingsDto readSettings() {
        return new AppSettingsDto(
                setting("shopName", "Shiv Shakti Krishi Sewa Kendra"),
                setting("shopLogoUrl", ""),
                setting("shopAddress", "Betul"),
                setting("gstNumber", ""),
                setting("upiId", ""),
                setting("invoiceFooter", "Thank you for your business."),
                setting("defaultGstPercent", "0"),
                setting("defaultLowStockAlert", "5"),
                setting("backupPath", "data/backups"));
    }

    public void saveSettings(AppSettingsDto request) {
        writeSetting("shopName", request.shopName());
        writeSetting("shopLogoUrl", request.shopLogoUrl());
        writeSetting("shopAddress", request.shopAddress());
        writeSetting("gstNumber", request.gstNumber());
        writeSetting("upiId", request.upiId());
        writeSetting("invoiceFooter", request.invoiceFooter());
        writeSetting("defaultGstPercent", request.defaultGstPercent());
        writeSetting("defaultLowStockAlert", request.defaultLowStockAlert());
        writeSetting("backupPath", request.backupPath());
    }

    public String setting(String key, String fallback) {
        return settings.findById(key).map(AppSetting::getSettingValue).orElse(fallback);
    }

    private void writeSetting(String key, String value) {
        AppSetting setting = settings.findById(key).orElseGet(AppSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value);
        settings.save(setting);
    }
}
