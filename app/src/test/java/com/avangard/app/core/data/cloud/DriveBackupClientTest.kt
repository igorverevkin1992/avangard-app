package com.avangard.app.core.data.cloud

import com.avangard.app.core.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Drive REST contracts covered by a stubbed MockWebServer. Each scenario
 * asserts both the outgoing request shape (URL, method, Authorization) and
 * the parsed result.
 */
class DriveBackupClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DriveBackupClient
    private lateinit var auth: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        auth = mockk(relaxed = true)
        coEvery { auth.accessToken() } returns "TEST_TOKEN"
        client = DriveBackupClient(
            httpClient = OkHttpClient(),
            auth = auth,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchRemote returns null when AppData folder is empty`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))

        val result = client.fetchRemote()

        assertNull(result)
        val listRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", listRequest.method)
        assertEquals("Bearer TEST_TOKEN", listRequest.getHeader("Authorization"))
        assertTrue(
            "list URL must scope to appDataFolder",
            listRequest.path!!.contains("spaces=appDataFolder"),
        )
    }

    @Test
    fun `fetchRemote downloads existing file and parses modifiedTime`() = runBlocking {
        // Anchor on a hand-verified UTC instant so the parse is exact.
        // 2024-01-01T00:00:00Z = 1704067200000 ms.
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"FILE123","modifiedTime":"2024-01-01T00:00:00.000Z"}]}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"schemaVersion":2,"quotes":[]}"""))

        val remote = client.fetchRemote()

        assertNotNull(remote)
        assertEquals("FILE123", remote!!.fileId)
        assertEquals(1704067200000L, remote.modifiedTimeMs)
        assertEquals(
            """{"schemaVersion":2,"quotes":[]}""",
            String(remote.bytes, Charsets.UTF_8),
        )

        server.takeRequest() // list
        val download = server.takeRequest()
        assertEquals("GET", download.method)
        assertTrue(download.path!!.startsWith("/drive/v3/files/FILE123"))
        assertTrue(download.path!!.contains("alt=media"))
    }

    @Test
    fun `uploadOrReplace POSTs multipart when no file exists`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"id":"NEW_FILE","modifiedTime":"2024-01-02T00:00:00.000Z"}"""
            )
        )

        val payload = "{\"foo\":\"bar\"}".toByteArray()
        val meta = client.uploadOrReplace(payload)

        assertEquals("NEW_FILE", meta.fileId)
        // 2024-01-02T00:00:00Z = 1704067200000 + 86400000 = 1704153600000
        assertEquals(1704153600000L, meta.modifiedTimeMs)
        server.takeRequest() // list
        val upload = server.takeRequest()
        assertEquals("POST", upload.method)
        assertTrue(upload.path!!.contains("uploadType=multipart"))
        // Body contains the AppData parent declaration on create.
        val body = upload.body.readUtf8()
        assertTrue(body.contains("appDataFolder"))
        assertTrue(body.contains("avangard-backup.json"))
        assertTrue(body.contains("\"foo\":\"bar\""))
    }

    @Test
    fun `uploadOrReplace PATCHes multipart when file already exists`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"EXISTING","modifiedTime":"2024-01-01T00:00:00.000Z"}]}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"id":"EXISTING","modifiedTime":"2024-01-03T00:00:00.000Z"}"""
            )
        )

        val meta = client.uploadOrReplace("payload".toByteArray())

        assertEquals("EXISTING", meta.fileId)
        server.takeRequest() // list
        val upload = server.takeRequest()
        assertEquals("PATCH", upload.method)
        assertTrue(upload.path!!.startsWith("/upload/drive/v3/files/EXISTING"))
    }

    @Test
    fun `fetchRemote throws IOException on 5xx`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            client.fetchRemote()
            fail("expected IOException")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

}
