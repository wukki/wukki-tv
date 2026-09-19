package hu.wukki.tv

@Suppress("UnsafeCastFromDynamic")
internal actual fun coreNormalize(value: String): String {
    val normalized: String = value.lowercase().asDynamic().normalize("NFD")
    return normalized
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
