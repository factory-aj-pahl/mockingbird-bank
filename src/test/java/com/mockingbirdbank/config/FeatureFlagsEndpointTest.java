package com.mockingbirdbank.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagsEndpointTest {

    @Test
    void flagsDelegatesToFeatureFlagService() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowWelcomeBanner(true);
        FeatureFlagsEndpoint endpoint =
                new FeatureFlagsEndpoint(new FeatureFlagService(properties));

        assertThat(endpoint.flags())
                .containsEntry("showAccountNumberOnDashboard", true)
                .containsEntry("showWelcomeBanner", true)
                .containsEntry("showBuildVersion", true)
                .containsEntry("showAccountCount", false)
                .containsEntry("showAccountPrograms", false);
    }
}
