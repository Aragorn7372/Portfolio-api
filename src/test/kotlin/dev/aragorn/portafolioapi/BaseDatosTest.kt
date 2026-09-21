package dev.aragorn.portafolioapi

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
abstract class BaseDatosTest {

    companion object {
        private val containers = TestContainerConfig.getInstance()

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
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