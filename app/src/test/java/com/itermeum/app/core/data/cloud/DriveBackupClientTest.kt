package com.itermeum.app.core.data.cloud

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Section 5.17 Drive AppData client: prefix filter, POST vs PATCH, token refresh on 401. */
class DriveBackupClientTest {

    private class FakeTokens : TokenProvider {
        var issued = 0
        val invalidated = mutableListOf<String>()
        override suspend fun accessToken(): String = "t${++issued}"
        override suspend fun invalidate(token: String) { invalidated += token }
    }

    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private lateinit var client: DriveBackupClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokens()
        client = DriveBackupClient(OkHttpClient(), tokens, server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `foreign files are ignored and a new backup is created with POST`() = runBlocking {
        server.enqueue(json("""{"files":[{"id":"x","name":"primemover-snapshot.json","modifiedTime":"2026-01-01T00:00:00Z"}]}"""))
        server.enqueue(json("""{"id":"new","modifiedTime":"2026-10-26T10:00:00Z"}"""))
        val meta = client.uploadOrReplace(byteArrayOf(1, 2, 3))
        assertEquals("new", meta.fileId)
        server.takeRequest()
        val upload = server.takeRequest()
        assertEquals("POST", upload.method)
        assertTrue(upload.body.readUtf8().contains("appDataFolder"))
    }

    @Test
    fun `an existing backup is replaced with PATCH`() = runBlocking {
        server.enqueue(json("""{"files":[{"id":"ours","name":"itermeum-backup-v1.json.enc","modifiedTime":"2026-01-01T00:00:00Z"}]}"""))
        server.enqueue(json("""{"id":"ours","modifiedTime":"2026-10-26T10:00:00Z"}"""))
        client.uploadOrReplace(byteArrayOf(1))
        server.takeRequest()
        val upload = server.takeRequest()
        assertEquals("PATCH", upload.method)
        assertTrue(upload.path!!.contains("/files/ours"))
    }

    @Test
    fun `a 401 clears the cached token and retries once`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"files":[]}"""))
        assertNull(client.fetchRemote())
        assertEquals(listOf("t1"), tokens.invalidated)
        assertEquals("Bearer t1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer t2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a second 401 asks for a new sign-in`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            client.fetchRemote()
            fail("expected AuthRequiredException")
        } catch (e: AuthRequiredException) {
            assertEquals(1, tokens.invalidated.size)
        }
    }
}
