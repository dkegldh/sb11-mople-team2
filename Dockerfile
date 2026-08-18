# --- 1단계 : 빌드 ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# 의존성 캐시를 위해 gradle 관련 파일 먼저 복사
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드 (테스트는 CI에서 검증되므로 스킵)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- 2단계 : 실행 ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# non-root 사용자로 실행
RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/build/libs/mople-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]