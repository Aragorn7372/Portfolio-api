package dev.aragorn.portafolioapi.visits.gate

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Obtiene la IP real del cliente de una petición.
 *
 * En producción la API está detrás del proxy perimetral y del balanceador de la plataforma de
 * despliegue, así que `remoteAddr` es la IP de esa infraestructura y no la del visitante. La IP real
 * solo llega en cabeceras. Esta clase centraliza cómo se leen, para que el conteo de visitas
 * ([dev.aragorn.portafolioapi.visits.controller.VisitsController]) y el límite de peticiones
 * ([VisitJwtFilter]) usen siempre la misma IP.
 *
 * Orden de preferencia:
 * 1. La cabecera con la IP del cliente que añade el proxy perimetral de confianza. Solo puede
 *    llegar a través de ese proxy, porque [OriginGateFilter] bloquea las peticiones directas.
 * 2. La primera IP de `X-Forwarded-For`, pero solo si la conexión viene de uno de los proxies de
 *    [trustedProxies]. Si no, cualquiera podría falsificar su IP con esa cabecera.
 * 3. La IP de la conexión TCP (`remoteAddr`).
 *
 * @param trustedProxies IPs de proxies de confianza, separadas por comas
 *   (`app.visits.trusted-proxies`). Vacío = no se hace caso a `X-Forwarded-For`.
 */
@Component
class ClientIpResolver(
    @Value("\${app.visits.trusted-proxies:}")
    trustedProxies: String,
) {

    private val trusted = trustedProxies.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    /**
     * Devuelve la IP real del cliente.
     *
     * @param req petición entrante.
     * @return la IP del cliente, o `"unknown"` si no se puede saber.
     */
    fun resolve(req: HttpServletRequest): String {
        req.getHeader("CF-Connecting-IP")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val forwarded = req.getHeader("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim().orEmpty()
        val remote = req.remoteAddr ?: "unknown"
        return if (forwarded.isNotBlank() && remote in trusted) forwarded else remote
    }
}
