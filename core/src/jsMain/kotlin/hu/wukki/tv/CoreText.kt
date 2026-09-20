@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package hu.wukki.tv

@Suppress("UnsafeCastFromDynamic")
internal actual fun coreNormalize(value: String): String {
    val normalized: String = value.lowercase().asDynamic().normalize("NFD")
    return normalized
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

actual fun stableChannelId(value: String): String {
    val digest = md5(value.encodeToByteArray())
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x30).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = digest.joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}

private fun md5(input: ByteArray): ByteArray {
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val data = ByteArray(paddedSize)
    input.copyInto(data)
    data[input.size] = 0x80.toByte()
    val bitLength = input.size.toLong() * 8L
    repeat(8) { index -> data[paddedSize - 8 + index] = (bitLength shr (index * 8)).toByte() }

    var first = 0x67452301u
    var second = 0xefcdab89u
    var third = 0x98badcfeu
    var fourth = 0x10325476u
    val words = UIntArray(16)

    for (blockStart in data.indices step 64) {
        repeat(16) { index -> words[index] = littleEndianWord(data, blockStart + index * 4) }
        var a = first
        var b = second
        var c = third
        var d = fourth

        repeat(64) { index ->
            val function: UInt
            val wordIndex: Int
            when (index) {
                in 0..15 -> {
                    function = (b and c) or (b.inv() and d)
                    wordIndex = index
                }

                in 16..31 -> {
                    function = (d and b) or (d.inv() and c)
                    wordIndex = (5 * index + 1) % 16
                }

                in 32..47 -> {
                    function = b xor c xor d
                    wordIndex = (3 * index + 5) % 16
                }

                else -> {
                    function = c xor (b or d.inv())
                    wordIndex = (7 * index) % 16
                }
            }
            val previousD = d
            d = c
            c = b
            b += rotateLeft(a + function + MD5_CONSTANTS[index] + words[wordIndex], MD5_SHIFTS[index])
            a = previousD
        }
        first += a
        second += b
        third += c
        fourth += d
    }

    val digest = ByteArray(16)
    listOf(first, second, third, fourth).forEachIndexed { wordIndex, word ->
        repeat(4) { byteIndex -> digest[wordIndex * 4 + byteIndex] = (word shr (byteIndex * 8)).toByte() }
    }
    return digest
}

private fun littleEndianWord(
    bytes: ByteArray,
    offset: Int,
): UInt =
    bytes[offset].toUByte().toUInt() or
        (bytes[offset + 1].toUByte().toUInt() shl 8) or
        (bytes[offset + 2].toUByte().toUInt() shl 16) or
        (bytes[offset + 3].toUByte().toUInt() shl 24)

private fun rotateLeft(
    value: UInt,
    bits: Int,
): UInt = (value shl bits) or (value shr (32 - bits))

private val MD5_SHIFTS =
    """
    7 12 17 22 7 12 17 22 7 12 17 22 7 12 17 22
    5 9 14 20 5 9 14 20 5 9 14 20 5 9 14 20
    4 11 16 23 4 11 16 23 4 11 16 23 4 11 16 23
    6 10 15 21 6 10 15 21 6 10 15 21 6 10 15 21
    """.trimIndent().split(Regex("\\s+")).map(String::toInt).toIntArray()

private val MD5_CONSTANTS =
    """
    d76aa478 e8c7b756 242070db c1bdceee f57c0faf 4787c62a a8304613 fd469501
    698098d8 8b44f7af ffff5bb1 895cd7be 6b901122 fd987193 a679438e 49b40821
    f61e2562 c040b340 265e5a51 e9b6c7aa d62f105d 02441453 d8a1e681 e7d3fbc8
    21e1cde6 c33707d6 f4d50d87 455a14ed a9e3e905 fcefa3f8 676f02d9 8d2a4c8a
    fffa3942 8771f681 6d9d6122 fde5380c a4beea44 4bdecfa9 f6bb4b60 bebfbc70
    289b7ec6 eaa127fa d4ef3085 04881d05 d9d4d039 e6db99e5 1fa27cf8 c4ac5665
    f4292244 432aff97 ab9423a7 fc93a039 655b59c3 8f0ccc92 ffeff47d 85845dd1
    6fa87e4f fe2ce6e0 a3014314 4e0811a1 f7537e82 bd3af235 2ad7d2bb eb86d391
    """.trimIndent().split(Regex("\\s+")).map { value -> value.toUInt(16) }.toUIntArray()
