# Stage 1: Build the application jar inside Docker.
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy pom.xml first and download dependencies to leverage layer caching.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package.
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime image with JRE and fonts.
FROM eclipse-temurin:17-jre

# Install CJK fonts + fontconfig.
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei \
    fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

# Copy standalone CJK fonts for ofdrw's two font systems:
# 1. wqy-microhei.ttf (TrueType, has glyf table) - for PdfboxMaker (PDF export).
#    PDFBox requires TrueType glyf table; CFF/OTF fonts cause UnsupportedOperationException.
# 2. NotoSansCJKsc-Regular.otf (CFF/OTF) - for AWTMaker (image rendering/preview).
#    Image rendering uses Java AWT which handles OTF fine.
COPY wqy-microhei.ttf /usr/share/fonts/truetype/wqy/wqy-microhei.ttf
COPY NotoSansCJKsc-Regular.otf /usr/share/fonts/truetype/noto-cjk/NotoSansCJKsc-Regular.otf
RUN fc-cache -f

# Font aliases: map Chinese font names to installed fonts.
COPY fonts.conf /etc/fonts/conf.d/99-cjk-aliases.conf
RUN fc-cache -f

WORKDIR /app
COPY --from=builder /build/target/ofd-converter-backend-0.1.0.jar app.jar
ENV JAVA_OPTS="-Xmx768m"
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
