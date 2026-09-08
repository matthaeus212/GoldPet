package com.goldpet.domain.auth.service

interface SmsSender {
    fun send(phoneNumber: String, message: String)
}
