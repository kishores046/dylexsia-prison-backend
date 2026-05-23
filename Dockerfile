FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/dylexsia-prison-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
LABEL authors="Kishore S"
ENTRYPOINT ["java", "-jar","app.jar"]