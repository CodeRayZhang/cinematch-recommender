FROM node:22-alpine AS web
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY --from=web /workspace/frontend/dist frontend/dist
RUN mvn -B -f backend/pom.xml verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system cinematch && useradd --system --gid cinematch cinematch && mkdir /app/.data && chown -R cinematch:cinematch /app
COPY --from=api /workspace/backend/target/cinematch-2.0.0-SNAPSHOT.jar /app/app.jar
USER cinematch
ENV BIND_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
