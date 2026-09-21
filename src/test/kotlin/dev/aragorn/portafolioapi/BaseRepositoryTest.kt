package dev.aragorn.portafolioapi

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Clase base para tests de repositorio (JPA) con Testcontainers.
 * Usa un singleton para mantener los contenedores vivos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class BaseRepositoryTest {

    companion object {

        // Obtener la instancia singleton
        private val containers = TestContainerConfig.getInstance()

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {

            // PostgreSQL
            registry.add("spring.datasource.url") {
                containers.getPostgresContainer().jdbcUrl
            }
            registry.add("spring.datasource.username") {
                containers.getPostgresContainer().username
            }
            registry.add("spring.datasource.password") {
                containers.getPostgresContainer().password
            }
            registry.add("spring.datasource.driver-class-name") {
                "org.postgresql.Driver"
            }


            // Redis (la app usa spring.data.redis.* + password)
            registry.add("spring.data.redis.host") {
                containers.getRedisContainer().host
            }
            registry.add("spring.data.redis.port") {
                containers.getRedisContainer().firstMappedPort
            }
            registry.add("spring.data.redis.password") {
                TestContainerConfig.REDIS_PASSWORD
            }
        }
    }
}