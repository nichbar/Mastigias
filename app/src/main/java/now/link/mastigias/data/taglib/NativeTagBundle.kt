package now.link.mastigias.data.taglib

/**
 * Flat transfer DTO returned across the JNI boundary.
 * Primitive arrays avoid deep Kotlin/Java object graph allocation inside native C++.
 */
class NativeTagBundle(
    val keys: Array<String>,
    val values: Array<String>,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMs: Long
) {
    /**
     * Secondary constructor supporting 32-bit durationMs for backward compatibility.
     */
    constructor(
        keys: Array<String>,
        values: Array<String>,
        bitrateKbps: Int,
        sampleRateHz: Int,
        channels: Int,
        durationMs: Int
    ) : this(
        keys = keys,
        values = values,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        channels = channels,
        durationMs = durationMs.toLong()
    )
}
