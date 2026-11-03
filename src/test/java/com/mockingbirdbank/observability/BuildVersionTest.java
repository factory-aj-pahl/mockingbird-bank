package com.mockingbirdbank.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

class BuildVersionTest {

    private static final String FULL_COMMIT = "09f771a2d17cc1266a44474a9dfd368ecb3abd46";

    @Test
    void readsVersionCommitBranchAndBuildTimeFromTheGeneratedProperties() {
        BuildVersion buildVersion =
                new BuildVersion(
                        Optional.of(build("0.1.0-09f771a", "2026-11-03T13:36:11Z")),
                        Optional.of(git("master")));

        assertThat(buildVersion.version()).isEqualTo("0.1.0-09f771a");
        assertThat(buildVersion.commit()).isEqualTo("09f771a");
        assertThat(buildVersion.branch()).isEqualTo("master");
        assertThat(buildVersion.buildTime()).contains(Instant.parse("2026-11-03T13:36:11Z"));
        assertThat(buildVersion.details())
                .isEqualTo("commit 09f771a, branch master, built 2026-11-03T13:36:11Z");
    }

    @Test
    void displayVersionIsJustTheVersionWhenItAlreadyNamesTheCommit() {
        BuildVersion buildVersion =
                new BuildVersion(
                        Optional.of(build("0.1.0-09f771a-dirty", null)),
                        Optional.of(git("master")));

        assertThat(buildVersion.displayVersion()).isEqualTo("0.1.0-09f771a-dirty");
    }

    @Test
    void displayVersionAddsTheCommitForAReleaseVersion() {
        BuildVersion buildVersion =
                new BuildVersion(Optional.of(build("0.2.0", null)), Optional.of(git("detached")));

        assertThat(buildVersion.displayVersion()).isEqualTo("0.2.0 (09f771a)");
    }

    @Test
    void detailsLeaveOutBuildTimeWhenItWasNotRecorded() {
        BuildVersion buildVersion =
                new BuildVersion(
                        Optional.of(build("0.1.0-09f771a", null)), Optional.of(git("master")));

        assertThat(buildVersion.buildTime()).isEmpty();
        assertThat(buildVersion.details()).isEqualTo("commit 09f771a, branch master");
    }

    @Test
    void missingMetadataReadsUnknownInsteadOfFailing() {
        BuildVersion buildVersion = new BuildVersion(Optional.empty(), Optional.empty());

        assertThat(buildVersion.version()).isEqualTo(BuildVersion.UNKNOWN);
        assertThat(buildVersion.commit()).isEqualTo(BuildVersion.UNKNOWN);
        assertThat(buildVersion.branch()).isEqualTo(BuildVersion.UNKNOWN);
        assertThat(buildVersion.displayVersion()).isEqualTo(BuildVersion.UNKNOWN);
        assertThat(buildVersion.details()).isEqualTo("commit unknown, branch unknown");
    }

    @Test
    void logsTheRunningVersionOnceTheAppIsReady() {
        Logger logger = (Logger) LoggerFactory.getLogger(BuildVersion.class);
        ListAppender<ILoggingEvent> capturedLogs = new ListAppender<>();
        capturedLogs.start();
        logger.addAppender(capturedLogs);
        try {
            BuildVersion buildVersion =
                    new BuildVersion(
                            Optional.of(build("0.1.0-09f771a", null)), Optional.of(git("master")));

            buildVersion.logRunningVersion();

            assertThat(capturedLogs.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly(
                            "Mockingbird Bank 0.1.0-09f771a started (commit 09f771a, branch"
                                    + " master)");
        } finally {
            logger.detachAppender(capturedLogs);
        }
    }

    private static BuildProperties build(String version, String time) {
        Properties entries = new Properties();
        entries.setProperty("version", version);
        if (time != null) {
            entries.setProperty("time", time);
        }
        return new BuildProperties(entries);
    }

    private static GitProperties git(String branch) {
        Properties entries = new Properties();
        entries.setProperty("commit.id", FULL_COMMIT);
        entries.setProperty("commit.id.abbrev", "09f771a");
        entries.setProperty("branch", branch);
        return new GitProperties(entries);
    }
}
