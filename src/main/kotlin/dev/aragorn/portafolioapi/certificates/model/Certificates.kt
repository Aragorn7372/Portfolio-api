package dev.aragorn.portafolioapi.certificates.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.validator.constraints.Length
import java.time.LocalDate

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
        private fun extractDriveFileId(url: String): String {
            return Regex("""/file/d/([^/?]+)""")
                .find(url)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalArgumentException("Invalid url: $url")
        }
    }
}