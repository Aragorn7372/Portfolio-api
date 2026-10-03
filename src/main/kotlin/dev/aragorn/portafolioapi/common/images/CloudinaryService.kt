package dev.aragorn.portafolioapi.common.images

import com.cloudinary.Cloudinary
import dev.aragorn.portafolioapi.common.exceptions.CloudinaryException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.File
import java.util.logging.Logger


@Service
class CloudinaryService(
    private val cloudinary: Cloudinary
) {
    private val log: Logger = Logger.getLogger(CloudinaryService::class.java.name)

    suspend fun upload(file: File, publicId: String): String = withContext(Dispatchers.IO) {
        log.info("Cloudinary upload $publicId")
        val result = cloudinary.uploader().upload(
            file,
            mapOf<String, Any>(
                "folder" to "portfolio/experiences",
                "public_id" to publicId,
                "overwrite" to true
            )
        )

        (result["secure_url"] as? String)
            ?: run {
                log.severe("Cloudinary no devolvió secure_url para $publicId")
                throw CloudinaryException("Cloudinary no devolvió secure_url")
            }
    }
    suspend fun delete(publicId: String) = withContext(Dispatchers.IO) {
        log.info("Cloudinary delete $publicId")
        val result = cloudinary.uploader().destroy(
            publicId,
            mapOf("resource_type" to "image")
        )
        if (result?.get("result") != "ok") {
            log.warning("Cloudinary delete $publicId devolvió ${result?.get("result")}")
        }
        result
    }
}
