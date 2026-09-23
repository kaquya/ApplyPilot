FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q verify

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache su-exec
RUN addgroup -S applypilot && adduser -S applypilot -G applypilot
WORKDIR /app
RUN mkdir /app/uploads && chown -R applypilot:applypilot /app
COPY --from=build --chown=applypilot:applypilot /app/target/applypilot-api-0.1.0.jar /app/app.jar
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
