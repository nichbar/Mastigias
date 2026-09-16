package now.link.mastigias.domain.model

enum class TagCategory(val title: String) {
    BASIC("Basic Tags"),
    LYRICS("Lyrics"),
    SORTING("Sort Order"),
    MUSICAL("Musical Properties"),
    PRODUCTION("Credits & Production"),
    REPLAYGAIN("ReplayGain"),
    IDENTIFIER("Identifiers & Fingerprints"),
    PUBLISHING("Publishing & Legal"),
    URL("Web Links"),
    CUSTOM("Custom Fields")
}
