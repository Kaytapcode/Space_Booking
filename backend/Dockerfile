FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependencies first so this layer is cached until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package && mv target/spacebooking-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home spring
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER spring
EXPOSE 8080
# Render injects PORT; fall back to 8080 locally.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
