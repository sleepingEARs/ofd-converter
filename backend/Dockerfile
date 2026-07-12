FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre

# Install CJK fonts for OFD rendering (Chinese characters show as boxes without them).
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-noto-cjk \
    fonts-wqy-zenhei \
    fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Bound JVM heap: 4 parallel conversions share this headroom; per-task memory
# is bounded by this plus try/catch on OutOfMemoryError in ConvertService.
ENV JAVA_OPTS="-Xmx768m"
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
