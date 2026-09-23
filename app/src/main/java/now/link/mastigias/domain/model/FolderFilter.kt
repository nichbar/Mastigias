package now.link.mastigias.domain.model

enum class FilterMode {
    INCLUDE,
    EXCLUDE
}

data class FolderFilter(
    val uri: String,
    val path: String,
    val mode: FilterMode = FilterMode.INCLUDE
) {
    val isInclude: Boolean get() = mode == FilterMode.INCLUDE
    val isExclude: Boolean get() = mode == FilterMode.EXCLUDE

    val displayName: String
        get() = when {
            path.isNotBlank() -> path
            uri.contains("primary", ignoreCase = true) -> "Internal Storage"
            uri.isNotBlank() -> {
                val clean = uri.substringBefore('?').substringBefore('#')
                clean.substringAfterLast('/').substringAfterLast(':').substringAfterLast("%3A").ifBlank { "All Files" }
            }
            else -> "All Files"
        }
}
