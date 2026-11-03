package com.mockingbirdbank.observability;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Which build of the app is running: version, commit, branch and (for image builds) build time,
 * read from the {@code build-info.properties} / {@code git.properties} the Gradle build generates -
 * the same data {@code /actuator/info} serves. Backs the version label in the top bar, and logs one
 * line once the app is ready so {@code kubectl logs} answers "what's deployed" too. Either file can
 * be missing (e.g. running from an IDE without Gradle), in which case its values read "unknown".
 */
@Component
public class BuildVersion {

    static final String UNKNOWN = "unknown";

    private static final Logger log = LoggerFactory.getLogger(BuildVersion.class);

    private final String version;
    private final String commit;
    private final String branch;
    private final Optional<Instant> buildTime;

    public BuildVersion(Optional<BuildProperties> build, Optional<GitProperties> git) {
        this.version = build.map(BuildProperties::getVersion).orElse(UNKNOWN);
        this.commit = git.map(GitProperties::getShortCommitId).orElse(UNKNOWN);
        this.branch = git.map(GitProperties::getBranch).orElse(UNKNOWN);
        this.buildTime = build.map(BuildProperties::getTime);
    }

    public String version() {
        return version;
    }

    public String commit() {
        return commit;
    }

    public String branch() {
        return branch;
    }

    public Optional<Instant> buildTime() {
        return buildTime;
    }

    /**
     * The short form for display: the version alone when it already names the commit (every
     * non-release build, e.g. {@code 0.1.0-09f771a}), otherwise the version with the commit
     * alongside (a tagged release, e.g. {@code 0.2.0 (09f771a)}).
     */
    public String displayVersion() {
        if (UNKNOWN.equals(commit) || version.contains(commit)) {
            return version;
        }
        return version + " (" + commit + ")";
    }

    /** E.g. {@code commit 09f771a, branch master, built 2026-11-03T13:36:11Z}. */
    public String details() {
        StringBuilder details =
                new StringBuilder("commit ").append(commit).append(", branch ").append(branch);
        buildTime.ifPresent(time -> details.append(", built ").append(time));
        return details.toString();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRunningVersion() {
        log.info("Mockingbird Bank {} started ({})", version, details());
    }
}
