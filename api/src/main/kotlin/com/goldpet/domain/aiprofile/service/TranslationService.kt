package com.goldpet.domain.aiprofile.service

interface TranslationService {
    fun translate(text: String, sourceLang: String = "KO", targetLang: String = "EN"): String
}
