package io.f7z.olas.feature.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Canonical picture-post upload state machine, shared verbatim with iOS
 * (`UploadStep` in Core/Models.swift). Hashing + signing live in Rust
 * (nmp-blossom hashes; nmp.publish signs), so they are not native steps:
 *   IDLE → ENCODING → UPLOADING(progress) → PUBLISHING → DONE | ERROR
 */
enum class UploadStep { IDLE, ENCODING, UPLOADING, PUBLISHING, DONE, ERROR }

data class UploadState(
    val step: UploadStep = UploadStep.IDLE,
    val progress: Float  = 0f,
    val error: String?   = null,
)

class UploadViewModel : ViewModel() {

    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    /**
     * Drive a NIP-68 picture post through the two canonical Rust actions, in
     * lock-step with iOS. Native does only render + capability:
     *   1. Downsample + JPEG-encode each image (render).
     *   2. Write the bytes to a temp file (capability — OS file I/O).
     *   3. Measure pixel dimensions for the imeta `dim` (a render fact).
     * Rust owns the rest: `nmp.blossom.upload` hashes + uploads; `nmp.publish`
     * (PublishRaw) constructs, signs, and routes the kind:20. No SHA-256, no
     * event JSON, no signing, no imeta assembly happen here. D8: no polling —
     * each Rust action terminal resolves a suspended awaiter.
     */
    fun upload(
        context: Context,
        uris: List<Uri>,
        caption: String,
        altTexts: Map<Uri, String>,
        includeLocation: Boolean,
    ) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                _state.value = UploadState(UploadStep.ENCODING, 0f)

                // Single-image parity with iOS: encode + upload the first image,
                // await its BUD-02 descriptor, then publish one kind:20 carrying
                // the caption + imeta. Rust signs + routes.
                val uri = uris.first()
                _state.value = _state.value.copy(step = UploadStep.UPLOADING, progress = 0f)
                val publishInput = withContext(Dispatchers.IO) {
                    uploadOne(appContext, uri, caption, altTexts[uri], includeLocation)
                } ?: throw IllegalStateException("Upload failed")

                _state.value = UploadState(UploadStep.PUBLISHING, 1f)
                val published = NMPBridge.dispatchAndAwaitResult("nmp.publish", publishInput)
                if (published == null || !published.succeeded) {
                    throw IllegalStateException("Couldn't publish post")
                }
                _state.value = UploadState(UploadStep.DONE, 1f)
            } catch (e: Exception) {
                _state.value = UploadState(UploadStep.ERROR, 0f, error = e.message)
            }
        }
    }

    /**
     * Encode one image to a temp JPEG, upload via `nmp.blossom.upload`, await the
     * BUD-02 descriptor, and return the ready-to-dispatch `nmp.publish` input
     * JSON (built in Rust). Returns null on any failure.
     */
    private suspend fun uploadOne(
        context: Context,
        uri: Uri,
        caption: String,
        alt: String?,
        includeLocation: Boolean,
    ): String? {
        // 1. Load upload config from Rust (max_dimension, jpeg_quality).
        val configJson = NMPBridge.mediaUploadConfigJson() ?: """{"max_dimension":2048,"jpeg_quality":0.92}"""
        val config = runCatching { JSONObject(configJson) }.getOrNull()
        val maxDimension = config?.optInt("max_dimension", 2048) ?: 2048
        val jpegQuality = ((config?.optDouble("jpeg_quality", 0.92) ?: 0.92) * 100).toInt().coerceIn(1, 100)

        // 2. Decode + downsample (render).
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val resized = downsample(bitmap, maxDimension)
        val dim = "${resized.width}x${resized.height}"

        // 3. Write JPEG to a temp file (capability).
        val tmp = File.createTempFile("olas_upload_", ".jpg", context.cacheDir)
        try {
            tmp.outputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out) }

            // 4. Dispatch the Blossom upload action and await the descriptor.
            val uploadInput = NMPBridge.blossomUploadInputJson(
                filePath = tmp.absolutePath,
                mime = "image/jpeg",
                serverUrl = NMPBridge.primaryBlossomServer(),
            ) ?: return null
            val terminal = NMPBridge.dispatchAndAwaitResult("nmp.blossom.upload", uploadInput)
            if (terminal == null || !terminal.succeeded || terminal.resultJson == "null") return null

            val geohash = if (includeLocation) currentCoarseGeohash(context) else null

            // 5. Build the nmp.publish (PublishRaw) input in Rust from the descriptor.
            return NMPBridge.picturePostPublishJson(
                blossomResultJson = terminal.resultJson,
                caption = caption,
                alt = alt,
                dim = dim,
                geohash = geohash,
            )
        } finally {
            tmp.delete()
        }
    }

    private fun downsample(src: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= maxDimension) return src
        val scale = maxDimension.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            true,
        )
    }
}
