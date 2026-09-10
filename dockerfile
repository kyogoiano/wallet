# Build stage
FROM oraclelinux:9-slim AS builder

ENV JAVA_HOME=/usr/java/valhalla-jdk
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${GRADLE_HOME}/bin:${JAVA_HOME}/bin:${PATH}"

# Install necessary base utilities
RUN set -eux; \
    microdnf install -y gzip tar binutils unzip findutils wget; \
    microdnf clean all

# 1. Download and extract Project Valhalla JDK 27 Early Access
RUN set -eux; \
    # Instala utilitários necessários para baixar e descompactar
    microdnf install -y gzip tar wget; \
    microdnf clean all; \
    \
    # Cria a pasta destino do Java
    mkdir -p "$JAVA_HOME"; \
    \
    # URL oficial e direta do binário Early-Access mais recente com suporte ao Valhalla (JDK 28)
    DOWNLOAD_URL="https://download.java.net/java/early_access/valhalla/27/1/openjdk-27-jep401ea3+1-1_linux-x64_bin.tar.gz"; \
    \
    # Baixa o tarball direto e descompacta via pipeline para economizar espaço em disco
    curl -fL "$DOWNLOAD_URL" | tar --extract --gzip --directory "$JAVA_HOME" --strip-components=1 --no-same-owner; \
    \
    # Valida se o binário foi instalado e está funcionando
    java --version


# 2. Download and extract Gradle 9.8
RUN set -eux; \
    GRADLE_URL="https://services.gradle.org/distributions/gradle-9.8.0-rc-1-bin.zip"; \
    curl -fL -o gradle.zip "$GRADLE_URL"; \
    unzip -d /opt gradle.zip; \
    mv /opt/gradle-9.8.0-rc-1 "$GRADLE_HOME"; \
    rm gradle.zip

# Verify installations
RUN java --version && gradle --version




WORKDIR /app

# Copia arquivos de configuração primeiro para aproveitar o cache de camadas
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY core core/
COPY fraud fraud/
COPY edge edge/

RUN chmod +x gradlew

# Baixa dependências
RUN ./gradlew dependencies --no-daemon || true

# Copia o código fonte do projeto principal
COPY src src

# Executa o build do projeto raiz explicitamente
# O clean garante que não haja lixo de builds anteriores
RUN ./gradlew clean :bootJar --no-daemon

# Runtime stage
FROM oraclelinux:9-slim

ENV JAVA_HOME=/usr/java/valhalla-jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install native C++ runtime and OpenMP dependencies required by embedded ONNX Runtime (I-FUSION-004)
RUN set -eux; \
    microdnf install -y libstdc++ libgomp; \
    microdnf clean all

# Re-copy only the Valhalla JDK from stage 1 to keep things consistent
COPY --from=builder /usr/java/valhalla-jdk /usr/java/valhalla-jdk

WORKDIR /app

# Copia o JAR gerado (usando um wildcard mais seguro)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djavax.net.debug=ssl:handshake", "-jar", "app.jar"]