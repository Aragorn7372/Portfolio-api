FROM gradle:jdk25-alpine AS build
LABEL authors="Aragorn"
# Directorio de trabajo
WORKDIR /app

# Copia los archivos build.gradle y src de nuestro proyecto
COPY build.gradle.kts .
COPY gradlew .
COPY gradle gradle
COPY custom/report/report.css custom/report/report.css
COPY src src
ARG DOCKER_HOST_ARG=tcp://host.docker.internal:2375
ENV DOCKER_HOST=$DOCKER_HOST_ARG
RUN ./gradlew build dokkaGenerate jacocoTestReport
#-----------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS run
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# Mitigacion CVE-2026-91403 (intermedias eMudhra comprometidas), workaround oficial de openjdk.org/emudhra:
# quitar del cacerts las raices emSign ECC Root CA - G3 y AAA Certificate Services. El build falla si siguen.
# Eliminar cuando Temurin publique una version corregida (>= 25.0.5.0.2).
RUN K="keytool -J-Duser.language=en -cacerts -storepass changeit"; \
    P='emSign ECC Root CA - G3|CN=AAA Certificate Services'; \
    $K -list -v | awk -F': ' -v p="$P" '/^Alias name:/{a=$2} /^Owner:/ && $0 ~ p {print a}' \
      | while IFS= read -r a; do $K -delete -noprompt -alias "$a"; done; \
    ! $K -list -v | grep -qE "$P"
# Directorio de trabajo
WORKDIR /app

# Copia el jar de la aplicación, ojo que esta en la etapa de compilación, etiquetado como build
# Cuidado con la ruta definida cuando has copiado las cosas en la etapa de compilación
# Para copiar un archivo de una etapa a otra, se usa la instrucción COPY --from=etapaOrigen
COPY --from=build --chown=appuser:appgroup /app/build/libs/*SNAPSHOT.jar /app/my-app.jar
USER appuser
# Ejecuta el jar
ENTRYPOINT ["java","-jar","/app/my-app.jar"]
#-----------------------------------------------------------------------------------------------------------------------
# nginx etapa webTest
FROM nginx:latest AS testweb

# establezco el directoio de trabajo
WORKDIR /app
# elimino la web por defecto
RUN rm -rf /usr/share/nginx/html/*

# Copiamos informe de test, si es que se genero
COPY --from=build /app/build/reports/tests/test /usr/share/nginx/html
#-----------------------------------------------------------------------------------------------------------------------
# nginx etapa jacoco web
FROM nginx:latest AS jacocoweb
# establezco el directoio de trabajo
WORKDIR /app
# elimino la web por defecto
RUN rm -rf /usr/share/nginx/html/*

# Copiamos informe de test, si es que se genero
COPY --from=build /app/build/reports/jacoco/test/html /usr/share/nginx/html
#----------------------------------------------------------------------------------------------------------------------
# apache2 etapa documentacion web
FROM httpd:latest AS docweb
# establezco el directoio de trabajo
WORKDIR /app
# elimino la web por defecto
RUN rm -rf /usr/local/apache2/htdocs/*

# Copiamos informe de test, si es que se genero
COPY --from=build /app/build/dokka/html /usr/local/apache2/htdocs/
