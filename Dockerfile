# Stage 1: Build source code
# Sử dụng maven với eclipse-temurin-17 (bản ổn định thay thế cho openjdk cũ)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build package, bỏ qua test để deploy nhanh hơn
RUN mvn clean package -DskipTests

# Stage 2: Run application
# Thay thế 'openjdk:17-jdk-slim' bằng 'eclipse-temurin:17-jre-alpine'
# Alpine Linux siêu nhẹ (~170MB) giúp deploy nhanh hơn và ít lỗi "not found"
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file jar từ bước build
COPY --from=build /app/target/vsv-shop.jar vsv-shop.jar

# Thiết lập biến môi trường
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE ${PORT}

ENTRYPOINT ["java", "-jar", "vsv-shop.jar"]