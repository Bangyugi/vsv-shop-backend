# Stage 1: Build source code
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build package, bỏ qua test để deploy nhanh hơn
RUN mvn clean package -DskipTests

# Stage 2: Run application
# Sử dụng bản Debian-based (không phải Alpine) để ổn định Network & DNS resolution
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy file jar từ bước build
COPY --from=build /app/target/vsv-shop.jar vsv-shop.jar

# Thiết lập biến môi trường
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE ${PORT}

# QUAN TRỌNG: Thêm -Djava.net.preferIPv4Stack=true để tránh lỗi timeout khi kết nối SMTP Gmail
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "vsv-shop.jar"]