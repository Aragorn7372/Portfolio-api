package dev.aragorn.portafolioapi

import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class TestContainerConfig private constructor() {

    private val postgresContainer: PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:15"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")

    private val redisContainer: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)

    init {
        postgresContainer.start()
        redisContainer.start()

        Runtime.getRuntime().addShutdownHook(Thread {
            postgresContainer.stop()
            redisContainer.stop()
        })
    }

    fun getPostgresContainer(): PostgreSQLContainer = postgresContainer

    fun getRedisContainer(): GenericContainer<*> = redisContainer

    companion object {
        const val REDIS_PASSWORD = "testpass"
        private var instance: TestContainerConfig? = null

        fun getInstance(): TestContainerConfig {
            if (instance == null) {
                instance = TestContainerConfig()
            }
            return instance!!
        }
    }
}