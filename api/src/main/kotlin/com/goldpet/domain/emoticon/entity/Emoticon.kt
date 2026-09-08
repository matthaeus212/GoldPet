package com.goldpet.domain.emoticon.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "emoticons")
class Emoticon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    var pack: EmoticonPack,

    @Column(length = 50)
    var code: String? = null,

    @Column(length = 100)
    var name: String? = null,

    @Column(name = "image_url", nullable = false, length = 500)
    var imageUrl: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) : BaseTimeEntity()
