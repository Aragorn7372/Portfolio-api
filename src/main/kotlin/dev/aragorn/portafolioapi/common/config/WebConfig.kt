package dev.aragorn.portafolioapi.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Configuración CORS de la API.
 *
 * Solo permite llamadas desde los orígenes de `app.host.allowed`, con los métodos `GET`, `POST`
 * y `OPTIONS` y con credenciales. Las credenciales hacen falta para que el navegador envíe la
 * cookie `visit_jwt` en las peticiones entre dominios.
 *
 * Spring Security aplica esta configuración gracias a `cors(...)` en [SecurityConfig].
 */
@Configuration
class WebConfig : WebMvcConfigurer {
    /**
     * Patrón de orígenes permitidos (`app.host.allowed`). Acepta los comodines de
     * `allowedOriginPatterns` de Spring. El valor por defecto `*` solo tiene sentido en desarrollo.
     */
    @Value($$"${app.host.allowed:*}")
    private var allowedHosts: String = "*"

    /**
     * Registra la política CORS para todas las rutas de la API.
     *
     * @param registry registro de CORS de Spring MVC.
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedHosts)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowCredentials(true)
    }
}
