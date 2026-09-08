package com.goldpet.domain.common.dto

data class AppVersionResponse(
    val forceUpdate: Boolean,
    val softUpdate: Boolean,
    val minimumVersion: String,
    val latestVersion: String,
    val updateUrl: String,
    val updateMessage: String? = null
)
