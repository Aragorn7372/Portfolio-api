package dev.aragorn.portafolioapi.common.images

import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import dev.aragorn.portafolioapi.common.exceptions.CloudinaryException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@ExtendWith(MockitoExtension::class)
class CloudinaryServiceTest {
    @Mock
    private lateinit var cloudinary: Cloudinary

    @Mock
    private lateinit var uploader: Uploader

    @InjectMocks
    private lateinit var service: CloudinaryService

    private val file = File("logo.png")
    private val publicId = "logo-01JTEST"
    private val uploadOptions = mapOf<String, Any>(
        "folder" to "portfolio/experiences",
        "public_id" to publicId,
        "overwrite" to true
    )

    @BeforeEach
    fun setup() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
    }

    @Test
    @DisplayName("upload bien, devuelve la secure_url")
    fun upload() = runTest {
        whenever(uploader.upload(file, uploadOptions))
            .thenReturn(mapOf("secure_url" to "https://res.cloudinary.com/test/logo.png"))

        val result = service.upload(file, publicId)

        assertEquals("https://res.cloudinary.com/test/logo.png", result)
        verify(uploader, times(1)).upload(file, uploadOptions)
    }

    @Test
    @DisplayName("upload mal, sin secure_url lanza CloudinaryException")
    fun uploadWithoutSecureUrl() = runTest {
        whenever(uploader.upload(file, uploadOptions)).thenReturn(mapOf("url" to "http://inseguro"))

        val exception = assertThrows<CloudinaryException> {
            service.upload(file, publicId)
        }

        assertEquals("Cloudinary no devolvió secure_url", exception.message)
        verify(uploader, times(1)).upload(file, uploadOptions)
    }

    @Test
    @DisplayName("delete bien, borra la imagen por public id")
    fun delete() = runTest {
        whenever(uploader.destroy(publicId, mapOf("resource_type" to "image")))
            .thenReturn(mapOf("result" to "ok"))

        service.delete(publicId)

        verify(uploader, times(1)).destroy(publicId, mapOf("resource_type" to "image"))
    }
}
