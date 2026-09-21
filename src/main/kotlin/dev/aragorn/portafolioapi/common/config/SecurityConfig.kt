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

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val visitJwtFilter: VisitJwtFilter,
    private val gateProperties: VisitGateProperties,
) {

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
