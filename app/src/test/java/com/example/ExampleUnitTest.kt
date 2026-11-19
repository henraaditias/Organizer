package com.example

import com.example.data.engine.SafStorageHelper
import com.example.domain.FileCategory
import com.example.domain.FileClassifier
import com.example.domain.NameCollisionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExampleUnitTest {

    @Test
    fun `classify covers all 7 categories accurately`() {
        assertEquals(FileCategory.IMAGES, FileClassifier.classify("sunset.JPG"))
        assertEquals(FileCategory.IMAGES, FileClassifier.classify("photo.heic"))
        assertEquals(FileCategory.VIDEOS, FileClassifier.classify("clip.mkv"))
        assertEquals(FileCategory.DOCUMENTS, FileClassifier.classify("invoice.pdf"))
        assertEquals(FileCategory.AUDIO, FileClassifier.classify("track.flac"))
        assertEquals(FileCategory.ARCHIVES, FileClassifier.classify("backup.tar.gz"))
        assertEquals(FileCategory.ARCHIVES, FileClassifier.classify("bundle.7z"))
        assertEquals(FileCategory.APPLICATIONS, FileClassifier.classify("installer.apk"))
        assertEquals(FileCategory.OTHERS, FileClassifier.classify("unknown.xyz"))
        assertEquals(FileCategory.OTHERS, FileClassifier.classify("README"))
    }

    @Test
    fun `collision resolver preserves single and multipart extensions`() {
        assertEquals("report (1).pdf", NameCollisionResolver.buildSuffixedName("report.pdf", 1))
        assertEquals("archive (2).tar.gz", NameCollisionResolver.buildSuffixedName("archive.tar.gz", 2))
        assertEquals("README (1)", NameCollisionResolver.buildSuffixedName("README", 1))
        assertEquals(".hidden (1)", NameCollisionResolver.buildSuffixedName(".hidden", 1))
    }

    @Test
    fun `symlink detection identifies symbolic links without following`() {
        val tempDir = Files.createTempDirectory("organizer_symlink_test")
        try {
            val realFile = Files.createTempFile(tempDir, "real", ".txt")
            val linkPath = tempDir.resolve("linked.txt")
            Files.createSymbolicLink(linkPath, realFile)

            assertFalse(SafStorageHelper.isLocalPathSymbolicLink(realFile.toAbsolutePath().toString()))
            assertTrue(SafStorageHelper.isLocalPathSymbolicLink(linkPath.toAbsolutePath().toString()))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
