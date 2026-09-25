package dev.aragorn.portafolioapi.common.config

import dev.aragorn.portafolioapi.visits.gate.GateRule
import dev.aragorn.portafolioapi.visits.gate.VisitGateProperties
import dev.aragorn.portafolioapi.visits.gate.VisitJwtFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Configuración de Spring Security de la API.
 *
 * La API no tiene usuarios ni sesiones. La "autenticación" consiste en tener un token de visita
 * válido (ver [VisitJwtFilter]), y qué rutas lo exigen se decide en la configuración
 * (`app.gate.rules`, ver [VisitGateProperties]), no en el código.
 *
 * Resumen de la cadena:
 * - CSRF desactivado: no hay sesión ni formularios. El token viaja en una cookie `SameSite=Lax`
 *   o en la cabecera `Authorization`.
 * - CORS activado con la configuración de [WebConfig].
 * - Sin estado ([SessionCreationPolicy.STATELESS]): el contexto de seguridad se guarda solo en
 *   los atributos de la petición.
 * - Las reglas con `auth=false` y `/error` son públicas. Las reglas con `auth=true` exigen
 *   autenticación. El resto de rutas son públicas.
 * - [VisitJwtFilter] va antes de [UsernamePasswordAuthenticationFilter].
 *
 * Por delante de esta cadena se ejecuta
 * [dev.aragorn.portafolioapi.visits.gate.OriginGateFilter], que comprueba el origen de la petición.
 *
 * @param visitJwtFilter filtro que valida el token de visita y aplica los límites de peticiones.
 * @param gateProperties reglas de acceso por ruta.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val visitJwtFilter: VisitJwtFilter,
    private val gateProperties: VisitGateProperties,
) {

    /**
     * Construye la [SecurityFilterChain] a partir de las reglas de `app.gate.rules`.
     *
     * @param http constructor de seguridad HTTP de Spring.
     * @return la cadena de filtros de seguridad.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val (open, closed) = gateProperties.rules.partition { !it.auth }
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(RequestAttributeSecurityContextRepository()) }
            .authorizeHttpRequests {
                it.requestMatchers("/error").permitAll()
                if (open.isNotEmpty()) {
                    it.requestMatchers(*open.map(GateRule::pattern).toTypedArray()).permitAll()
                }
                if (closed.isNotEmpty()) {
                    it.requestMatchers(*closed.map(GateRule::pattern).toTypedArray()).authenticated()
                }
                it.anyRequest().permitAll()
            }
            .addFilterBefore(visitJwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
