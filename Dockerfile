FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 1. pom.xml сначала
COPY pom.xml .

# 2. Зависимости (cache!)
RUN mvn dependency:go-offline -B

# 3. ВСЁ остальное
COPY src ./src
COPY .mvn .mvn

# 4. УКАЗЫВАЕМ main class явно!
RUN mvn clean package -DskipTests \
    -Dspring-boot.repackage.skip=false \
    -Dmaven.main.skip=false

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]