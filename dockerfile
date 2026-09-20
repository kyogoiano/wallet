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
    GRADLE_URL="https://services.gradle.org/distributions/gradle-9.8.0-rc-2-bin.zip"; \
    curl -fL -o gradle.zip "$GRADLE_URL"; \
    unzip -d /opt gradle.zip; \
    mv /opt/gradle-9.8.0-rc-2 "$GRADLE_HOME"; \
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

# Executa o build de ambos os módulos de runtime (core e edge)
RUN ./gradlew clean :bootJar :edge:bootJar --no-daemon

# =========================================================
# Edge Runtime Stage (wallet-edge)
# =========================================================
FROM oraclelinux:9-slim AS edge

ENV JAVA_HOME=/usr/java/valhalla-jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install curl for container healthcheck and create non-root wallet user
RUN set -eux; \
    microdnf install -y curl; \
    microdnf clean all; \
    useradd -u 10001 -m -s /bin/sh wallet; \
    mkdir -p /spool; \
    chown -R 10001:10001 /spool; \
    chmod 700 /spool

# Re-copy only the Valhalla JDK from stage 1
COPY --from=builder /usr/java/valhalla-jdk /usr/java/valhalla-jdk

WORKDIR /app

COPY --from=builder --chown=10001:10001 /app/edge/build/libs/wallet-edge.jar app.jar

USER 10001:10001

VOLUME ["/spool"]
EXPOSE 8080 8443/udp

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# =========================================================
# Core Runtime Stage (wallet-core / default)
# =========================================================
FROM oraclelinux:9-slim AS core

ENV JAVA_HOME=/usr/java/valhalla-jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install native C++ runtime, OpenMP for ONNX Runtime (I-FUSION-004), curl for healthcheck, and create non-root wallet user
RUN set -eux; \
    microdnf install -y libstdc++ libgomp curl; \
    microdnf clean all; \
    useradd -u 10001 -m -s /bin/sh wallet

# Re-copy only the Valhalla JDK from stage 1
COPY --from=builder /usr/java/valhalla-jdk /usr/java/valhalla-jdk

WORKDIR /app

COPY --from=builder --chown=10001:10001 /app/build/libs/wallet-core.jar app.jar

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java", "-Duser.timezone=UTC", "--enable-native-access=ALL-UNNAMED", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]