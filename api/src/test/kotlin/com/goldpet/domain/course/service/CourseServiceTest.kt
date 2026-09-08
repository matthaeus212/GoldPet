package com.goldpet.domain.course.service

import com.amazonaws.services.s3.AmazonS3
import com.goldpet.domain.course.entity.CourseDifficulty
import com.goldpet.domain.course.entity.WalkCourse
import com.goldpet.domain.course.repository.CourseSpotRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.infra.GeocodingService
import com.goldpet.infra.StaticMapService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CourseServiceTest {

    @Mock private lateinit var walkCourseRepository: WalkCourseRepository
    @Mock private lateinit var courseSpotRepository: CourseSpotRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var walkRepository: WalkRepository
    @Mock private lateinit var geocodingService: GeocodingService
    @Mock private lateinit var staticMapService: StaticMapService
    @Mock private lateinit var amazonS3: AmazonS3

    private lateinit var courseService: CourseService

    private val gf = GeometryFactory(PrecisionModel(), 4326)

    @BeforeEach
    fun setUp() {
        // Manual construction required because CourseService has @Value String parameters
        courseService = CourseService(
            walkCourseRepository, courseSpotRepository, userRepository, walkRepository,
            geocodingService, staticMapService, amazonS3,
            "test-bucket", "http://localhost:9100"
        )
    }

    @Test
    fun `getCourse_shouldReturnResponse_whenCourseIsPublished`() {
        val path = gf.createLineString(
            arrayOf(Coordinate(126.978, 37.566), Coordinate(126.979, 37.567))
        )
        val startLoc = gf.createPoint(Coordinate(126.978, 37.566))
        val author = User(
            id = 1L, email = "author@test.com", oauthProvider = "GOOGLE", oauthId = "g1",
            username = null, password = null, nickname = "TestAuthor",
            name = null, birthDate = null, phoneNumber = null,
            gender = null, birthYear = null, mainLocationText = null,
            mainLocationGeom = null, profileImageUrl = null
        )
        val course = WalkCourse(
            id = 10L, author = author, title = "Han River Course",
            path = path, distanceKm = 2.5, estimatedMinutes = 30,
            difficulty = CourseDifficulty.MODERATE, region = "Seoul", startLocation = startLoc
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        `when`(walkCourseRepository.findById(10L)).thenReturn(Optional.of(course))
        `when`(courseSpotRepository.findByCourseIdOrderByOrderIndex(10L)).thenReturn(emptyList())

        val result = courseService.getCourse(10L)

        assertThat(result).isNotNull
        assertThat(result.id).isEqualTo(10L)
        assertThat(result.title).isEqualTo("Han River Course")
        assertThat(result.distanceKm).isEqualTo(2.5)
        verify(walkCourseRepository).findById(10L)
        verify(courseSpotRepository).findByCourseIdOrderByOrderIndex(10L)
    }
}
