FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app
RUN mkdir /tmpwork
COPY . .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
