package com.goldpet.config.initializers

import com.goldpet.domain.user.entity.Hobby
import com.goldpet.domain.user.entity.Interest
import com.goldpet.domain.user.repository.HobbyRepository
import com.goldpet.domain.user.repository.InterestRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!codegen")
class TagDataInitializer(
    private val interestRepository: InterestRepository,
    private val hobbyRepository: HobbyRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (interestRepository.count() == 0L) {
            val interests = listOf(
                "만남", "여행", "건강", "패션/뷰티", "경제/투자", 
                "스포츠", "문화/예술", "음악", "게임", "산책"
            )
            interestRepository.saveAll(interests.mapIndexed { index, name -> 
                Interest(name = name, orderIndex = index) 
            })
        }

        if (hobbyRepository.count() == 0L) {
            val hobbies = listOf(
                "러닝", "요리", "캠핑", "영화감상", "사진촬영", 
                "자전거", "레저", "스포츠", "명상/요가", "댄스", 
                "악기연주", "보드게임", "DIY/공예", "게임", "여행", "그리기"
            )
            hobbyRepository.saveAll(hobbies.mapIndexed { index, name -> 
                Hobby(name = name, orderIndex = index) 
            })
        }
    }
}
