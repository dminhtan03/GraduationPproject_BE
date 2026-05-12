FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/BookingMeetingRoom-0.0.1-SNAPSHOT.jar app.jar
COPY src/main/resources/application.yml application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]