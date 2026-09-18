package dev.aragorn.portafolioapi.projects.detector

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TechStackDetectorTest {

    private val detector = TechStackDetector()

    @Test
    fun `detecta stack completo de portafolio`() {
        val paths = listOf(
            "gradlew",
            "gradlew.bat",
            "build.gradle.kts",
            "package.json",
            "Dockerfile",
            "compose.yaml",
            "nginx/nginx.conf",
            ".github/workflows/ci.yml",
            "bruno/api.bru",
            "openapi.yaml",
            "src/main/resources/application.properties",
        )
        val contents = mapOf(
            "compose.yaml" to "services:\n  db:\n    image: postgres:12-alpine\n  cache:\n    image: redis:7-alpine",
            "src/main/resources/application.properties" to "spring.datasource.url=jdbc:postgresql://localhost/db",
        )

        val result = detector.detect(paths, contents)

        assertEquals(
            listOf("bruno", "compose", "docker", "github-actions", "gradle", "nginx", "npm", "openapi", "postgresql", "redis"),
            result,
        )
    }

    @Test
    fun `detecta blazor razor y mvc dotnet`() {
        val blazor = detector.detect(listOf("App.csproj", "Pages/Index.razor", "Program.cs"))
        assertTrue(blazor.contains("blazor"))
        assertTrue(blazor.contains("razor"))

        val mvc = detector.detect(listOf("Web.csproj", "Controllers/HomeController.cs", "Views/Home/Index.cshtml"))
        assertTrue(mvc.contains("mvc"))
    }

    @Test
    fun `repo vacio no detecta nada`() {
        assertEquals(emptyList<String>(), detector.detect(emptyList()))
    }

    @Test
    fun `selectContentFiles limita a maximo 3 y conserva path original`() {
        val tree = listOf(
            "compose.yaml",
            "application.properties",
            ".env",
            "Dockerfile",
            "README.md",
        )
        assertEquals(listOf("compose.yaml", "application.properties", ".env"), detector.selectContentFiles(tree))
    }
}
