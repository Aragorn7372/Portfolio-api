package dev.aragorn.portafolioapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PortafolioApiApplication

fun main(args: Array<String>) {
    runApplication<PortafolioApiApplication>(*args)
}
