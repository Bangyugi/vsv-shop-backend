# Stage 1: Build source code
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build package, bỏ qua test để deploy nhanh hơn
RUN mvn clean package -DskipTests

# Stage 2: Run application
FROM openjdk:17-jdk-slim
WORKDIR /app
# Copy file jar từ bước build (lưu ý tên file khớp với finalName trong pom.xml)
COPY --from=build /app/target/vsv-shop.jar vsv-shop.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "vsv-shop.jar"]