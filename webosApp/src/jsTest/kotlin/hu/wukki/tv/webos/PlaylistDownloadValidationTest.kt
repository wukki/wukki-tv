package hu.wukki.tv.webos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaylistDownloadValidationTest {
    @Test
    fun `accepts successful response within the playlist limit`() {
        validatePlaylistResponse(status = 200, statusText = "OK", declaredSize = 12)
        assertEquals("#EXTM3U", validatePlaylistBody("#EXTM3U"))
    }

    @Test
    fun `rejects HTTP errors and oversized playlist metadata`() {
        assertFailsWith<IllegalStateException> {
            validatePlaylistResponse(status = 404, statusText = "Not Found", declaredSize = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validatePlaylistResponse(status = 200, statusText = "OK", declaredSize = 2 * 1024 * 1024 + 1)
        }
    }

    @Test
    fun `uses UTF-8 byte size for the downloaded body limit`() {
        val oversizedUnicode = "ő".repeat(1024 * 1024 + 1)

        assertFailsWith<IllegalArgumentException> { validatePlaylistBody(oversizedUnicode) }
    }
}
