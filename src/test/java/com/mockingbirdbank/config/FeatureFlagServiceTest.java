package com.mockingbirdbank.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest {

    @Test
    void defaultsMatchTodaysActualBehavior() {
        FeatureFlagService service = new FeatureFlagService(new FeatureFlagsProperties());

        assertThat(service.showAccountNumberOnDashboard()).isTrue();
        assertThat(service.showWelcomeBanner()).isFalse();
        assertThat(service.allowAccountNumberReveal()).isTrue();
        assertThat(service.showBuildVersion()).isTrue();
        assertThat(service.showAccountCount()).isFalse();
    }

    @Test
    void reflectsOverriddenProperties() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowAccountNumberOnDashboard(false);
        properties.setShowWelcomeBanner(true);
        properties.setAllowAccountNumberReveal(false);
        properties.setShowBuildVersion(false);
        properties.setShowAccountCount(true);

        FeatureFlagService service = new FeatureFlagService(properties);

        assertThat(service.showAccountNumberOnDashboard()).isFalse();
        assertThat(service.showWelcomeBanner()).isTrue();
        assertThat(service.allowAccountNumberReveal()).isFalse();
        assertThat(service.showBuildVersion()).isFalse();
        assertThat(service.showAccountCount()).isTrue();
    }

    @Test
    void allFlagsReflectsCurrentValues() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowWelcomeBanner(true);

        FeatureFlagService service = new FeatureFlagService(properties);

        assertThat(service.allFlags())
                .containsEntry("showAccountNumberOnDashboard", true)
                .containsEntry("showWelcomeBanner", true)
                .containsEntry("allowAccountNumberReveal", true)
                .containsEntry("showBuildVersion", true)
                .containsEntry("showAccountCount", false);
    }
}
