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
}
