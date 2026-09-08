package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.UserProfileImage
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileImageRepository : JpaRepository<UserProfileImage, Long>
