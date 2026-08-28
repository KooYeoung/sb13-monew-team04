# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 스테이지 ----------
# Gradle 빌드에만 필요한 JDK 풀버전 이미지를 사용한다.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app/monew

# 의존성 목록(gradlew, gradle 설정 파일)만 먼저 복사한다.
# 소스 코드(src)가 바뀌어도 build.gradle이 그대로면 이 레이어는 캐시되어 재사용된다.
# → 매번 전체 의존성을 새로 받지 않아 CI 빌드 시간이 크게 줄어든다.
COPY gradlew gradle build.gradle settings.gradle ./
RUN chmod +x gradlew

# 소스 코드는 의존성 레이어 다음에 복사한다.
# 실제 코드가 자주 바뀌므로, 이 레이어 아래(의존성)는 캐시를 최대한 재사용하기 위함이다.
COPY src src

# 테스트는 이미 develop→main PR 단계(GitHub Actions)에서 검증되었으므로
# 배포용 이미지 빌드에서는 -x test로 다시 돌리지 않는다. (빌드 시간 단축)
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 2단계: 실행 스테이지 ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# 컨테이너 안에서도 root로 프로세스를 띄우지 않기 위해 전용 사용자를 만든다.
# 컨테이너가 뚫려도 root 권한 탈취로 바로 이어지지 않도록 하는 최소한의 방어다.
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# 빌드 스테이지에서 만들어진 jar만 복사한다. (소스코드, gradle 캐시 등은 최종 이미지에 포함되지 않음)
COPY --from=build /app/monew/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# -XX:MaxRAMPercentage: 고정된 -Xmx 대신 컨테이너에 할당된 메모리의 비율로 힙을 잡는다.
# EC2 인스턴스 사양을 나중에 바꿔도(t3.micro→t3.small 등) Dockerfile을 수정할 필요가 없다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
