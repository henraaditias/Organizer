package com.example.domain

enum class FileCategory(
    val folderName: String,
    val extensions: Set<String>
) {
    IMAGES(
        folderName = "Images",
        extensions = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif",
            "bmp", "svg", "raw", "dng", "tiff", "tif", "avif", "ico"
        )
    ),
    VIDEOS(
        folderName = "Videos",
        extensions = setOf(
            "mp4", "mkv", "mov", "avi", "webm", "m4v",
            "flv", "wmv", "3gp", "ts", "mts", "mpg", "mpeg"
        )
    ),
    DOCUMENTS(
        folderName = "Documents",
        extensions = setOf(
            "pdf", "doc", "docx", "txt", "md", "rtf", "odt",
            "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp",
            "epub", "json", "xml", "yaml", "yml", "log", "tex", "html", "htm"
        )
    ),
    AUDIO(
        folderName = "Audio",
        extensions = setOf(
            "mp3", "wav", "flac", "aac", "ogg", "m4a",
            "opus", "wma", "aiff", "mid", "midi", "amr"
        )
    ),
    ARCHIVES(
        folderName = "Archives",
        extensions = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2",
            "xz", "tgz", "tbz2", "zst", "iso", "lz4"
        )
    ),
    APPLICATIONS(
        folderName = "Applications",
        extensions = setOf(
            "apk", "apks", "xapk", "aab", "exe",
            "msi", "dmg", "deb", "rpm", "appimage"
        )
    ),
    OTHERS(
        folderName = "Others",
        extensions = emptySet()
    );

    companion object {
        const val ORGANIZED_ROOT_NAME = "Organized"

        fun fromFolderName(name: String): FileCategory =
            entries.firstOrNull { it.folderName.equals(name, ignoreCase = true) } ?: OTHERS
    }
}

object FileClassifier {
    private val multiPartArchiveSuffixes = listOf(
        ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst"
    )

    fun extractExtension(fileName: String): String {
        val lower = fileName.trim().lowercase()
        for (multi in multiPartArchiveSuffixes) {
            if (lower.endsWith(multi) && lower.length > multi.length) {
                return multi.removePrefix(".")
            }
        }
        val dotIndex = lower.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == lower.lastIndex) return ""
        return lower.substring(dotIndex + 1)
    }

    fun classify(fileName: String): FileCategory {
        val ext = extractExtension(fileName)
        if (ext.isEmpty()) return FileCategory.OTHERS
        if (ext.startsWith("tar.")) return FileCategory.ARCHIVES
        return FileCategory.entries.firstOrNull { category ->
            category != FileCategory.OTHERS && ext in category.extensions
        } ?: FileCategory.OTHERS
    }
}

object NameCollisionResolver {
    private val multiPartExtensions = listOf(
        ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst"
    )

    fun splitBaseAndExtension(fileName: String): Pair<String, String> {
        val lower = fileName.lowercase()
        for (multi in multiPartExtensions) {
            if (lower.endsWith(multi) && fileName.length > multi.length) {
                val splitAt = fileName.length - multi.length
                return fileName.substring(0, splitAt) to fileName.substring(splitAt)
            }
        }
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < fileName.length - 1) {
            fileName.substring(0, lastDot) to fileName.substring(lastDot)
        } else {
            fileName to ""
        }
    }

    fun buildSuffixedName(originalName: String, suffixIndex: Int): String {
        if (suffixIndex <= 0) return originalName
        val (base, ext) = splitBaseAndExtension(originalName)
        return "$base ($suffixIndex)$ext"
    }
}
