# Build stage
FROM gradle:9.7.1-jdk26 AS build

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
FROM bellsoft/liberica-runtime-container:jdk-26-glibc
WORKDIR /app

# Install native C++ runtime and OpenMP dependencies required by embedded ONNX Runtime (I-FUSION-004)
RUN apk update && apk add --no-cache libstdc++ libgomp

# Copia o JAR gerado (usando um wildcard mais seguro)
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]