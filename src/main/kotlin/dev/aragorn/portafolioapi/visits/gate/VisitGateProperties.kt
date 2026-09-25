package dev.aragorn.portafolioapi.visits.gate

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Regla de acceso para un grupo de rutas.
 *
 * @property pattern patrón Ant de las rutas afectadas (acepta `*` y `**`). Una regla con patrón
 *   vacío se ignora.
 * @property auth `true` si las rutas exigen un token de visita válido y aplican límite de
 *   peticiones. `false` si son públicas.
 * @property fpPerMinute peticiones por minuto que se permiten a un mismo visitante (huella del
 *   token) en este grupo de rutas.
 * @property ipPerMinute peticiones por minuto que se permiten a una misma IP. El contador es
 *   global: lo comparten todas las reglas.
 */
data class GateRule(
    val pattern: String = "",
    val auth: Boolean = false,
    val fpPerMinute: Int = 120,
    val ipPerMinute: Int = 30,
)


/**
 * Reglas de acceso por ruta (prefijo `app.gate`).
 *
 * Proteger un endpoint nuevo solo requiere añadir una regla en la configuración, sin tocar código.
 * Las reglas se usan en dos sitios:
 * - [dev.aragorn.portafolioapi.common.config.SecurityConfig] marca como públicas o autenticadas
 *   las rutas de cada regla.
 * - [VisitJwtFilter] busca la **primera** regla que coincide con la ruta y aplica el token y los
 *   límites. Por eso, si dos patrones se solapan, importa el orden.
 *
 * @property rules reglas en orden de prioridad.
 */
@ConfigurationProperties(prefix = "app.gate")
data class VisitGateProperties(
    val rules: List<GateRule> = emptyList(),
)
