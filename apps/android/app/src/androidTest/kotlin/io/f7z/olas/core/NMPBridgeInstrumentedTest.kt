package io.f7z.olas.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NMPBridgeInstrumentedTest {
    @Test
    fun androidBridgeUsesSharedRustPhotoFilter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NMPBridge.initialize(context)

        val raw = """
            {
              "id": "abc",
              "author": "0202020202020202020202020202020202020202020202020202020202020202",
              "kind": 20,
              "created_at": 42,
              "tags": [
                [
                  "imeta",
                  "url https://example.com/p.jpg",
                  "x abc123",
                  "m image/jpeg",
                  "dim 800x600",
                  "alt beach"
                ]
              ],
              "content": "hello #olas"
            }
        """.trimIndent()

        assertNotNull(
            "Following mode should parse a valid kind:20 image event through Rust",
            NMPBridge.photoPostJson(raw, FeedMode.FOLLOWING),
        )
        assertNull(
            "Network mode should reject the event until Rust WoT admits the author",
            NMPBridge.photoPostJson(raw, FeedMode.NETWORK),
        )
    }
}
