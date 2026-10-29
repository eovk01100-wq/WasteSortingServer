# syntax=docker/dockerfile:1
# 1-6단계: 서버를 Docker 이미지로 만든다 (멀티스테이지)
# 1) build: JDK로 jar를 만든다  2) run: 작은 JRE 이미지에 jar만 담는다

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성 정의를 먼저 복사해서, 코드만 바뀌면 의존성 다운로드를 다시 하지 않게 한다
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# root가 아닌 일반 사용자로 실행 (보안)
RUN useradd --system --uid 1001 spring \
    && mkdir -p /app/uploads && chown spring /app/uploads
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8080
# 컨테이너 메모리의 75%까지만 자바가 쓰도록 (프리티어 서버 대비)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
