FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre

# Install CJK fonts + fontconfig.
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

# Copy standalone Noto Sans CJK SC (.otf, extracted from .ttc) - ofdrw's FontLoader
# needs standalone font files, not .ttc collections.
COPY NotoSansCJKsc-Regular.otf /usr/share/fonts/truetype/noto-cjk/NotoSansCJKsc-Regular.otf
RUN fc-cache -f

# Font aliases: map Chinese font names to installed fonts.
COPY fonts.conf /etc/fonts/conf.d/99-cjk-aliases.conf
RUN fc-cache -f

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-Xmx768m"
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
