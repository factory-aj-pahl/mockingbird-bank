# Build stage - gradlew pins the exact Gradle version (see
# gradle/wrapper/gradle-wrapper.properties), so any JDK 21 image works
# here; no need for a Gradle-specific base image, and no risk of the
# build using a different Gradle version than local/CI does.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# .dockerignore keeps .git out of the build context, so the commit/version
# the app reports (/actuator/info, startup log, top bar) arrive as build args
# instead - scripts/build-metadata.sh computes them for every image build
# path. Leaving them unset still builds; the app just reports "unknown".
ARG APP_VERSION=
ARG GIT_COMMIT=
ARG GIT_BRANCH=
ARG GIT_DIRTY=
ARG GIT_COMMIT_TIME=
RUN ./gradlew --no-daemon -Pvaadin.productionMode=true \
        -PappVersion="$APP_VERSION" \
        -PgitCommit="$GIT_COMMIT" \
        -PgitBranch="$GIT_BRANCH" \
        -PgitDirty="$GIT_DIRTY" \
        -PgitCommitTime="$GIT_COMMIT_TIME" \
        -PbuildTime="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        clean bootJar

# Run stage
FROM eclipse-temurin:21-jre
ARG APP_VERSION=
ARG GIT_COMMIT=
# description overrides the Ubuntu text the JRE base image would otherwise leak through.
LABEL org.opencontainers.image.title="mockingbird-bank" \
      org.opencontainers.image.description="Mockingbird Bank - read-only account dashboard SPA" \
      org.opencontainers.image.version="$APP_VERSION" \
      org.opencontainers.image.revision="$GIT_COMMIT"
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
