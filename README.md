# Portafolio-Api

API REST que alimenta mi portafolio personal. Reúne en un solo sitio mis **proyectos** (repositorios de GitHub, con su reparto de lenguajes y las tecnologías detectadas), mis **certificados** y un **contador de visitas únicas**. Además protege los datos contra el scraping con un token de visita y un límite de peticiones.

![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

---

## Índice

- [Autor](#autor)
- [Qué hace](#qué-hace)
- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Endpoints](#endpoints)
- [Seguridad](#seguridad)
- [Requisitos](#requisitos)
- [Configuración](#configuración)
- [Cómo usarlo](#cómo-usarlo)
- [Tests y calidad](#tests-y-calidad)
- [Documentación](#documentación)
- [CI/CD](#cicd)
- [Estructura del proyecto](#estructura-del-proyecto)

---

## Autor

**Aragorn**: [github.com/Aragorn7372](https://github.com/Aragorn7372)

Diseño, desarrollo y mantenimiento del proyecto.

---

## Qué hace

| Módulo           | Descripción |
|------------------|-------------|
| **Proyectos**    | Descarga los repositorios de mi usuario y de las organizaciones que yo elija. Para cada uno calcula el % por lenguaje, el nº de commits y la URL de GitHub Pages, y detecta su stack (Gradle, npm, Docker, Compose, nginx, GitHub Actions, Bruno, OpenAPI, Blazor/Razor, MVC, PostgreSQL, Redis). Lo guarda todo en base de datos. |
| **Certificados** | Sincroniza la lista de certificados que publica un servicio externo (título, enlace y fecha). La valida y la guarda en base de datos. |
| **Visitas**      | Cuenta las visitas únicas con una huella del navegador (SHA-256 de varias señales más la IP), sin guardar datos personales en claro. Las recargas y visitas repetidas no suman. |

Los datos externos **nunca se consultan durante una petición**. Se refrescan en segundo plano (al arrancar y cada N horas) y los endpoints leen de PostgreSQL a través de una caché de dos niveles. Por eso las respuestas son rápidas y la API sigue funcionando aunque GitHub o el servicio de certificados estén caídos.

---

## Tecnologías

**Lenguaje y framework**
- **Kotlin 2.3** sobre **Java 25**
- **Spring Boot 4.1**: Web MVC, Data JPA, Security, Validation, Cache y Data Redis
- **Kotlin Coroutines**: controladores `suspend`, refrescos en paralelo con concurrencia limitada

**Persistencia y caché**
- **PostgreSQL**: proyectos, propietarios, certificados y contador de visitas (con columnas `jsonb`)
- **Redis**: caché distribuida (L2), límite de peticiones, deduplicación de visitas y pub/sub para invalidar cachés
- **Caffeine**: caché local en memoria (L1)

**Seguridad**
- **Spring Security** sin estado, con reglas de acceso definidas en configuración
- **JJWT**: tokens de visita firmados con HMAC

**Clientes HTTP**
- **RestClient** de Spring con reintentos, backoff exponencial y control de la cuota de GitHub

**Calidad y documentación**
- **JUnit 5**, **Mockito / mockito-kotlin**, **kotlinx-coroutines-test**
- **Testcontainers** (PostgreSQL real en los tests de repositorio)
- **JaCoCo**: cobertura, con CSS propio
- **Bruno**: tests de API end-to-end
- **Dokka**: documentación generada a partir del KDoc

**Infraestructura**
- **Docker**: build multi-etapa y usuario sin privilegios
- **Docker Compose**: API, PostgreSQL, Redis y un portal local opcional
- **nginx**: gateway local que agrupa la API y los informes
- **GitHub Actions** y **Dependabot**

---

## Arquitectura

```
                       ┌─────────────────────────── API ───────────────────────────┐
 Cliente ──► Proxy ──► │ OriginGateFilter ─► VisitJwtFilter ─► Controllers         │
 (web)       de        │  (secreto de       (token + límite     │                  │
             confianza │   origen)           de peticiones)     ▼                  │
                       │                                  Services ─► Caché L1/L2 ─┼─► Redis
                       │                                     │                     │
                       │                                     ▼                     │
                       │                                 Repositories ─────────────┼─► PostgreSQL
                       │                                                           │
                       │  Scheduler / arranque ─► Refresh ─► Clients ──────────────┼─► GitHub API
                       │                                                ──────────┼─► Servicio de certificados
                       └───────────────────────────────────────────────────────────┘
```

Cada módulo (`projects`, `certificates`, `visits`) sigue el mismo esquema por capas:

```
controller → service → (client | repository) → model
                ↘ mapper / validator / dto
```

**Caché híbrida.** `HybridCacheManager` pone una L1 en memoria (Caffeine, 1 min) delante de Redis (L2, con un TTL por caché). Cuando una instancia escribe o borra una clave, lo publica en el canal `cache:invalidate` para que las demás descarten su copia local. Si una entrada de Redis está corrupta o tiene otro tipo, se detecta, se borra y se vuelve a calcular.

**Refresco resistente a fallos.** Si falla un dato de un repositorio (lenguajes, Pages...), ese dato se sustituye por un valor por defecto y el refresco sigue. Si se agota la cuota de GitHub, el refresco se aborta sin guardar nada, así no queda una foto a medias. Si el servicio de certificados devuelve una lista vacía, se trata como error para no borrar los datos guardados.

---

## Endpoints

| Método | Ruta              | Token de visita | Descripción |
|--------|-------------------|:---------------:|-------------|
| `POST` | `/visits/track`   | No  | Registra la visita y devuelve la cookie `visit_jwt`. |
| `GET`  | `/visits/count`   | Sí  | Total de visitas. |
| `GET`  | `/projects`       | Sí  | Lista de proyectos. |
| `GET`  | `/certificates`   | Sí  | Lista de certificados. |
| `GET`  | `/health`         | No  | Health check: `200 {"status":"UP"}` si la API está levantada. No pide token ni secreto de origen. |

### Flujo de uso

1. El frontend llama a `POST /visits/track` con las señales del navegador:

   ```http
   POST /visits/track
   Content-Type: application/json

   {
     "userAgent": "Mozilla/5.0 ...",
     "language": "es-ES",
     "timezone": "Europe/Madrid",
     "screen": "1920x1080",
     "plugins": []
   }
   ```

   Todos los campos son opcionales (`{}` también vale). Respuesta:

   ```json
   { "counted": true, "visits": 1234 }
   ```

   con `Set-Cookie: visit_jwt=...; HttpOnly; Secure; SameSite=Lax`.

2. Con esa cookie (o con `Authorization: Bearer <token>`) ya se pueden llamar los demás endpoints:

   ```json
   // GET /projects
   [
     {
       "name": "Portfolio-api",
       "description": "API del portafolio",
       "url": "https://github.com/Aragorn7372/Portfolio-api",
       "pagesUrl": null,
       "owner": "Aragorn7372",
       "avatarUrl": "https://avatars.githubusercontent.com/...",
       "stars": 3,
       "forks": 0,
       "commits": 120,
       "languages": { "Kotlin": 92.4, "Dockerfile": 4.1, "HTML": 3.5 },
       "topics": ["kotlin", "spring-boot"],
       "technologies": ["compose", "docker", "github-actions", "gradle", "postgresql", "redis"]
     }
   ]
   ```

   ```json
   // GET /certificates
   [ { "titulo": "Curso de ...", "url": "https://...", "fecha": "2025-06-01" } ]
   ```

### Errores

Todos los errores devuelven un JSON con la forma `{"error": "<código>"}`:

| HTTP | `error`                 | Motivo |
|------|-------------------------|--------|
| 400  | `validation_failed`     | El cuerpo no pasa la validación (incluye `details`). |
| 400  | `bad_request`           | JSON mal formado. |
| 401  | `visit_token_required`  | Falta el token de visita o no es válido. |
| 403  | `forbidden_origin`      | La petición no llega por el proxy de confianza. |
| 404  | `not_found`             | La ruta no existe. |
| 405  | `method_not_allowed`    | Verbo HTTP no permitido en esa ruta. |
| 415  | `unsupported_media_type`| `Content-Type` no soportado (p. ej. `/visits/track` sin JSON). |
| 429  | `rate_limited`          | Límite de peticiones superado (cabecera `Retry-After: 60`). |
| 500  | `internal_error`        | Error inesperado (el detalle solo va al log). |

---

## Seguridad

- **Candado de origen** (`OriginGateFilter`): si se define `APP_ORIGIN_SECRET`, solo se aceptan peticiones que traigan la cabecera `X-Origin-Secret` con ese valor, que añade el proxy perimetral de confianza. La comparación es en tiempo constante. Sin la variable, el filtro queda desactivado (desarrollo y CI).
- **Token de visita**: JWT firmado con HMAC que lleva la huella del visitante y caduca a los `APP_VISITS_JWT_MINUTES` minutos. Sin él no se pueden leer proyectos, certificados ni el contador.
- **Límite de peticiones** en Redis (ventana de 1 minuto): por huella y grupo de rutas, y global por IP. Se configura por regla.
- **Reglas en configuración**: para proteger o abrir una ruta basta con añadir una regla en `application.properties`, sin tocar código:

  ```properties
  app.gate.rules[0].pattern=/projects/**
  app.gate.rules[0].auth=true
  app.gate.rules[0].fp-per-minute=120
  app.gate.rules[0].ip-per-minute=30
  ```

- **CORS** limitado a `APP_HOST_ALLOWED`, con credenciales para que viaje la cookie.
- **IP real**: `X-Forwarded-For` solo se tiene en cuenta si la conexión viene de una IP de `APP_VISITS_TRUSTED_PROXIES`.
- **Privacidad**: de cada visitante solo se guarda el hash SHA-256 de su huella, nunca la IP ni las señales en claro.

---

## Requisitos

| Para                        | Necesitas |
|-----------------------------|-----------|
| Ejecutar con Docker         | Docker y Docker Compose |
| Desarrollar en local        | JDK 25 (el wrapper `gradlew` descarga Gradle 9.1), Docker para PostgreSQL y Redis |
| Tests de integración        | Docker (Testcontainers arranca un PostgreSQL temporal) |
| Tests de API                | Node.js 22+ (Bruno CLI se ejecuta con `npx`) |
| Datos reales de proyectos   | Un token de GitHub (PAT) de solo lectura |

---

## Configuración

Toda la configuración va por variables de entorno. Copia la plantilla y rellénala:

```bash
cp .env.example .env
```

| Variable | Obligatoria | Descripción |
|----------|:-----------:|-------------|
| `API_PORT` | ✅ | Puerto HTTP de la API (p. ej. `3000`). |
| **GitHub** | | |
| `APP_GITHUB_PERSONAL` | ✅ | Usuario de GitHub cuyos repositorios forman el portafolio. |
| `APP_GITHUB_ORGANIZATIONS` | ✅ | Organizaciones extra, separadas por comas (puede ir vacía). |
| `APP_GITHUB_EXCLUDED_REPOSITORIES` | | Repositorios excluidos, en formato `propietario/nombre` y separados por comas. |
| `APP_GITHUB_TOKEN` | ✅ | Token de acceso. Sin un token válido la cuota de GitHub es muy baja. |
| `APP_GITHUB_BASE_URL` | ✅ | `https://api.github.com` |
| **Refresco** | | |
| `APP_RENIEW_PROJECTS_TIME` | ✅ | Cada cuántas horas se refrescan los proyectos. |
| `APP_RENIEW_CERTS_TIME` | ✅ | Cada cuántas horas se refrescan los certificados. |
| `APP_REFRESH_ON_STARTUP` | ✅ | `true` para refrescar al arrancar (`false` en CI). |
| **Certificados** | | |
| `APP_CERTIFICATES_BASE_URL` | ✅ | URL que devuelve el JSON de certificados. |
| `APP_REDIS_CERTS_TIME` | ✅ | TTL en horas de la caché de certificados. |
| **Base de datos** | | |
| `POSTGRES_HOST` | ✅ | Host (y puerto opcional) de PostgreSQL. En Compose es `postgres`. |
| `POSTGRES_DATABASE` | ✅ | Nombre de la base de datos. |
| `DATABASE_USER` / `DATABASE_PASSWORD` | ✅ | Credenciales. |
| **Redis** | | |
| `REDIS` / `REDIS_PORT` / `REDIS_PASSWORD` | ✅ | Host, puerto y contraseña. En Compose el host es `redis`. |
| `REDIS_SSL` | | `true` si el Redis exige TLS (URLs `rediss://`). Por defecto, `false`. |
| **Visitas y seguridad** | | |
| `APP_VISITS_JWT_SECRET` | ✅ | Secreto HMAC del token de visita, de **32 caracteres como mínimo**. |
| `APP_VISITS_JWT_MINUTES` | ✅ | Duración del token y de la ventana de deduplicación de visitas. |
| `APP_VISITS_TRUSTED_PROXIES` | | IPs de proxies de confianza, separadas por comas. Solo hace falta si la API está detrás de un proxy que no envía la cabecera de IP del proxy perimetral. |
| `APP_HOST_ALLOWED` | ✅ | Orígenes CORS permitidos (`*` solo en desarrollo). |
| `APP_ORIGIN_SECRET` | | Secreto del candado de origen. Vacío = desactivado. |

> **Formato de los certificados.** El servicio de `APP_CERTIFICATES_BASE_URL` tiene que devolver un array JSON como este:
> ```json
> [ { "titulo": "Nombre", "url": "https://.../file/d/<id>/view", "fecha": "2025-01-31" } ]
> ```
> El identificador de cada certificado se saca del segmento `/file/d/<id>` de la URL.

> ⚠️ Nunca subas el `.env` al repositorio: contiene tokens y contraseñas.

---

## Cómo usarlo

### 1. Clonar

```bash
git clone https://github.com/Aragorn7372/Portfolio-api.git
cd Portfolio-api
cp .env.example .env   # y rellénalo
```

### 2. Opción A: todo con Docker Compose

Levanta la API, PostgreSQL y Redis:

```bash
docker compose up --build -d
```

Por defecto la API **no publica puertos**: solo el gateway habla con ella. Para acceder a ella directamente, descomenta el bloque `ports` del servicio `api` en `compose.yaml`.

> La imagen compila el proyecto **y ejecuta los tests** (que usan Testcontainers) durante el build, así que el build necesita acceso al demonio de Docker. Por defecto usa `tcp://host.docker.internal:2375`, que en Docker Desktop se activa en *Settings → General → Expose daemon on tcp://localhost:2375*. Se puede cambiar con `--build-arg DOCKER_HOST_ARG=...`.

### 3. Opción B: portal local (API + informes detrás de nginx)

```bash
docker compose --profile portal up --build -d
```

Abre **http://localhost:8080**:

| Ruta       | Contenido |
|------------|-----------|
| `/`        | Portada con un botón para obtener la cookie `visit_jwt` y enlaces a todo. |
| `/api/`    | La API (sin el prefijo `/api`). |
| `/tests/`  | Informe de tests de Gradle. |
| `/jacoco/` | Informe de cobertura de JaCoCo. |
| `/docs/`   | Documentación de Dokka. |

### 4. Opción C: desarrollo en local con Gradle

1. Carga las variables del `.env` en tu entorno o en la configuración de ejecución del IDE, con `POSTGRES_HOST=localhost:5432` y `REDIS=localhost`.
2. Arranca la aplicación:

   ```bash
   ./gradlew bootRun
   ```

   El proyecto incluye `spring-boot-docker-compose` (solo en desarrollo), así que `bootRun` levanta automáticamente los servicios de `compose.yaml`.

3. Prueba la API:

   ```bash
   curl -i -c cookies.txt -X POST http://localhost:3000/visits/track -H "Content-Type: application/json" -d "{}"
   ```

   ```bash
   curl -b cookies.txt http://localhost:3000/projects
   ```

### 5. Imagen de producción

`Dokerfile-Deploy` genera una imagen mínima: compila sin tests, usa un JRE Alpine y ejecuta con un usuario sin privilegios. Solo necesita las variables de entorno de la tabla de [Configuración](#configuración):

```bash
docker build -f Dokerfile-Deploy -t portafolio-api .
```

```bash
docker run --env-file .env -p 3000:3000 portafolio-api
```

En producción conviene definir `APP_ORIGIN_SECRET`, restringir `APP_HOST_ALLOWED` a tu dominio y poner la API detrás de un proxy de confianza que inyecte la cabecera de origen.

---

## Tests y calidad

```bash
./gradlew test               # tests unitarios y de integración (necesita Docker)
./gradlew jacocoTestReport   # informe de cobertura
```

- **Unitarios**: servicios, mappers, validadores, filtros, detector de stack, caché híbrida, scheduler... con Mockito y `runTest`.
- **Integración**: repositorios JPA contra un PostgreSQL real con Testcontainers.
- **Informes**: `build/reports/tests/test/index.html` y `build/reports/jacoco/test/html/index.html`.

### Tests de API con Bruno

La colección está en `Api-portfolio-test/` y cubre el flujo completo: primera visita, recarga (F5), token manipulado, peticiones sin token, cuerpo inválido, etc.

```bash
cd Api-portfolio-test
npx -y @usebruno/cli run . -r --env Local      # contra http://localhost:3000
npx -y @usebruno/cli run . -r --env Gateway    # contra http://localhost:8080/api
```

---

## Documentación

Todo el código tiene KDoc: cada clase, cada endpoint y cada decisión de diseño. La documentación HTML se genera con:

```bash
./gradlew dokkaGenerate
```

Queda en `build/dokka/html/index.html`. También se sirve en `/docs/` del portal local y se publica en GitHub Pages desde la rama `main`.

---

## CI/CD

El workflow `.github/workflows/docs.yml` se ejecuta en cada push a `main`:

1. **build**: compila sin tests.
2. **unit-test**: tests y cobertura JaCoCo.
3. **dokka**: genera la documentación.
4. **bruno-tests**: arranca PostgreSQL, Redis, la API y el gateway nginx, y ejecuta la colección de Bruno.
5. **docs**: junta la documentación y los informes (tests, cobertura, Bruno) en un portal y lo publica en la rama `gh-pages`.

**Dependabot** revisa cada semana las dependencias de Gradle y agrupa las de JJWT.

---

## Estructura del proyecto

```
Portafolio-Api/
├── src/main/kotlin/dev/aragorn/portafolioapi/
│   ├── common/          # Configuración transversal
│   │   ├── config/      #   caché híbrida, Redis, seguridad, CORS, clientes HTTP, errores
│   │   ├── controller/  #   health check (/health)
│   │   ├── schedulers/  #   refresco periódico
│   │   ├── service/     #   orquestación del refresco y contrato de validación
│   │   └── startup/     #   refresco al arrancar
│   ├── projects/        # Proyectos (GitHub): client, detector, dto, mapper, model, service...
│   ├── certificates/    # Certificados: client, dto, mapper, model, service...
│   └── visits/          # Visitas: gate (filtros), ratelimit, service, controller...
├── src/test/            # Tests unitarios y de integración
├── Api-portfolio-test/  # Colección de Bruno (tests de API)
├── gateway/             # nginx del portal local y de CI + portada HTML
├── custom/report/       # CSS propio para JaCoCo
├── Dockerfile           # Build completo + imágenes de informes (portal)
├── Dokerfile-Deploy     # Imagen mínima de producción
├── compose.yaml         # API + PostgreSQL + Redis (+ perfil "portal")
└── .env.example         # Plantilla de configuración
```

---

<p align="center">Hecho por <a href="https://github.com/Aragorn7372">Aragorn</a></p>
