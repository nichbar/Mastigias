package now.link.mastigias.domain.model

enum class TagField(
    val key: String,
    val displayName: String,
    val category: TagCategory,
    val id3v2Frame: String,
    val vorbisKey: String,
    val mp4Atom: String,
    val asfAttribute: String
) {
    // --- Basic Metadata (0..11) ---
    TITLE("title", "Title", TagCategory.BASIC, "TIT2", "TITLE", "\u00a9nam", "Title"),
    ARTIST("artist", "Artist", TagCategory.BASIC, "TPE1", "ARTIST", "\u00a9ART", "Author"),
    ALBUM("album", "Album", TagCategory.BASIC, "TALB", "ALBUM", "\u00a9alb", "WM/AlbumTitle"),
    YEAR("year", "Year", TagCategory.BASIC, "TDRC", "DATE", "\u00a9day", "WM/Year"),
    TRACK_NUMBER("track_number", "Track Number", TagCategory.BASIC, "TRCK", "TRACKNUMBER", "trkn", "WM/TrackNumber"),
    TRACK_TOTAL("track_total", "Total Tracks", TagCategory.BASIC, "TRCK", "TRACKTOTAL", "trkn", "WM/TrackTotal"),
    GENRE("genre", "Genre", TagCategory.BASIC, "TCON", "GENRE", "\u00a9gen", "WM/Genre"),
    ALBUM_ARTIST("album_artist", "Album Artist", TagCategory.BASIC, "TPE2", "ALBUMARTIST", "aART", "WM/AlbumArtist"),
    COMPOSER("composer", "Composer", TagCategory.BASIC, "TCOM", "COMPOSER", "\u00a9wrt", "WM/Composer"),
    DISC_NUMBER("disc_number", "Disc Number", TagCategory.BASIC, "TPOS", "DISCNUMBER", "disk", "WM/PartOfSet"),
    DISC_TOTAL("disc_total", "Total Discs", TagCategory.BASIC, "TPOS", "DISCTOTAL", "disk", "WM/TotalDiscs"),
    COMMENT("comment", "Comment", TagCategory.BASIC, "COMM", "COMMENT", "\u00a9cmt", "Description"),

    // --- Lyrics ---
    LYRICS("lyrics", "Lyrics", TagCategory.LYRICS, "USLT", "LYRICS", "\u00a9lyr", "WM/Lyrics"),

    // --- Sorting ---
    TITLE_SORT("title_sort", "Title Sort Order", TagCategory.SORTING, "TSOT", "TITLESORT", "sonm", "WM/TitleSortOrder"),
    ARTIST_SORT("artist_sort", "Artist Sort Order", TagCategory.SORTING, "TSOP", "ARTISTSORT", "soar", "WM/ArtistSortOrder"),
    ALBUM_SORT("album_sort", "Album Sort Order", TagCategory.SORTING, "TSOA", "ALBUMSORT", "soal", "WM/AlbumSortOrder"),
    ALBUM_ARTIST_SORT("album_artist_sort", "Album Artist Sort Order", TagCategory.SORTING, "TSO2", "ALBUMARTISTSORT", "soaa", "WM/AlbumArtistSortOrder"),
    COMPOSER_SORT("composer_sort", "Composer Sort Order", TagCategory.SORTING, "TSOC", "COMPOSERSORT", "soco", "WM/ComposerSortOrder"),

    // --- Musical & Production ---
    BPM("bpm", "Beats Per Minute", TagCategory.MUSICAL, "TBPM", "BPM", "tmpo", "WM/BeatsPerMinute"),
    INITIAL_KEY("initial_key", "Initial Key", TagCategory.MUSICAL, "TKEY", "KEY", "----:com.apple.iTunes:initialkey", "WM/InitialKey"),
    MOOD("mood", "Mood", TagCategory.MUSICAL, "TMOO", "MOOD", "----:com.apple.iTunes:MOOD", "WM/Mood"),
    GROUPING("grouping", "Grouping", TagCategory.MUSICAL, "TIT1", "GROUPING", "\u00a9grp", "WM/ContentGroupDescription"),
    SUBTITLE("subtitle", "Subtitle", TagCategory.MUSICAL, "TIT3", "SUBTITLE", "----:com.apple.iTunes:SUBTITLE", "WM/SubTitle"),
    CONDUCTOR("conductor", "Conductor", TagCategory.PRODUCTION, "TPE3", "CONDUCTOR", "----:com.apple.iTunes:CONDUCTOR", "WM/Conductor"),
    REMIXER("remixer", "Modified By / Remixer", TagCategory.PRODUCTION, "TPE4", "REMIXER", "----:com.apple.iTunes:REMIXER", "WM/ModifiedBy"),
    ARRANGER("arranger", "Arranger", TagCategory.PRODUCTION, "TIPL:arranger", "ARRANGER", "----:com.apple.iTunes:ARRANGER", "WM/Arranger"),
    LYRICIST("lyricist", "Lyricist", TagCategory.PRODUCTION, "TEXT", "LYRICIST", "----:com.apple.iTunes:LYRICIST", "WM/Writer"),
    PRODUCER("producer", "Producer", TagCategory.PRODUCTION, "TIPL:producer", "PRODUCER", "----:com.apple.iTunes:PRODUCER", "WM/Producer"),

    // --- ReplayGain ---
    REPLAYGAIN_TRACK_GAIN("replaygain_track_gain", "Track Gain", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_GAIN", "----:com.apple.iTunes:replaygain_track_gain", "REPLAYGAIN_TRACK_GAIN"),
    REPLAYGAIN_TRACK_PEAK("replaygain_track_peak", "Track Peak", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_TRACK_PEAK", "----:com.apple.iTunes:replaygain_track_peak", "REPLAYGAIN_TRACK_PEAK"),
    REPLAYGAIN_ALBUM_GAIN("replaygain_album_gain", "Album Gain", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_GAIN", "----:com.apple.iTunes:replaygain_album_gain", "REPLAYGAIN_ALBUM_GAIN"),
    REPLAYGAIN_ALBUM_PEAK("replaygain_album_peak", "Album Peak", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_ALBUM_PEAK", "REPLAYGAIN_ALBUM_PEAK", "----:com.apple.iTunes:replaygain_album_peak", "REPLAYGAIN_ALBUM_PEAK"),

    // --- AcoustID & MusicBrainz IDs ---
    ACOUSTID_FINGERPRINT("acoustid_fingerprint", "AcoustID Fingerprint", TagCategory.IDENTIFIER, "TXXX:AcoustID Fingerprint", "ACOUSTID_FINGERPRINT", "----:com.apple.iTunes:AcoustID Fingerprint", "AcoustID/Fingerprint"),
    ACOUSTID_ID("acoustid_id", "AcoustID ID", TagCategory.IDENTIFIER, "TXXX:AcoustID Id", "ACOUSTID_ID", "----:com.apple.iTunes:AcoustID Id", "AcoustID/Id"),
    MUSICBRAINZ_TRACK_ID("musicbrainz_track_id", "MusicBrainz Track ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Track Id", "MUSICBRAINZ_TRACKID", "----:com.apple.iTunes:MusicBrainz Track Id", "MusicBrainz/Track Id"),
    MUSICBRAINZ_ARTIST_ID("musicbrainz_artist_id", "MusicBrainz Artist ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Artist Id", "MUSICBRAINZ_ARTISTID", "----:com.apple.iTunes:MusicBrainz Artist Id", "MusicBrainz/Artist Id"),
    MUSICBRAINZ_RELEASE_ID("musicbrainz_release_id", "MusicBrainz Release ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Album Id", "MUSICBRAINZ_ALBUMID", "----:com.apple.iTunes:MusicBrainz Album Id", "MusicBrainz/Album Id"),
    MUSICBRAINZ_RELEASEARTIST_ID("musicbrainz_releaseartist_id", "MusicBrainz Release Artist ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Album Artist Id", "MUSICBRAINZ_ALBUMARTISTID", "----:com.apple.iTunes:MusicBrainz Album Artist Id", "MusicBrainz/Album Artist Id"),
    MUSICBRAINZ_DISC_ID("musicbrainz_disc_id", "MusicBrainz Disc ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Disc Id", "MUSICBRAINZ_DISCID", "----:com.apple.iTunes:MusicBrainz Disc Id", "MusicBrainz/Disc Id"),
    MUSICBRAINZ_WORK_ID("musicbrainz_work_id", "MusicBrainz Work ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Work Id", "MUSICBRAINZ_WORKID", "----:com.apple.iTunes:MusicBrainz Work Id", "MusicBrainz/Work Id"),
    MUSICBRAINZ_RECORDING_ID("musicbrainz_recording_id", "MusicBrainz Recording ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Recording Id", "MUSICBRAINZ_RECORDINGID", "----:com.apple.iTunes:MusicBrainz Recording Id", "MusicBrainz/Recording Id"),
    MUSICBRAINZ_RELEASEGROUP_ID("musicbrainz_releasegroup_id", "MusicBrainz Release Group ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Release Group Id", "MUSICBRAINZ_RELEASEGROUPID", "----:com.apple.iTunes:MusicBrainz Release Group Id", "MusicBrainz/Release Group Id"),

    // --- Publishing & Industry Identifiers ---
    ISRC("isrc", "ISRC", TagCategory.PUBLISHING, "TSRC", "ISRC", "----:com.apple.iTunes:ISRC", "WM/ISRC"),
    BARCODE("barcode", "Barcode / UPC", TagCategory.PUBLISHING, "TXXX:BARCODE", "BARCODE", "----:com.apple.iTunes:BARCODE", "WM/Barcode"),
    CATALOG_NUMBER("catalog_number", "Catalog Number", TagCategory.PUBLISHING, "TXXX:CATALOGNUMBER", "CATALOGNUMBER", "----:com.apple.iTunes:CATALOGNUMBER", "WM/CatalogNo"),
    RECORD_LABEL("record_label", "Record Label / Organization", TagCategory.PUBLISHING, "TPUB", "ORGANIZATION", "----:com.apple.iTunes:LABEL", "WM/Publisher"),
    COPYRIGHT("copyright", "Copyright", TagCategory.PUBLISHING, "TCOP", "COPYRIGHT", "cprt", "Copyright"),
    ENCODER("encoder", "Encoded By", TagCategory.PUBLISHING, "TSSE", "ENCODER", "\u00a9too", "WM/EncodedBy"),
    MEDIA_TYPE("media_type", "Media Type", TagCategory.PUBLISHING, "TMED", "SOURCEMEDIA", "----:com.apple.iTunes:MEDIA", "WM/Media"),
    ORIGINAL_DATE("original_date", "Original Release Date", TagCategory.PUBLISHING, "TDOR", "ORIGINALDATE", "----:com.apple.iTunes:ORIGINALDATE", "WM/OriginalReleaseTime"),
    RATING("rating", "Rating / Popularity", TagCategory.PUBLISHING, "POPM", "RATING", "rtng", "WM/SharedUserRating"),

    // --- Web URLs ---
    URL_OFFICIAL_ARTIST("url_artist", "Official Artist URL", TagCategory.URL, "WOAR", "URL_OFFICIAL_ARTIST_SITE", "----:com.apple.iTunes:URL_OFFICIAL_ARTIST_SITE", "WM/AuthorURL"),
    URL_OFFICIAL_RELEASE("url_release", "Official Release URL", TagCategory.URL, "WOAS", "URL_OFFICIAL_RELEASE_SITE", "----:com.apple.iTunes:URL_OFFICIAL_RELEASE_SITE", "WM/PromotionURL"),
    URL_DISCOGS_RELEASE("url_discogs_release", "Discogs Release URL", TagCategory.URL, "WXXX:DISCOGS_RELEASE", "DISCOGS_RELEASE", "----:com.apple.iTunes:DISCOGS_RELEASE", "WM/DiscogsReleaseURL"),
    URL_DISCOGS_ARTIST("url_discogs_artist", "Discogs Artist URL", TagCategory.URL, "WXXX:DISCOGS_ARTIST", "DISCOGS_ARTIST", "----:com.apple.iTunes:DISCOGS_ARTIST", "WM/DiscogsArtistURL"),
    URL_WIKIPEDIA_ARTIST("url_wikipedia_artist", "Wikipedia Artist URL", TagCategory.URL, "WXXX:WIKIPEDIA_ARTIST", "WIKIPEDIA_ARTIST", "----:com.apple.iTunes:WIKIPEDIA_ARTIST", "WM/WikipediaArtistURL"),
    URL_WIKIPEDIA_RELEASE("url_wikipedia_release", "Wikipedia Release URL", TagCategory.URL, "WXXX:WIKIPEDIA_RELEASE", "WIKIPEDIA_RELEASE", "----:com.apple.iTunes:WIKIPEDIA_RELEASE", "WM/WikipediaReleaseURL"),

    // --- Custom User Fields ---
    CUSTOM_1("custom_1", "Custom Field 1", TagCategory.CUSTOM, "TXXX:CUSTOM1", "CUSTOM1", "----:com.apple.iTunes:CUSTOM1", "CUSTOM1"),
    CUSTOM_2("custom_2", "Custom Field 2", TagCategory.CUSTOM, "TXXX:CUSTOM2", "CUSTOM2", "----:com.apple.iTunes:CUSTOM2", "CUSTOM2"),
    CUSTOM_3("custom_3", "Custom Field 3", TagCategory.CUSTOM, "TXXX:CUSTOM3", "CUSTOM3", "----:com.apple.iTunes:CUSTOM3", "CUSTOM3"),
    CUSTOM_4("custom_4", "Custom Field 4", TagCategory.CUSTOM, "TXXX:CUSTOM4", "CUSTOM4", "----:com.apple.iTunes:CUSTOM4", "CUSTOM4"),
    CUSTOM_5("custom_5", "Custom Field 5", TagCategory.CUSTOM, "TXXX:CUSTOM5", "CUSTOM5", "----:com.apple.iTunes:CUSTOM5", "CUSTOM5");

    companion object {
        private val keyLookup = entries.associateBy { it.key }
        fun fromKey(key: String): TagField? = keyLookup[key]

        val basicFields: List<TagField> = entries.filter { it.category == TagCategory.BASIC }
        val advancedFields: List<TagField> = entries.filter { it.category != TagCategory.BASIC }
    }
}
