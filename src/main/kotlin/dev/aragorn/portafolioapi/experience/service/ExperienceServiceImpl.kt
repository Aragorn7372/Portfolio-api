package dev.aragorn.portafolioapi.experience.service

import com.github.f4b6a3.ulid.UlidCreator
import dev.aragorn.portafolioapi.common.images.CloudinaryService
import dev.aragorn.portafolioapi.experience.client.ExperienceClient
import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.dto.StorageImage
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceNotFoundException
import dev.aragorn.portafolioapi.experience.mapper.ExperienceMapper
import dev.aragorn.portafolioapi.experience.model.Experience
import dev.aragorn.portafolioapi.experience.model.ExperienceImage
import dev.aragorn.portafolioapi.experience.repository.ExperienceRepository
import dev.aragorn.portafolioapi.experience.storage.StorageExperienZipImpl
import dev.aragorn.portafolioapi.experience.validation.ExperienceValidator
import jakarta.transaction.Transactional
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.util.logging.Logger

@Service
class ExperienceServiceImpl(
    private val repository: ExperienceRepository,
    private val mapper: ExperienceMapper,
    private val client: ExperienceClient,
    private val storage: StorageExperienZipImpl,
    private val validator: ExperienceValidator,
    private val cloudinary: CloudinaryService
): ExperienceService {
    private val log: Logger = Logger.getLogger(ExperienceServiceImpl::class.java.name)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["experiences"], allEntries = true),
            CacheEvict(cacheNames = ["experience-details"], allEntries = true)
        ]
    )
    override suspend fun refresh() {
        log.info("Refreshing experiences")
        val path = withTimeout(60_000) { client.getExperience() }
        var extracted: List<ExperienceStorageDto> = emptyList()
        try {
            val experience = withTimeout(60_000) {
                storage.readZip(path)
            }
            extracted = experience
            log.info("read ${experience.size} experiences from zip")
            validator.validates(experience)
            syncExperience(experience)
        } finally {
            log.info("cleaning zip and ${extracted.sumOf { it.images.size }} temporary images")
            Files.deleteIfExists(path)
            extracted.flatMap { it.images }.forEach { Files.deleteIfExists(it.file.toPath()) }
        }
    }
    private suspend fun syncExperience(
        experience: List<ExperienceStorageDto>
    ) {
        val actuales = repository.findAll()

        val actualesPorId = actuales.associateBy { it.id }
        val nuevasPorId = experience.associateBy { it.id }
        val creadas = mutableListOf<String>()
        val actualizadas = mutableListOf<String>()

        experience.forEach { remote ->
            val actual = actualesPorId[remote.id]

            if (actual == null) {
                log.info("creating experience ${remote.id} with ${remote.images.size} images")
                val nueva = mapper.experienceStorageDtotoModel(remote)

                coroutineScope {
                    val images = remote.images.map { image ->
                        async {
                            uploadImage(nueva, image)
                        }
                    }.awaitAll()

                    nueva.images.addAll(images)
                }

                repository.save(nueva)
                creadas.add(remote.id)

            } else {
                if (actual.title != remote.title) actual.title = remote.title
                if (actual.company != remote.company) actual.company = remote.company
                if (actual.location != remote.location) actual.location = remote.location
                if (actual.markdown != remote.mdFile) actual.markdown = remote.mdFile

                val actualesImagenes =
                    actual.images.associateBy { it.relativePath }

                val nuevasImagenes =
                    remote.images.associateBy { it.path }
                val remotasNuevas = remote.images
                    .filter { actualesImagenes[it.path] == null }
                val remotasModificadas = remote.images
                    .filter {
                        actualesImagenes[it.path]?.contentHash != null &&
                                actualesImagenes[it.path]?.contentHash != it.contentHash
                    }
                val eliminadas = actual.images
                    .filter { it.relativePath !in nuevasImagenes }
                log.info(
                    "updating experience ${actual.id}: new=${remotasNuevas.size}, " +
                        "modified=${remotasModificadas.size}, deleted=${eliminadas.size} images"
                )
                coroutineScope {
                    val nuevas = remotasNuevas
                        .map { image ->
                            async {
                                uploadImage(actual, image)
                            }
                        }
                        .awaitAll()
                    val modificadas = remotasModificadas
                        .map { image ->
                            async {
                                val actualImage = actualesImagenes[image.path]!!
                                log.info("re-uploading modified image ${image.path}")

                                val url = cloudinary.upload(
                                    image.file,
                                    actualImage.cloudinaryPublicId
                                )

                                actualImage to url
                            }
                        }
                        .awaitAll()
                    actual.images.addAll(nuevas)
                    modificadas.forEach { (image, url) ->
                        image.cloudinaryUrl = url
                        image.contentHash = remote.images
                            .first { it.path == image.relativePath }
                            .contentHash
                    }
                }

                eliminadas
                    .forEach { image ->
                        log.info("deleting image ${image.cloudinaryPublicId}")
                        cloudinary.delete(image.cloudinaryPublicId)
                        actual.images.remove(image)
                    }

                repository.save(actual)
                actualizadas.add(actual.id)
            }
        }
        val borradas = actuales.filter { it.id !in nuevasPorId }
        borradas
            .forEach { actual ->
                log.info("deleting experience ${actual.id} and its ${actual.images.size} images")
                actual.images.forEach { image ->
                    cloudinary.delete(image.cloudinaryPublicId)
                }

                repository.delete(actual)
            }
        log.info(
            "Experiences synchronized. created: $creadas, updated: $actualizadas, " +
                "deleted: ${borradas.map { it.id }}"
        )
    }
    private suspend fun uploadImage(
        experience: Experience,
        image: StorageImage
    ): ExperienceImage {
        val originalName = image.path.substringAfterLast("/")
        val baseName = originalName.substringBeforeLast(".").ifBlank { "image" }
        val publicId =
            "$baseName-${UlidCreator.getUlid()}"
        log.info("uploading image ${image.path} as $publicId")

        val url = withTimeout(10000){
            cloudinary.upload(
            image.file,
            publicId
        )}

        return ExperienceImage(
            experience = experience,
            filename = originalName,
            relativePath = image.path,
            cloudinaryPublicId = publicId,
            cloudinaryUrl = url,
            contentHash = image.contentHash
        )
    }
    @Cacheable(cacheNames = ["experiences"])
    override suspend fun getExperiences(): List<ExperienceResponseDto> {
        log.info("getting experiences")
        return repository.findAll()
            .map { mapper.experienceToDto(it) }
    }
    @Cacheable(cacheNames = ["experience-details"], key = "#id")
    override suspend fun getExperience(id: String): ExperienceDetailsResponseDto{
        log.info("getting experience $id")
        return repository.findExperienceById(id)?.let {
            mapper.experienceToDetailsDto(it)
        }?: run {
            log.warning("experience $id not found")
            throw ExperienceNotFoundException("Experience not found")
        }
    }
}
