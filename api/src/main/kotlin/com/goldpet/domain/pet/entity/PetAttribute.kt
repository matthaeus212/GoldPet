package com.goldpet.domain.pet.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "pet_attributes")
class PetAttribute(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: AttributeCategory,

    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var inputType: InputType,

    @Column(nullable = false)
    var displayOrder: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var options: List<AttributeOption>? = mutableListOf()
) {
    fun update(name: String, inputType: InputType, displayOrder: Int, options: List<AttributeOption>) {
        this.name = name
        this.inputType = inputType
        this.displayOrder = displayOrder
        this.options = options
    }
}

enum class AttributeCategory {
    TRAIT,    // 성향 (Activity, Friendliness...)
    INTEREST, // 관심사 (Toy, Walk...)
    ALLERGY   // 알러지 (Nuts, Shellfish...)
}

enum class InputType {
    SELECT,
    RADIO,
    TEXT
}

data class AttributeOption(
    val label: String,
    val value: String
)
