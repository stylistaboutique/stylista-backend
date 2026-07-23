# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download deps first (layer cached unless pom.xml changes)
RUN mvn -q dependency:go-offline -B
COPY src ./src
RUN mvn -q clean package -DskipTests -B

# ===== Run stage =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/stylista-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
