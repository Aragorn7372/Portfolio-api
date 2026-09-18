package dev.aragorn.portafolioapi.projects.detector

import org.springframework.stereotype.Component

/**
 * Detecta tecnologías por nombres de fichero del árbol (default branch)
 * + contenido de ficheros clave (compose, properties, Dockerfile).
 * Todo case-insensitive. Devuelve slugs ordenados y sin duplicados.
 */
@Component
class TechStackDetector {

    companion object {
        /** Ficheros cuyo contenido se inspecciona para postgres/redis (max 3 por repo). */
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
        private const val MAX_CONTENT_FILES = 3
    }

    fun detect(paths: List<String>, fileContents: Map<String, String> = emptyMap()): List<String> {
        val lower = paths.map { it.lowercase() }
        val byName = lower.toSet()
        val result = mutableSetOf<String>()

        // Gradle wrapper
        if (byName.contains("gradlew") || byName.contains("gradlew.bat") ||
            lower.any { it.startsWith("gradle/wrapper/") || it == "build.gradle" || it == "build.gradle.kts" || it == "settings.gradle" || it == "settings.gradle.kts" }
        ) result.add("gradle")

        // npm
        if (byName.contains("package.json") || byName.contains("package-lock.json")) result.add("npm")

        // Docker / Compose
        if (lower.any { it == "dockerfile" || it.startsWith("dockerfile.") || it.startsWith("docker/") || it.endsWith("/dockerfile") }) result.add("docker")
        if (lower.any { it == "compose.yaml" || it == "compose.yml" || it == "docker-compose.yml" || it == "docker-compose.yaml" }) result.add("compose")

        // Nginx
        if (lower.any { it == "nginx.conf" || it.startsWith("nginx/") || it == "default.conf" || it.startsWith("sites-enabled/") }) result.add("nginx")

        // GitHub workflows
        if (lower.any { it.startsWith(".github/workflows/") && (it.endsWith(".yml") || it.endsWith(".yaml")) }) result.add("github-actions")

        // Bruno
        if (lower.any { it.endsWith(".bru") || it.startsWith("bruno/") || it.startsWith(".bruno/") }) result.add("bruno")

        // OpenAPI (yml)
        if (lower.any {
                val file = it.substringAfterLast("/")
                (file.startsWith("openapi") || file.startsWith("swagger")) && (it.endsWith(".yml") || it.endsWith(".yaml")) ||
                    it.startsWith("api-docs/") || it.contains("openapi")
            }
        ) result.add("openapi")

        // .NET: razor / blazor / mvc
        val hasCsproj = lower.any { it.endsWith(".csproj") || it.endsWith(".sln") }
        val hasRazor = lower.any { it.endsWith(".razor") }
        val hasMvc = lower.any { it.startsWith("controllers/") || it.endsWith("controller.cs") } &&
            lower.any { it.endsWith(".cshtml") || it.startsWith("views/") }
        if (hasRazor) {
            result.add("blazor")
            result.add("razor")
        }
        if (hasMvc) result.add("mvc")
        // Si hay .NET pero no encaja en blazor/mvc, no se añade slug genérico: se queda vacío.

        // Postgres / Redis por contenido
        val combined = fileContents.values.joinToString("\n").lowercase()
        if (combined.contains("postgres")) result.add("postgresql")
        if (combined.contains("redis")) result.add("redis")

        return result.sorted()
    }

    /** Elige qué ficheros del árbol merece la pena descargar para inspeccionar contenido. */
    fun selectContentFiles(treePaths: List<String>): List<String> {
        val lowerMap = treePaths.associateBy({ it.lowercase() }, { it })
        return CONTENT_CANDIDATES
            .mapNotNull { lowerMap[it.lowercase()] }
            .distinct()
            .take(MAX_CONTENT_FILES)
    }
}
