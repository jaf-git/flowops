FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml checkstyle.xml ./
RUN ./mvnw -B --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw -B --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S flowops && adduser -S flowops -G flowops
USER flowops

COPY --from=build /build/target/flowops-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
