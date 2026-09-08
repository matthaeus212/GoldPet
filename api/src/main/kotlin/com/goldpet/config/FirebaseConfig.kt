package com.goldpet.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import jakarta.annotation.PostConstruct
import java.io.File
import java.io.InputStream

@Configuration
@Profile("!codegen")
class FirebaseConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                val serviceAccount = findServiceAccount()
                if (serviceAccount != null) {
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build()
                    FirebaseApp.initializeApp(options)
                    log.info("Firebase Admin SDK initialized successfully")
                } else {
                    log.warn("firebase-service-account.json not found, FCM push will not work")
                }
            } catch (e: Exception) {
                log.error("Failed to initialize Firebase Admin SDK: {}", e.message)
            }
        }
    }

    private fun findServiceAccount(): InputStream? {
        // 1. classpath (JAR 내부 - 로컬 개발용)
        val classpath = javaClass.classLoader.getResourceAsStream("firebase-service-account.json")
        if (classpath != null) {
            log.info("Loading firebase-service-account.json from classpath")
            return classpath
        }

        // 2. JAR과 같은 디렉토리 (서버 배포용)
        val externalFile = File("firebase-service-account.json")
        if (externalFile.exists()) {
            log.info("Loading firebase-service-account.json from working directory: {}", externalFile.absolutePath)
            return externalFile.inputStream()
        }

        return null
    }
}
