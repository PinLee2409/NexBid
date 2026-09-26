# syntax=docker/dockerfile:1

# EN: Build stage. The Maven cache is a BuildKit cache mount, so a rebuild after a code change does not
#     download the dependencies again. Tests are not run here: they start their own containers and need
#     Docker, which a build does not have. Run them with ./mvnw test.
# VI: Giai đoạn build. Cache Maven là cache mount của BuildKit, nên build lại sau khi sửa code không phải
#     tải lại thư viện. Không chạy test ở đây: test tự khởi động container và cần Docker, thứ mà bước build
#     không có. Chạy test bằng ./mvnw test.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/nexbid-*.jar extract --layers --launcher --destination extracted

# EN: Runtime stage: a JRE only, the app unpacked into layers from least to most often changed, so a code
#     change rebuilds a 1 MB layer instead of the 100 MB of dependencies.
# VI: Giai đoạn chạy: chỉ có JRE, app được tách thành các lớp từ ít đổi tới hay đổi nhất, nên sửa code chỉ
#     build lại lớp 1 MB thay vì 100 MB thư viện.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 --home-dir /app nexbid \
    && mkdir -p /app/var/images \
    && chown -R nexbid /app/var

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER nexbid
EXPOSE 8080

# EN: Let the JVM use most of the container's memory limit rather than the default quarter.
# VI: Cho JVM dùng phần lớn giới hạn bộ nhớ của container thay vì mặc định một phần tư.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
