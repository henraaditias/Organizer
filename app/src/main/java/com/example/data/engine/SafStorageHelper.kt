package com.example.data.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

data class HashResult(
    val sha256Hex: String,
    val byteCount: Long
)

object SafStorageHelper {

    private const val BUFFER_SIZE = 64 * 1024

    fun isFileUri(uri: Uri): Boolean = uri.scheme.equals("file", ignoreCase = true)

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            val readOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readOk && writeOk
        }
    }

    fun openTreeOrFileDocument(context: Context, uri: Uri): DocumentFile? {
        if (isFileUri(uri)) {
            val path = uri.path ?: return null
            val dir = File(path)
            return if (dir.exists() && dir.isDirectory) DocumentFile.fromFile(dir) else null
        }
        return runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
    }

    fun openSingleOrFileDocument(context: Context, uri: Uri): DocumentFile? {
        if (isFileUri(uri)) {
            val path = uri.path ?: return null
            return DocumentFile.fromFile(File(path))
        }
        return runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
    }

    fun createTargetFile(parentDir: DocumentFile, mimeType: String, targetName: String): DocumentFile? {
        if (isFileUri(parentDir.uri)) {
            val parentPath = parentDir.uri.path ?: return null
            val targetFile = File(parentPath, targetName)
            if (targetFile.exists()) return null
            return if (runCatching { targetFile.createNewFile() }.getOrDefault(false)) {
                DocumentFile.fromFile(targetFile)
            } else {
                null
            }
        }
        return runCatching { parentDir.createFile(mimeType, targetName) }.getOrNull()
    }

    fun hasPersistedReadWritePermission(context: Context, treeUriString: String): Boolean {
        val targetUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return false
        if (isFileUri(targetUri)) {
            val path = targetUri.path ?: return false
            val file = File(path)
            val extRoot = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
            val requiresAllFiles = extRoot != null && path.startsWith(extRoot)
            val permOk = if (requiresAllFiles) hasAllFilesAccess(context) else true
            return permOk && file.exists() && file.isDirectory && file.canRead() && file.canWrite()
        }
        val hasGrant = context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == targetUri && perm.isReadPermission && perm.isWritePermission
        }
        if (!hasGrant) return false
        return runCatching {
            val rootDoc = DocumentFile.fromTreeUri(context, targetUri) ?: return@runCatching false
            rootDoc.exists() && rootDoc.isDirectory && rootDoc.canRead() && rootDoc.canWrite()
        }.getOrDefault(false)
    }

    fun releasePersistedPermission(context: Context, treeUriString: String) {
        runCatching {
            val uri = Uri.parse(treeUriString)
            if (isFileUri(uri)) return
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        }
    }

    fun getDocumentIdSafe(uri: Uri): String? {
        if (isFileUri(uri)) return uri.path
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    }

    fun resolveLocalPath(uri: Uri): String? {
        if (isFileUri(uri)) return uri.path
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = getDocumentIdSafe(uri) ?: return null
        val split = docId.split(":", limit = 2)
        if (split.size != 2) return null
        val volume = split[0]
        val rel = split[1]
        return when {
            volume.equals("primary", ignoreCase = true) -> {
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (rel.isEmpty()) base else "$base/$rel"
            }
            volume.equals("home", ignoreCase = true) -> {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                if (rel.isEmpty()) base else "$base/$rel"
            }
            else -> {
                val candidate = File("/storage/$volume", rel)
                if (candidate.exists()) candidate.absolutePath else null
            }
        }
    }

    fun isSymbolicLink(context: Context, doc: DocumentFile): Boolean {
        val localPath = resolveLocalPath(doc.uri) ?: return false
        return isLocalPathSymbolicLink(localPath)
    }

    fun isLocalPathSymbolicLink(localPath: String): Boolean {
        // 1. POSIX lstat does not follow the final symlink component
        val lstatIsLink = runCatching {
            val stat = Os.lstat(localPath)
            OsConstants.S_ISLNK(stat.st_mode)
        }.getOrDefault(false)
        if (lstatIsLink) return true

        // 2. NIO Files.isSymbolicLink on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nioIsLink = runCatching {
                Files.isSymbolicLink(Paths.get(localPath))
            }.getOrDefault(false)
            if (nioIsLink) return true
        } else if (Build.VERSION.SDK_INT == 0) {
            // Host JVM unit-test environment (non-Android, Build.VERSION.SDK_INT == 0)
            val jvmIsLink = runCatching {
                val target = File(localPath)
                val canonParent = target.parentFile?.canonicalFile ?: return@runCatching false
                val fileInCanonParent = File(canonParent, target.name)
                fileInCanonParent.canonicalPath != fileInCanonParent.absolutePath
            }.getOrDefault(false)
            if (jvmIsLink) return true
        }

        return false
    }

    fun isVirtualDocument(context: Context, uri: Uri): Boolean {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val flags = cursor.getInt(0)
                    (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0
                } else {
                    false
                }
            } ?: false
        }.getOrDefault(false)
    }

    fun getAvailableBytes(context: Context, treeUri: Uri): Long? {
        val localPath = resolveLocalPath(treeUri)
        if (localPath != null) {
            val statBytes = runCatching {
                StatFs(localPath).availableBytes
            }.getOrNull()
            if (statBytes != null) return statBytes
        }

        // Fallback via ParcelFileDescriptor fstatvfs if provider supports it
        val fdBytes = runCatching {
            context.contentResolver.openFileDescriptor(treeUri, "r")?.use { pfd ->
                val vfs = Os.fstatvfs(pfd.fileDescriptor)
                vfs.f_bavail * vfs.f_frsize
            }
        }.getOrNull()
        if (fdBytes != null) return fdBytes

        // Final fallback to primary external storage StatFs
        return runCatching {
            StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
        }.getOrNull()
    }

    private fun openInput(context: Context, uri: Uri): InputStream? {
        if (isFileUri(uri)) {
            val path = uri.path ?: return null
            return runCatching { FileInputStream(File(path)) }.getOrNull()
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun openOutput(context: Context, uri: Uri): OutputStream? {
        if (isFileUri(uri)) {
            val path = uri.path ?: return null
            return FileOutputStream(File(path), false)
        }
        return runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
    }

    fun computeSha256(context: Context, uri: Uri): HashResult? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0L
            val input = openInput(context, uri) ?: return null
            input.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        digest.update(buffer, 0, read)
                        totalBytes += read
                    }
                }
            }
            HashResult(
                sha256Hex = digest.digest().toHexString(),
                byteCount = totalBytes
            )
        }.getOrNull()
    }

    fun copyWithSha256(context: Context, sourceUri: Uri, destUri: Uri): HashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L

        val input = openInput(context, sourceUri)
            ?: throw IllegalStateException("Unable to open source stream for reading.")
        val output = openOutput(context, destUri)
            ?: throw IllegalStateException("Unable to open destination stream for writing.")

        input.use { inStream ->
            output.use { outStream ->
                while (true) {
                    val read = inStream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        outStream.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalBytes += read
                    }
                }
                outStream.flush()
                if (outStream is FileOutputStream) {
                    runCatching { outStream.fd.sync() }
                }
            }
        }

        return HashResult(
            sha256Hex = digest.digest().toHexString(),
            byteCount = totalBytes
        )
    }

    suspend fun copyWithSha256WithProgress(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        onProgress: suspend (bytesCopied: Long) -> Unit
    ): HashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        var lastEmitMs = 0L

        val input = openInput(context, sourceUri)
            ?: throw IllegalStateException("Unable to open source stream for reading.")
        val output = openOutput(context, destUri)
            ?: throw IllegalStateException("Unable to open destination stream for writing.")

        input.use { inStream ->
            output.use { outStream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = inStream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        outStream.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 220L) {
                            lastEmitMs = now
                            onProgress(totalBytes)
                        }
                    }
                }
                outStream.flush()
                if (outStream is FileOutputStream) {
                    runCatching { outStream.fd.sync() }
                }
            }
        }
        onProgress(totalBytes)

        return HashResult(
            sha256Hex = digest.digest().toHexString(),
            byteCount = totalBytes
        )
    }

    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v ushr 4, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }
}
