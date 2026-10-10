package win.android.fileexplorar.data

import java.io.File

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, SHORTCUT, UNKNOWN
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val type: FileType = detectType(file)
) {
    val path: String get() = file.absolutePath

    companion object {
        fun detectType(file: File): FileType {
            if (file.isDirectory) return FileType.FOLDER
            if (file.extension.lowercase() == "wlnk") return FileType.SHORTCUT
            return when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> FileType.IMAGE
                "mp4", "mkv", "avi", "webm", "3gp" -> FileType.VIDEO
                "mp3", "wav", "ogg", "m4a", "flac" -> FileType.AUDIO
                "pdf", "doc", "docx", "txt", "md", "rtf", "xls", "xlsx", "ppt", "pptx" -> FileType.DOCUMENT
                "zip", "rar", "7z", "tar", "gz" -> FileType.ARCHIVE
                "apk" -> FileType.APK
                else -> FileType.UNKNOWN
            }
        }
    }
}

object ShortcutHelper {
    fun create(shortcutFile: File, targetPath: String): Boolean {
        return try {
            shortcutFile.writeText("WINEXPLORER_SHORTCUT\n$targetPath\n")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resolve(shortcutFile: File): String? {
        return try {
            val lines = shortcutFile.readLines()
            if (lines.isNotEmpty() && lines[0] == "WINEXPLORER_SHORTCUT" && lines.size >= 2) {
                lines[1].trim()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun isShortcut(file: File): Boolean =
        file.isFile && file.extension.lowercase() == "wlnk"
}
