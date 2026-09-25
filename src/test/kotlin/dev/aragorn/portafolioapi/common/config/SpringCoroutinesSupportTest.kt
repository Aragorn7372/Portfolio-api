package dev.aragorn.portafolioapi.common.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Guardián: Spring MVC necesita este puente en runtime para invocar
 * controladores suspend. Si desaparece del classpath, todo endpoint
 * suspend devuelve 500 (los tests unitarios no lo cazan).
 */
class SpringCoroutinesSupportTest {
    @Test
    @DisplayName("spring mvc encuentra el puente de corrutinas reactor")
    fun coroutinesReactorPresente() {
        val monoKt = assertDoesNotThrow {
            Class.forName("kotlinx.coroutines.reactor.MonoKt")
        }
        val mono = assertDoesNotThrow {
            Class.forName("reactor.core.publisher.Mono")
        }

        assertEquals("kotlinx.coroutines.reactor.MonoKt", monoKt.name)
        assertEquals("reactor.core.publisher.Mono", mono.name)
    }
}
