package com.goldpet.domain.common.util

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream

/**
 * Byte array → MultipartFile 경량 어댑터. Spring test 의 `MockMultipartFile` 은 main
 * classpath 에 없으므로 production 경로(emoticon seed, AI profile, etc.)에서
 * FileService.storeFile 재사용을 위해 자체 구현.
 */
class InMemoryMultipartFile(
    private val bytes: ByteArray,
    private val originalFilename: String,
    private val mimeType: String,
) : MultipartFile {
    override fun getName(): String = "file"
    override fun getOriginalFilename(): String = originalFilename
    override fun getContentType(): String = mimeType
    override fun isEmpty(): Boolean = bytes.isEmpty()
    override fun getSize(): Long = bytes.size.toLong()
    override fun getBytes(): ByteArray = bytes
    override fun getInputStream(): InputStream = bytes.inputStream()
    override fun transferTo(dest: File) { dest.writeBytes(bytes) }
    override fun transferTo(dest: java.nio.file.Path) { java.nio.file.Files.write(dest, bytes) }
}
