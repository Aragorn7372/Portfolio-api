package dev.aragorn.portafolioapi.visits.startup

import dev.aragorn.portafolioapi.visits.model.Visits
import dev.aragorn.portafolioapi.visits.repository.VisitsRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.ApplicationArguments

@ExtendWith(MockitoExtension::class)
class VisitsStartupSeedTest {
    @Mock
    private lateinit var repository: VisitsRepository

    @InjectMocks
    private lateinit var seed: VisitsStartupSeed

    @Test
    @DisplayName("run sin fila la crea")
    fun run() {
        val args: ApplicationArguments = mock()
        whenever(repository.existsById(1L)).thenReturn(false)

        seed.run(args)

        verify(repository, times(1)).existsById(1L)
        verify(repository, times(1)).save(Visits(id = 1, total = 0))
    }

    @Test
    @DisplayName("run con fila no guarda")
    fun runExisting() {
        val args: ApplicationArguments = mock()
        whenever(repository.existsById(1L)).thenReturn(true)

        seed.run(args)

        verify(repository, times(1)).existsById(1L)
        verify(repository, times(0)).save(any())
    }
}
