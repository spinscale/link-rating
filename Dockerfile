FROM gradle:6.7-jdk14 as build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon


FROM openjdk:15.0.1-jdk-slim
RUN addgroup --system spring && adduser --system spring --ingroup spring
VOLUME /tmp
USER spring:spring
WORKDIR /app
ARG JAR_FILE=build/libs/*.jar
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
EXPOSE 8080
