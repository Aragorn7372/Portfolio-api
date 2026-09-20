package dev.aragorn.portafolioapi.visits.startup

import dev.aragorn.portafolioapi.visits.model.Visits
import dev.aragorn.portafolioapi.visits.repository.VisitsRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Asegura la fila única del contador (id = 1) en el primer arranque. */
@Component
class VisitsStartupSeed(
    private val repository: VisitsRepository,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!repository.existsById(1)) {
            repository.save(Visits(id = 1, total = 0))
        }
    }
}
