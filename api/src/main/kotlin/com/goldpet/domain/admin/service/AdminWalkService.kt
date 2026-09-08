package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.entity.BestWalkCouple
import com.goldpet.domain.walk.repository.BestWalkCoupleRepository
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.domain.walk.service.PhotoUrlSigner
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException

@Service
@Transactional(readOnly = true)
class AdminWalkService(
    private val walkRepository: WalkRepository,
    private val petRepository: PetRepository,
    private val userRepository: UserRepository,
    private val bestWalkCoupleRepository: BestWalkCoupleRepository,
    private val photoUrlSigner: PhotoUrlSigner
) {
    fun getWalks(
        page: Int,
        size: Int,
        userNickname: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        minDistance: Double?,
        maxDistance: Double?
    ): Page<WalkAdminResponse> {
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "start_time"))
        return walkRepository.findAllForAdmin(
            userNickname = userNickname,
            startDate = startDate?.atStartOfDay(),
            endDate = endDate?.atTime(23, 59, 59),
            minDistance = minDistance,
            maxDistance = maxDistance,
            pageable = pageable
        ).map { WalkAdminResponse.from(it) }
    }

    fun getWalkDetail(walkId: Long): WalkDetailResponse {
        val walk = walkRepository.findById(walkId)
            .orElseThrow { NotFoundException("Walk not found: $walkId") }

        // Fetch pet info separately (Walk has no Pet relationship)
        val pets = petRepository.findByOwnerId(walk.user.id).map { pet ->
            WalkDetailPetInfo(
                petId = pet.id,
                petName = pet.name,
                species = pet.species.name,
                breed = pet.breed?.name
            )
        }

        val pathCoordinates = walk.path.coordinates.map { coord ->
            CoordinateDto(latitude = coord.y, longitude = coord.x)
        }

        return WalkDetailResponse(
            id = walk.id,
            userId = walk.user.id,
            userNickname = walk.user.nickname,
            userProfileImageUrl = walk.user.profileImageUrl,
            userPets = pets.ifEmpty { null },
            startTime = walk.startTime,
            endTime = walk.endTime,
            distanceKm = walk.distanceKm,
            durationSeconds = walk.durationSeconds,
            caloriesBurned = walk.caloriesBurned,
            notes = walk.notes,
            pathCoordinates = pathCoordinates,
            spots = walk.spots.map { WalkSpotAdminDto.from(it, photoUrlSigner) },
            createdAt = walk.createdAt
        )
    }

    @Transactional
    fun deleteWalk(walkId: Long, reason: String) {
        val walk = walkRepository.findById(walkId)
            .orElseThrow { NotFoundException("Walk not found: $walkId") }
        walkRepository.delete(walk)
    }

    fun getDetailedStats(
        period: String?,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): WalkStatsDetailResponse {
        val now = LocalDateTime.now()
        val from = startDate?.atStartOfDay() ?: now.minusDays(30)
        val to = endDate?.atTime(23, 59, 59) ?: now

        val walks = walkRepository.findAllByStartTimeBetween(from, to)

        val totalWalks = walks.size.toLong()
        val totalDistance = walks.sumOf { it.distanceKm }
        val avgDistance = if (totalWalks > 0) totalDistance / totalWalks else 0.0
        val avgDuration = if (totalWalks > 0) walks.sumOf { it.durationSeconds } / totalWalks / 60.0 else 0.0

        val dailyBreakdown = walks
            .groupBy { it.startTime.toLocalDate() }
            .map { (date, dayWalks) ->
                DailyStatEntry(
                    date = date,
                    count = dayWalks.size,
                    totalDistanceKm = dayWalks.sumOf { it.distanceKm }
                )
            }
            .sortedBy { it.date }

        return WalkStatsDetailResponse(
            totalWalks = totalWalks,
            totalDistanceKm = totalDistance,
            averageDistanceKm = avgDistance,
            averageDurationMinutes = avgDuration,
            dailyBreakdown = dailyBreakdown
        )
    }

    fun getAdminRanking(yearMonth: String, size: Int): List<WalkRankingAdminResponse> {
        val ym = parseYearMonth(yearMonth)
        val startOfMonth = ym.atDay(1).atStartOfDay()
        val startOfNextMonth = ym.plusMonths(1).atDay(1).atStartOfDay()
        val pageable = PageRequest.of(0, size)
        val rows = walkRepository.findCalendarRankings(startOfMonth, startOfNextMonth, null, pageable)
        return rows.mapIndexed { index, row ->
            WalkRankingAdminResponse(
                rank = index + 1,
                userId = (row[0] as Number).toLong(),
                nickname = row[1] as? String,
                profileImageUrl = row[2] as? String,
                petId = (row[3] as? Number)?.toLong(),
                petName = row[4] as? String,
                petProfileImageUrl = row[5] as? String,
                totalDistanceKm = (row[6] as Number).toDouble(),
                totalMinutes = (row[7] as Number).toLong(),
                totalGold = (row[8] as Number).toInt(),
                walkCount = (row[9] as Number).toInt()
            )
        }
    }

    fun getBestCouple(yearMonth: String): BestWalkCoupleResponse {
        parseYearMonth(yearMonth)
        return bestWalkCoupleRepository.findByYearMonth(yearMonth)?.let { BestWalkCoupleResponse.from(it) }
            ?: throw NotFoundException("No best couple selected for $yearMonth")
    }

    @Transactional
    fun setBestCouple(request: BestWalkCoupleRequest): BestWalkCoupleResponse {
        val ym = parseYearMonth(request.yearMonth)

        val user = userRepository.findById(request.userId)
            .orElseThrow { NotFoundException("User not found: ${request.userId}") }

        val pet = petRepository.findById(request.petId)
            .orElseThrow { NotFoundException("Pet not found: ${request.petId}") }

        if (pet.owner.id != user.id) {
            throw BadRequestException("Pet ${request.petId} does not belong to user ${request.userId}")
        }

        val startOfMonth = ym.atDay(1).atStartOfDay()
        val startOfNextMonth = ym.plusMonths(1).atDay(1).atStartOfDay()
        if (!walkRepository.existsByUserIdAndStartTimeBetween(user.id, startOfMonth, startOfNextMonth)) {
            throw BadRequestException("User ${request.userId} has no walks in ${request.yearMonth}")
        }

        // Upsert: delete existing entry if present
        bestWalkCoupleRepository.findByYearMonth(request.yearMonth)?.let {
            bestWalkCoupleRepository.delete(it)
            bestWalkCoupleRepository.flush()
        }

        val saved = bestWalkCoupleRepository.save(
            BestWalkCouple(
                yearMonth = request.yearMonth,
                user = user,
                pet = pet,
                selectedBy = null // admin_users is a separate table from users
            )
        )
        return BestWalkCoupleResponse.from(saved)
    }

    @Transactional
    fun deleteBestCouple(yearMonth: String) {
        parseYearMonth(yearMonth)
        bestWalkCoupleRepository.deleteByYearMonth(yearMonth)
    }

    private fun parseYearMonth(yearMonth: String): YearMonth {
        return try {
            YearMonth.parse(yearMonth)
        } catch (e: DateTimeParseException) {
            throw BadRequestException("Invalid yearMonth format: $yearMonth (expected YYYY-MM)")
        }
    }
}
