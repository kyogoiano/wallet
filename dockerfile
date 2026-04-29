# Build stage
FROM gradle:9.5.0-jdk25 AS build

WORKDIR /app

# Copia arquivos de configuração primeiro para aproveitar o cache de camadas
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY core core/
COPY fraud fraud/

RUN chmod +x gradlew

# Baixa dependências
RUN ./gradlew dependencies --no-daemon || true

# Copia o código fonte do projeto principal
COPY src src

# Executa o build do projeto raiz explicitamente
# O clean garante que não haja lixo de builds anteriores
RUN ./gradlew clean :bootJar --no-daemon

# Runtime stage
FROM bellsoft/liberica-runtime-container:jdk-25-slim-glibc
WORKDIR /app

# Copia o JAR gerado (usando um wildcard mais seguro)
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]