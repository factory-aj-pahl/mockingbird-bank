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
    }

    @Test
    void reflectsOverriddenProperties() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowAccountNumberOnDashboard(false);
        properties.setShowWelcomeBanner(true);
        properties.setAllowAccountNumberReveal(false);

        FeatureFlagService service = new FeatureFlagService(properties);

        assertThat(service.showAccountNumberOnDashboard()).isFalse();
        assertThat(service.showWelcomeBanner()).isTrue();
        assertThat(service.allowAccountNumberReveal()).isFalse();
    }

    @Test
    void allFlagsReflectsCurrentValues() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowWelcomeBanner(true);

        FeatureFlagService service = new FeatureFlagService(properties);

        assertThat(service.allFlags())
                .containsEntry("showAccountNumberOnDashboard", true)
                .containsEntry("showWelcomeBanner", true)
                .containsEntry("allowAccountNumberReveal", true);
    }
}
