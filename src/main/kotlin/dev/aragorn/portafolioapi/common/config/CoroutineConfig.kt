package dev.aragorn.portafolioapi.common.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuración de las corrutinas que se lanzan en segundo plano.
 *
 * Define un único [CoroutineScope] compartido por los procesos que no dependen de una petición
 * HTTP, como los refrescos programados ([dev.aragorn.portafolioapi.common.schedulers.PortfolioScheduler])
 * y el refresco inicial ([dev.aragorn.portafolioapi.common.startup.PortfolioStartupRefresh]).
 */
@Configuration
class CoroutineConfig {
    /**
     * Scope de larga duración para las tareas de refresco del portafolio.
     *
     * - Usa [SupervisorJob], así que si falla una corrutina hija no se cancelan las demás ni
     *   el propio scope. Un fallo refrescando certificados no corta el refresco de proyectos.
     * - Se ejecuta en [Dispatchers.Default]. Las llamadas bloqueantes (HTTP, JPA) cambian
     *   a `Dispatchers.IO` dentro de cada servicio.
     *
     * @return el scope que se inyecta en los componentes que lanzan trabajo en segundo plano.
     */
    @Bean
    fun portfolioCoroutineScope(): CoroutineScope =CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
}