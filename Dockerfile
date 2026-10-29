# syntax=docker/dockerfile:1
# Uso: docker build --build-arg MODULE=sfe-api -t sfe-api .   |   --build-arg MODULE=sfe-worker -t sfe-worker .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY sfe-core/pom.xml sfe-core/
COPY sfe-persistence/pom.xml sfe-persistence/
COPY sfe-api/pom.xml sfe-api/
COPY sfe-worker/pom.xml sfe-worker/
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
ARG MODULE
RUN useradd --system --uid 10001 --no-create-home sfe
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar app.jar
USER 10001
# ignoreLineBreaks: Base64 de la firma en una linea (sin &#13;). TLS 1.2+ negociando la version mas alta (RNF-02).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Dcom.sun.org.apache.xml.internal.security.ignoreLineBreaks=true -Djdk.tls.client.protocols=TLSv1.3,TLSv1.2"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
