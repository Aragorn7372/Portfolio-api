package dev.aragorn.portafolioapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Clase de arranque de la API del portafolio.
 *
 * Además de la autoconfiguración de Spring Boot, activa [ConfigurationPropertiesScan] para que
 * las clases anotadas con `@ConfigurationProperties` (por ejemplo
 * [dev.aragorn.portafolioapi.projects.config.GithubProperties] y
 * [dev.aragorn.portafolioapi.visits.gate.VisitGateProperties]) se registren solas sin
 * declararlas una a una.
 *
 * También activa [EnableScheduling], necesario para que se ejecuten los refrescos periódicos de
 * [dev.aragorn.portafolioapi.common.schedulers.PortfolioScheduler].
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class PortafolioApiApplication

/**
 * Punto de entrada de la aplicación.
 *
 * @param args argumentos de línea de comandos que se pasan tal cual a Spring Boot.
 */
fun main(args: Array<String>) {
    runApplication<PortafolioApiApplication>(*args)
}
