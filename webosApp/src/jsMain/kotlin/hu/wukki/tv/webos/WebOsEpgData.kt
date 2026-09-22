package hu.wukki.tv.webos

import hu.wukki.tv.Programme
import hu.wukki.tv.XmlTvProgrammeParser
import kotlinx.browser.window
import org.w3c.fetch.Response
import kotlin.js.Date
import kotlin.js.JSON
import kotlin.js.Promise
import kotlin.js.jsTypeOf

internal const val WEBOS_EPG_STORAGE_KEY = "hu.wukki.tv.webos.epg.v1"
internal const val WEBOS_EPG_SCHEMA_VERSION = 1

internal class WebOsEpgParser(
    private val schedule: ((() -> Unit) -> Unit),
    private val batchSize: Int = 250,
) {
    private var generation = 0L

    fun parse(
        xml: String,
        onComplete: (List<Programme>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val token = ++generation
        val programmes = mutableListOf<Programme>()

        fun next(offset: Int) {
            if (token != generation) return
            try {
                val batch = XmlTvProgrammeParser.parseBatch(xml, offset, batchSize)
                programmes += batch.programmes
                if (batch.complete) {
                    onComplete(programmes.sortedBy(Programme::start))
                } else {
                    schedule { next(batch.nextOffset) }
                }
            } catch (error: Throwable) {
                if (token == generation) onFailure(error)
            }
        }
        schedule { next(0) }
    }

    fun cancel() {
        generation++
    }
}

internal data class WebOsEpgCache(
    val sourceUrl: String,
    val updatedAt: Long,
    val programmes: List<Programme>,
)

internal class WebOsEpgCacheStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val remove: () -> Unit,
) {
    fun load(): WebOsEpgCache? =
        runCatching {
            val envelope = JSON.parse<dynamic>(read() ?: return null)
            require(number(envelope.schemaVersion)?.toInt() == WEBOS_EPG_SCHEMA_VERSION)
            val sourceUrl = string(envelope.sourceUrl) ?: error("Missing EPG source URL")
            val updatedAt = number(envelope.updatedAt)?.toLong() ?: error("Missing EPG update time")
            val encoded = envelope.programmes
            require(jsTypeOf(encoded) == "object" && encoded != null && encoded.length != null)
            val programmes =
                (0 until (encoded.length as Number).toInt()).mapNotNull { index ->
                    val item = encoded[index]
                    val channelId = string(item.channelId) ?: return@mapNotNull null
                    val start = number(item.start)?.toLong() ?: return@mapNotNull null
                    val end = number(item.end)?.toLong() ?: return@mapNotNull null
                    if (end <= start) return@mapNotNull null
                    Programme(
                        channelId = channelId,
                        title = string(item.title).orEmpty(),
                        start = start,
                        end = end,
                        description = string(item.description),
                        imageUrl = string(item.imageUrl),
                    )
                }
            WebOsEpgCache(sourceUrl, updatedAt, programmes)
        }.getOrNull()

    fun save(cache: WebOsEpgCache): String? =
        runCatching {
            val envelope = js("({})")
            envelope.schemaVersion = WEBOS_EPG_SCHEMA_VERSION
            envelope.sourceUrl = cache.sourceUrl
            envelope.updatedAt = cache.updatedAt.toDouble()
            envelope.programmes =
                cache.programmes
                    .map { programme ->
                        val encoded = js("({})")
                        encoded.channelId = programme.channelId
                        encoded.title = programme.title
                        encoded.start = programme.start.toDouble()
                        encoded.end = programme.end.toDouble()
                        encoded.description = programme.description
                        encoded.imageUrl = programme.imageUrl
                        encoded
                    }.toTypedArray()
            write(JSON.stringify(envelope))
        }.exceptionOrNull()?.let { "Az EPG cache nem menthető: ${it.message ?: it}" }

    fun clear(): String? = runCatching(remove).exceptionOrNull()?.let { "Az EPG cache nem törölhető: ${it.message ?: it}" }
}

private fun string(value: dynamic): String? = if (jsTypeOf(value) == "string") (value as String).takeIf(String::isNotBlank) else null

private fun number(value: dynamic): Double? =
    if (jsTypeOf(value) == "number") {
        (value as Number).toDouble().takeIf(Double::isFinite)
    } else {
        null
    }

internal fun fetchBoundedText(
    url: String,
    maxBytes: Int,
    timeoutMillis: Int,
    label: String,
): Promise<String> = if (epgServiceAvailable()) fetchThroughEpgService(url, maxBytes, timeoutMillis, label) else fetchDirect(url, maxBytes, timeoutMillis, label)

private fun fetchDirect(
    url: String,
    maxBytes: Int,
    timeoutMillis: Int,
    label: String,
): Promise<String> =
    Promise { resolve, reject ->
        val controller = newAbortController()
        val options = js("({})")
        if (controller != null) options.signal = controller.signal
        val timeout =
            window.setTimeout(
                {
                    controller?.abort()
                    reject(Throwable("$label letöltése túllépte a ${timeoutMillis / 1_000} másodperces időkorlátot."))
                },
                timeoutMillis,
            )
        window
            .fetch(url, options)
            .then { response ->
                validateBoundedResponse(response, maxBytes, label)
                response.text()
            }.then { text ->
                val size = text.encodeToByteArray().size
                require(size <= maxBytes) { "$label túl nagy: $size bájt, maximum $maxBytes bájt lehet." }
                window.clearTimeout(timeout)
                resolve(text)
            }.catch { error ->
                window.clearTimeout(timeout)
                reject(error)
            }
    }

private fun fetchThroughEpgService(
    url: String,
    maxBytes: Int,
    timeoutMillis: Int,
    label: String,
): Promise<String> =
    Promise { resolve, reject ->
        val parameters = js("({})")
        parameters.url = url
        parameters.maxBytes = maxBytes
        parameters.timeoutMillis = timeoutMillis
        requestEpgService("fetchEpg", parameters)
            .then { started ->
                val token = started.token ?: throw IllegalStateException("Az EPG szolgáltatás nem adott munkamenet-azonosítót.")
                val expectedLength = started.length ?: 0
                val text = StringBuilder(expectedLength.coerceAtLeast(0))

                fun read(offset: Int) {
                    val request = js("({})")
                    request.token = token
                    request.offset = offset
                    request.length = EPG_SERVICE_CHUNK_CHARACTERS
                    requestEpgService("readChunk", request)
                        .then { response ->
                            text.append(response.chunk.orEmpty())
                            val nextOffset = response.nextOffset ?: offset
                            if (response.complete) {
                                releaseEpgServiceDocument(token)
                                val value = text.toString()
                                val size = value.encodeToByteArray().size
                                require(size <= maxBytes) { "$label túl nagy: $size bájt, maximum $maxBytes bájt lehet." }
                                resolve(value)
                            } else if (nextOffset <= offset) {
                                releaseEpgServiceDocument(token)
                                reject(Throwable("Az EPG szolgáltatás nem haladt a letöltésben."))
                            } else {
                                read(nextOffset)
                            }
                        }.catch { error ->
                            releaseEpgServiceDocument(token)
                            reject(error)
                        }
                }
                read(0)
            }.catch(reject)
    }

private fun requestEpgService(
    method: String,
    parameters: dynamic,
): Promise<WebOsEpgServiceResponse> =
    Promise { resolve, reject ->
        val options = js("({})")
        options.method = method
        options.parameters = parameters
        options.onSuccess = { response: dynamic ->
            if (response.returnValue == false) {
                reject(Throwable(response.errorText as? String ?: "Az EPG szolgáltatás hibát jelzett."))
            } else {
                resolve(
                    WebOsEpgServiceResponse(
                        token = response.token as? String,
                        length = (response.length as? Number)?.toInt(),
                        chunk = response.chunk as? String,
                        nextOffset = (response.nextOffset as? Number)?.toInt(),
                        complete = response.complete == true,
                    ),
                )
            }
        }
        options.onFailure = { error: dynamic ->
            reject(Throwable(error.errorText as? String ?: error.toString()))
        }
        window
            .asDynamic()
            .webOS.service
            .request(EPG_SERVICE_URI, options)
    }

private fun releaseEpgServiceDocument(token: String) {
    val parameters = js("({})")
    parameters.token = token
    val options = js("({})")
    options.method = "release"
    options.parameters = parameters
    options.onSuccess = { _: dynamic -> Unit }
    options.onFailure = { _: dynamic -> Unit }
    window
        .asDynamic()
        .webOS.service
        .request(EPG_SERVICE_URI, options)
}

private fun epgServiceAvailable(): Boolean =
    jsTypeOf(window.asDynamic().PalmServiceBridge) == "function" && window
        .asDynamic()
        .webOS
        ?.service
        ?.request != null

private fun validateBoundedResponse(
    response: Response,
    maxBytes: Int,
    label: String,
) {
    if (response.status.toInt() !in 200..299) throw IllegalStateException("HTTP ${response.status.toInt()} ${response.statusText}".trim())
    val declaredSize = response.headers.get("Content-Length")?.toIntOrNull()
    require(declaredSize == null || declaredSize <= maxBytes) {
        "$label túl nagy: $declaredSize bájt, maximum $maxBytes bájt lehet."
    }
}

internal fun newAbortController(): dynamic = js("typeof AbortController === 'undefined' ? null : new AbortController()")

internal fun formatEpgTime(timestamp: Long): String {
    val date = Date(timestamp.toDouble())
    return "${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}"
}

private const val EPG_SERVICE_URI = "luna://hu.wukki.tv.webos.epg/"
private const val EPG_SERVICE_CHUNK_CHARACTERS = 128 * 1024

private data class WebOsEpgServiceResponse(
    val token: String?,
    val length: Int?,
    val chunk: String?,
    val nextOffset: Int?,
    val complete: Boolean,
)
