package com.goldpet.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class S3Config(
    @Value("\${S3_ENDPOINT}") private val endpoint: String,
    @Value("\${S3_ACCESS_KEY}") private val accessKey: String,
    @Value("\${S3_SECRET_KEY}") private val secretKey: String,
) {
    companion object {
        private const val AWS_REGION = "ap-northeast-2"
    }

    @Bean
    fun amazonS3Client(): AmazonS3 {
        return AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(endpoint, AWS_REGION))
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
            .withPathStyleAccessEnabled(true)
            .build()
    }
}
