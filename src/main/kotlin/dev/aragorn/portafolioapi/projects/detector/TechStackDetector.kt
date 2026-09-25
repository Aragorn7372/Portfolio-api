package dev.aragorn.portafolioapi.projects.detector

import org.springframework.stereotype.Component


/**
 * Detecta las tecnologías de un repositorio a partir de sus rutas de fichero y, si hace falta,
 * del contenido de unos pocos ficheros de configuración.
 *
 * Es heurístico: no ejecuta ni compila nada, solo busca ficheros y directorios característicos.
 * La detección se hace en dos fases para ahorrar peticiones a GitHub:
 * 1. [selectContentFiles] elige, a partir del árbol, qué ficheros merece la pena descargar.
 * 2. [detect] combina las rutas del árbol con el contenido de esos ficheros.
 */
@Component
class TechStackDetector {

    companion object {
        /**
         * Ficheros cuyo contenido se inspecciona (compose, configuración de Spring, `.env`,
         * `Dockerfile`), en orden de prioridad. Son los sitios donde suelen aparecer las
         * dependencias de infraestructura, como la base de datos o la caché.
         */
        val CONTENT_CANDIDATES = listOf(
            "compose.yaml",
            "compose.yml",
            "docker-compose.yml",
            "docker-compose.yaml",
            "application.properties",
            "application.yml",
            "application.yaml",
            ".env",
            "Dockerfile",
        )
        /** Máximo de ficheros que se descargan por repositorio. */
        private const val MAX_CONTENT_FILES = 3
    }

    /**
     * Devuelve las tecnologías detectadas en un repositorio.
     *
     * Por rutas (sin distinguir mayúsculas) se detectan: `gradle`, `npm`, `docker`, `compose`,
     * `nginx`, `github-actions`, `bruno`, `openapi`, `blazor`/`razor` (ficheros `.razor`) y `mvc`
     * (controladores C# junto a vistas `.cshtml`). Por contenido se detectan `postgresql` y
     * `redis` si esas palabras aparecen en alguno de los ficheros descargados.
     *
     * @param paths rutas del árbol del repositorio.
     * @param fileContents contenido de los ficheros elegidos por [selectContentFiles], indexado por ruta.
     * @return identificadores de tecnología sin duplicados y ordenados alfabéticamente.
     */
    fun detect(paths: List<String>, fileContents: Map<String, String> = emptyMap()): List<String> {
        val lower = paths.map { it.lowercase() }
        val byName = lower.toSet()
        val result = mutableSetOf<String>()

        if (byName.contains("gradlew") || byName.contains("gradlew.bat") ||
            lower.any { it.startsWith("gradle/wrapper/") || it == "build.gradle" || it == "build.gradle.kts" || it == "settings.gradle" || it == "settings.gradle.kts" }
        ) result.add("gradle")

        if (byName.contains("package.json") || byName.contains("package-lock.json")) result.add("npm")

        if (lower.any { it == "dockerfile" || it.startsWith("dockerfile.") || it.startsWith("docker/") || it.endsWith("/dockerfile") }) result.add("docker")
        if (lower.any { it == "compose.yaml" || it == "compose.yml" || it == "docker-compose.yml" || it == "docker-compose.yaml" }) result.add("compose")

        if (lower.any { it == "nginx.conf" || it.startsWith("nginx/") || it == "default.conf" || it.startsWith("sites-enabled/") }) result.add("nginx")

        if (lower.any { it.startsWith(".github/workflows/") && (it.endsWith(".yml") || it.endsWith(".yaml")) }) result.add("github-actions")

        if (lower.any { it.endsWith(".bru") || it.startsWith("bruno/") || it.startsWith(".bruno/") }) result.add("bruno")

        if (lower.any {
                val file = it.substringAfterLast("/")
                (file.startsWith("openapi") || file.startsWith("swagger")) && (it.endsWith(".yml") || it.endsWith(".yaml")) ||
                    it.startsWith("api-docs/") || it.contains("openapi")
            }
        ) result.add("openapi")

        val hasCsproj = lower.any { it.endsWith(".csproj") || it.endsWith(".sln") }
        val hasRazor = lower.any { it.endsWith(".razor") }
        val hasMvc = lower.any { it.startsWith("controllers/") || it.endsWith("controller.cs") } &&
            lower.any { it.endsWith(".cshtml") || it.startsWith("views/") }
        if (hasRazor) {
            result.add("blazor")
            result.add("razor")
        }
        if (hasMvc) result.add("mvc")

        val combined = fileContents.values.joinToString("\n").lowercase()
        if (combined.contains("postgres")) result.add("postgresql")
        if (combined.contains("redis")) result.add("redis")

        return result.sorted()
    }

    /**
     * Elige qué ficheros del árbol merece la pena descargar para inspeccionar contenido.
     *
     * Solo tiene en cuenta ficheros en la raíz que coincidan con [CONTENT_CANDIDATES] (sin
     * distinguir mayúsculas), en el orden de esa lista y con un máximo de [MAX_CONTENT_FILES].
     *
     * @param treePaths rutas del árbol del repositorio.
     * @return rutas a descargar, con las mayúsculas originales del árbol.
     */
    fun selectContentFiles(treePaths: List<String>): List<String> {
        val lowerMap = treePaths.associateBy({ it.lowercase() }, { it })
        return CONTENT_CANDIDATES
            .mapNotNull { lowerMap[it.lowercase()] }
            .distinct()
            .take(MAX_CONTENT_FILES)
    }
}
