# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon

COPY src src

RUN set -eux; \
    ./gradlew bootJar --no-daemon; \
    jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"; \
    test -n "$jar_path"; \
    cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends liblcms2-2 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 placesplates \
    && useradd --system --uid 10001 --gid placesplates --home-dir /app --shell /usr/sbin/nologin placesplates

WORKDIR /app

COPY --from=builder --chown=placesplates:placesplates /workspace/app.jar /app/app.jar

USER placesplates

ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
