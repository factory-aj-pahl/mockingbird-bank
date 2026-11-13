package com.mockingbirdbank.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Feature flags, bound from {@code mockingbird.feature-flags.*} in {@code application.yml} (or the
 * matching {@code MOCKINGBIRD_FEATURE_FLAGS_*} env var, e.g. {@code
 * MOCKINGBIRD_FEATURE_FLAGS_SHOW_WELCOME_BANNER=true} - note the underscore between each word: env
 * vars are always upper-case, so Spring's relaxed binding needs that separator to recover word
 * boundaries; {@code SHOWWELCOMEBANNER} with no separators will not bind). Each flag defaults to
 * today's actual behavior, so adding a new one here is opt-in and changes nothing until it's
 * explicitly flipped in a specific environment - unless its own comment says otherwise. See
 * AGENTS.md's "Feature flags" section.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "mockingbird.feature-flags")
public class FeatureFlagsProperties {

    /** Whether the dashboard's account cards show the masked account number. */
    private boolean showAccountNumberOnDashboard = true;

    /** Whether the dashboard shows a "Welcome back" banner above the balance summary. */
    private boolean showWelcomeBanner = false;

    /**
     * Whether masked account numbers get an eye button that fetches and shows the full number on
     * request (per account, plus a dashboard-wide "show all").
     */
    private boolean allowAccountNumberReveal = true;

    /**
     * Whether the top bar shows which build is running (version and commit). Deliberately defaults
     * to on, an exception to the "defaults match today's behavior" rule above: knowing exactly what
     * is deployed is useful in every environment, and the top bar is only shown after sign-in.
     */
    private boolean showBuildVersion = true;

    /**
     * Whether the dashboard shows a "You have N accounts" line under its "Your accounts" heading.
     */
    private boolean showAccountCount = false;

    /**
     * Whether the dashboard and account pages show the signed-in customer's programs (Regular,
     * Youth Savings): the accounts in each one and a message for every unmet program rule.
     */
    private boolean showAccountPrograms = false;
}
