package com.goldpet.domain.pet.repository

import com.goldpet.domain.pet.entity.Pet
import org.springframework.data.jpa.repository.JpaRepository

interface PetRepository : JpaRepository<Pet, Long> {
    fun findByOwnerId(userId: Long): List<Pet>

    /** PERF-003: 친구추천 핫패스에서 owner 별 findByOwnerId N회 호출을 1회 IN 배치로 대체. */
    fun findByOwnerIdIn(ownerIds: Collection<Long>): List<Pet>

    fun findAllByOwnerId(userId: Long): List<Pet>
    fun countByOwnerId(userId: Long): Long
    fun findFirstByOwnerIdOrderByCreatedAtAsc(ownerId: Long): Pet?
}

