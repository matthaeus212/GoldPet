package com.goldpet.domain.emoticon.dto

import com.goldpet.domain.emoticon.entity.EmoticonPack

data class EmoticonPackResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val isActive: Boolean
) {
    companion object {
        fun from(pack: EmoticonPack): EmoticonPackResponse {
            return EmoticonPackResponse(
                id = pack.id,
                name = pack.name,
                description = pack.description,
                sortOrder = pack.sortOrder,
                isActive = pack.isActive
            )
        }
    }
}
