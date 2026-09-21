package dev.aragorn.portafolioapi.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    @Value($$"${app.host.allowed:*}")
    private var allowedHosts: String = "*"

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedHosts)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowCredentials(true)
    }
}
