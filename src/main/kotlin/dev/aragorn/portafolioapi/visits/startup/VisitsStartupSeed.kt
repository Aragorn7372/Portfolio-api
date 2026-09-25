package dev.aragorn.portafolioapi.visits.startup

import dev.aragorn.portafolioapi.visits.model.Visits
import dev.aragorn.portafolioapi.visits.repository.VisitsRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Crea la fila del contador de visitas (`id = 1`, `total = 0`) al arrancar, si todavía no existe.
 *
 * Es idempotente: si la fila ya existe no hace nada, así que nunca reinicia el contador.
 *
 * @param repository repositorio del contador.
 */
@Component
class VisitsStartupSeed(
    private val repository: VisitsRepository,
) : ApplicationRunner {

    /**
     * Comprueba si existe la fila del contador y la crea si falta.
     *
     * @param args argumentos de arranque (no se usan).
     */
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!repository.existsById(1)) {
            repository.save(Visits(id = 1, total = 0))
        }
    }
}
