FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests



FROM eclipse-temurin:21-jre

WORKDIR /app

COPY certs/russian_trusted_root_ca.crt /tmp/russian_trusted_root_ca.crt
RUN keytool -importcert -cacerts -storepass changeit -noprompt -alias russian-trusted-root-ca -file /tmp/russian_trusted_root_ca.crt && \
    rm /tmp/russian_trusted_root_ca.crt

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8095

ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-XX:MaxMetaspaceSize=192m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
