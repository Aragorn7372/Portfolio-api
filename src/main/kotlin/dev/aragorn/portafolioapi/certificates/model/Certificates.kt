package dev.aragorn.portafolioapi.certificates.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.validator.constraints.Length
import java.time.LocalDate

/**
 * Certificado o título que se muestra en el portafolio.
 *
 * El id no se genera en la base de datos: se saca de la propia URL del documento, así el mismo
 * certificado tiene siempre el mismo id aunque cambien su título o su fecha. Gracias a eso, el
 * refresco puede saber qué certificados son nuevos, cuáles se actualizan y cuáles se han quitado.
 *
 * @property name título del certificado (máximo 200 caracteres).
 * @property date fecha de expedición.
 * @property url enlace público al documento.
 * @property id identificador del documento extraído de la URL. Es la clave primaria.
 */
@Entity
@Table(name = "certificates")
data class Certificates(

    @Column(nullable = false)
    @Length(max = 200)
    val name: String,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    val url: String,

    @Id
    @Column(nullable = false)
    val id: String
) {
    /**
     * Crea un certificado calculando su [id] a partir de la [url].
     *
     * @param name título del certificado.
     * @param date fecha de expedición.
     * @param url enlace al documento. Tiene que contener el segmento `/file/d/{id}`.
     * @throws IllegalArgumentException si la URL no contiene un identificador de documento.
     */
    constructor(
        name: String,
        date: LocalDate,
        url: String
    ) : this(
        name = name,
        date = date,
        url = url,
        id = extractDriveFileId(url)
    )

    companion object {
        /**
         * Extrae el identificador del documento del segmento `/file/d/{id}` de la URL.
         *
         * @param url enlace al documento.
         * @return el identificador.
         * @throws IllegalArgumentException si la URL no tiene ese segmento.
         */
        private fun extractDriveFileId(url: String): String {
            return Regex("""/file/d/([^/?]+)""")
                .find(url)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalArgumentException("Invalid url: $url")
        }
    }
}