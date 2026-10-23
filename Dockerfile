# syntax=docker/dockerfile:1
# Build multi-stage: compila com Gradle e executa em UBI OpenJDK 25 (Quarkus fast-jar).
# Uso: docker compose build && docker compose up -d

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
# Node só no estágio de BUILD: o gabarito das contas da Academia (src/test/js, `node --test`) roda
# dentro da suíte. Sem ele o teste se ignora (Assumptions) e o portão da imagem não veria conta errada.
RUN apt-get update && apt-get install -y --no-install-recommends nodejs && rm -rf /var/lib/apt/lists/*

COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# Cache do Gradle entre builds (auditoria OPS-18): sem ele, toda mudança em src baixava de novo a
# distribuição do Gradle e todas as dependências. O cache de montagem do BuildKit não entra na imagem.
# Esta etapa, só com os arquivos de build, resolve o que depende deles antes de copiar o código.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# O teste das páginas de erro do proxy (502/503/504) gera e confere os arquivos em scripts/erro-proxy.
COPY scripts/erro-proxy scripts/erro-proxy
# A suíte (guardas de arquitetura, robots/sitemap, i18n/ícones, SRI, testes de comportamento) BARRA a
# imagem: sem esta etapa o deploy fazia `build -x test` e commit com teste vermelho ia para produção
# (auditoria F21). Etapa separada e antes do build de produção: os testes rodam no perfil de teste,
# e o artefato de produção só é montado se passarem. Testes que dependem de internet se ignoram
# sozinhos quando não há rede (Assumptions), em vez de reprovar por causa ambiental.
RUN --mount=type=cache,target=/root/.gradle ./gradlew test --no-daemon
RUN --mount=type=cache,target=/root/.gradle ./gradlew build -x test --no-daemon -Dquarkus.package.jar.type=fast-jar -Dquarkus.profile=prod

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

# Atenção ao JAVA_OPTS_APPEND abaixo: NÃO acrescentar um coletor de lixo aqui.
# O run-java.sh da imagem base já passa -XX:+UseParallelGC; uma segunda flag de GC faz a
# JVM abortar no boot com "Multiple garbage collectors selected" (container em crash-loop).
# Para trocar o coletor é preciso desligar o do entrypoint, não empilhar outra flag.
ENV LANGUAGE='pt_BR:pt' \
    HOME=/deployments/data \
    QUARKUS_PROFILE=prod \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Duser.home=/deployments/data -XX:MaxRAMPercentage=65 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"

USER root
RUN mkdir -p /deployments/data/logs /deployments/data/geo /deployments/data/.framework-net \
    && chown -R 185:0 /deployments/data \
    && chmod -R g+rwX /deployments/data

WORKDIR /deployments

COPY --from=build --chown=185:0 /build/build/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185:0 /build/build/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185:0 /build/build/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185:0 /build/build/quarkus-app/quarkus/ /deployments/quarkus/

VOLUME ["/deployments/data"]

EXPOSE 8080

USER 185

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/health > /dev/null || exit 1

ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
