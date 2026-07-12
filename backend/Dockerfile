FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre

# Install CJK fonts for OFD rendering. ofdrw's FontLoader needs .ttf (not .ttc).
# fonts-wqy-microhei ships as .ttf and covers GB2312/GBK Chinese chars.
# Also install fonttools to extract .ttf from Noto .ttc collection.
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

# Extract Noto Sans CJK SC as standalone .ttf from the .ttc collection
# (FontLoader may not handle .ttc properly; .ttf is universally supported).
RUN apt-get update && apt-get install -y --no-install-recommends python3-fonttools \
    && mkdir -p /usr/share/fonts/truetype/noto-cjk \
    && ttx --output /usr/share/fonts/truetype/noto-cjk/NotoSansCJKsc-Regular.otf \
       /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc 2>/dev/null \
    || cp /usr/share/fonts/truetype/wqy/wqy-microhei.ttc \
       /usr/share/fonts/truetype/noto-cjk/wqy-microhei.ttc 2>/dev/null \
    || true \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*

# Font aliases: map Chinese font names to installed fonts.
COPY fonts.conf /etc/fonts/conf.d/99-cjk-aliases.conf
RUN fc-cache -f

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Bound JVM heap: 4 parallel conversions share this headroom; per-task memory
# is bounded by this plus try/catch on OutOfMemoryError in ConvertService.
ENV JAVA_OPTS="-Xmx768m"
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
