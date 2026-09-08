package com.goldpet.domain.user.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserDataExportService(
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository
) {
    fun exportUserData(userId: Long): Map<String, Any?> {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        val pets = petRepository.findAllByOwnerId(userId)
        val posts = postRepository.findAllByUserId(userId)
        val comments = commentRepository.findAllByUserId(userId)

        return mapOf(
            "exportedAt" to LocalDateTime.now().toString(),
            "user" to mapOf(
                "id" to user.id,
                "email" to user.email,
                "nickname" to user.nickname,
                "name" to user.name,
                "gender" to user.gender,
                "birthDate" to user.birthDate?.toString(),
                "phoneNumber" to user.phoneNumber,
                "mainLocationText" to user.mainLocationText,
                "profileImageUrl" to (user.profileImages.firstOrNull()?.imageUrl ?: user.profileImageUrl),
                "intro" to user.intro,
                "mbti" to user.mbti,
                "createdAt" to user.createdAt.toString(),
                "isNotificationEnabled" to user.isNotificationEnabled
            ),
            "pets" to pets.map { pet ->
                mapOf(
                    "id" to pet.id,
                    "name" to pet.name,
                    "species" to pet.species.name,
                    "breed" to pet.breed?.name,
                    "gender" to pet.gender,
                    "birthDate" to pet.birthDate?.toString(),
                    "profileImageUrl" to (pet.profileImages.firstOrNull()?.imageUrl ?: pet.profileImageUrl)
                )
            },
            "posts" to posts.map { post ->
                mapOf(
                    "id" to post.id,
                    "title" to post.title,
                    "content" to post.content,
                    "createdAt" to post.createdAt.toString()
                )
            },
            "comments" to comments.map { comment ->
                mapOf(
                    "id" to comment.id,
                    "content" to comment.content,
                    "createdAt" to comment.createdAt.toString()
                )
            }
        )
    }
}
