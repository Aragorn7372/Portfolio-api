package dev.aragorn.portafolioapi.projects.repository

import dev.aragorn.portafolioapi.projects.model.Owner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OwnersRepository : JpaRepository<Owner, Long> {
    fun findByName(name: String): Owner?
}