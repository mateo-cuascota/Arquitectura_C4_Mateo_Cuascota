# Imagen para cualquiera de los dos microservicios.
# docker build --build-arg SERVICIO=sri-service -t sri-service backend
# docker build --build-arg SERVICIO=ant-service -t ant-service backend
FROM eclipse-temurin:21-jdk AS build
ARG SERVICIO
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests -pl ${SERVICIO} -am package

FROM eclipse-temurin:21-jre
ARG SERVICIO
WORKDIR /app
COPY --from=build /src/${SERVICIO}/target/${SERVICIO}-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
