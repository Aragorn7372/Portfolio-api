package dev.aragorn.portafolioapi.common.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint de salud para comprobar que la API está levantada.
 *
 * Es una comprobación de *liveness*: responde en cuanto el servidor HTTP acepta peticiones y no
 * consulta PostgreSQL, Redis ni servicios externos. Así un fallo en una dependencia no hace que
 * la plataforma de despliegue reinicie la instancia.
 *
 * Es una ruta pública: no pide token de visita (regla `auth=false` en `app.gate.rules`) ni
 * secreto de origen (está en `app.origin.open-paths`), para que los health checks funcionen
 * sin pasar por el proxy.
 */
@RestController
class HealthController {

    /**
     * `GET /health`: indica que la API está levantada.
     *
     * @return `200 {"status":"UP"}`.
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "UP"))
}
