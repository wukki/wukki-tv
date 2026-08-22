package hu.wukki.tv

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.UUID

actual fun platformNormalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

actual fun stableChannelId(value: String): String =
    UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()
