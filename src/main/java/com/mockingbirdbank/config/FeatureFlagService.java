package com.mockingbirdbank.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Read-only facade over {@link FeatureFlagsProperties}: typed accessors for call sites (so a UI
 * class asks {@code featureFlagService.showWelcomeBanner()} rather than reaching into a properties
 * bean directly), plus {@link #allFlags()} backing the ops-facing {@code /actuator/featureflags}
 * endpoint - one place to add a new flag rather than several.
 */
@Service
public class FeatureFlagService {

    private final FeatureFlagsProperties properties;

    public FeatureFlagService(FeatureFlagsProperties properties) {
        this.properties = properties;
    }

    public boolean showAccountNumberOnDashboard() {
        return properties.isShowAccountNumberOnDashboard();
    }

    public boolean showWelcomeBanner() {
        return properties.isShowWelcomeBanner();
    }

    public boolean allowAccountNumberReveal() {
        return properties.isAllowAccountNumberReveal();
    }

    public boolean showBuildVersion() {
        return properties.isShowBuildVersion();
    }

    public boolean showAccountCount() {
        return properties.isShowAccountCount();
    }

    public boolean showAccountPrograms() {
        return properties.isShowAccountPrograms();
    }

    /** All known flags and their current values, in declaration order. */
    public Map<String, Boolean> allFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("showAccountNumberOnDashboard", showAccountNumberOnDashboard());
        flags.put("showWelcomeBanner", showWelcomeBanner());
        flags.put("allowAccountNumberReveal", allowAccountNumberReveal());
        flags.put("showBuildVersion", showBuildVersion());
        flags.put("showAccountCount", showAccountCount());
        flags.put("showAccountPrograms", showAccountPrograms());
        return flags;
    }
}
