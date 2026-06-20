package io.f7z.olas.feature.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
     * P0-B: Drive a NIP-68 picture post through the two canonical Rust actions.
     * Supports multi-image: each image is encoded + uploaded independently, then
     * the full array of descriptors is passed to Rust's
     * `olas_picture_post_publish_json` which emits ONE kind:20 with multiple
     * NIP-68 `imeta` tags.
     *
     * Native does only render + capability:
     *   1. Downsample + JPEG-encode each image (render).
     *   2. Write the bytes to a temp file (capability — OS file I/O).
     *   3. Measure pixel dimensions for the imeta `dim` (a render fact).
     * Rust owns the rest: `nmp.blossom.upload` hashes + uploads; `nmp.publish`
     * constructs, signs, and routes the kind:20. D8: no polling.
     */
    fun upload(
        context: Context,
        uris: List<Uri>,
        caption: String,
        altTexts: Map<Uri, String> = emptyMap(),
        geohash: String? = null,
        filter: PhotoFilter? = null,
        intensity: Float = 1f,
    ) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                _state.value = UploadState(UploadStep.ENCODING, 0f)

                // Load media config from Rust once for all images.
                val configJson = NMPBridge.mediaUploadConfigJson()
                    ?: """{"max_dimension":2048,"jpeg_quality":0.92}"""
                val config = runCatching { JSONObject(configJson) }.getOrNull()
                val maxDimension = config?.optInt("max_dimension", 2048) ?: 2048
                val jpegQuality = ((config?.optDouble("jpeg_quality", 0.92) ?: 0.92) * 100)
                    .toInt().coerceIn(1, 100)

                // Determine Blossom server list.
                val servers = buildList {
                    val configured = NMPBridge.blossomServerUrl().takeIf { it.isNotBlank() }
                    if (configured != null) add(configured)
                    add("https://blossom.band")
                    add("https://blossom.primal.net")
                }.distinct()

                // Upload each image; collect descriptor + per-image metadata.
                // Parity with iOS (CaptionView.swift): filteredPreview replaces images[0] only;
                // subsequent images are uploaded unfiltered. Mirror that here.
                val uploadedImages = JSONArray()
                uris.forEachIndexed { index, uri ->
                    val progress = index.toFloat() / uris.size
                    _state.value = _state.value.copy(
                        step     = UploadStep.UPLOADING,
                        progress = progress,
                    )
                    val entry = withContext(Dispatchers.IO) {
                        uploadOne(
                            context       = appContext,
                            uri           = uri,
                            maxDimension  = maxDimension,
                            jpegQuality   = jpegQuality,
                            servers       = servers,
                            filter        = if (index == 0) filter else null,
                            intensity     = intensity,
                        )
                    }
                    val obj = JSONObject().apply {
                        put("descriptor", JSONObject(entry.descriptorJson))
                        altTexts[uri]?.takeIf { it.isNotBlank() }?.let { put("alt", it) }
                        put("dim", entry.dim)
                    }
                    uploadedImages.put(obj)
                }

                _state.value = UploadState(UploadStep.PUBLISHING, 1f)

                // Build the publish input in Rust (one kind:20 for all images).
                val publishInput = NMPBridge.picturePostPublishJson(
                    uploadedImagesJson = uploadedImages.toString(),
                    caption            = caption,
                    geohash            = geohash,
                ) ?: throw IllegalStateException("Failed to build publish input")

                val published = withTimeoutOrNull(30_000L) {
                    NMPBridge.dispatchAndAwaitResult("nmp.publish", publishInput)
                }
                if (published != null && !published.succeeded) {
                    throw IllegalStateException("Publish failed: ${published.resultJson.take(200)}")
                }
                _state.value = UploadState(UploadStep.DONE, 1f)
            } catch (e: Exception) {
                _state.value = UploadState(UploadStep.ERROR, 0f, error = e.message)
            }
        }
    }

    private data class UploadedEntry(val descriptorJson: String, val dim: String)

    /**
     * Encode one image to a temp JPEG, upload via `nmp.blossom.upload`, and
     * await the BUD-02 descriptor. Returns the descriptor JSON + dimensions.
     *
     * When [filter] is non-null and not "Original", the selected filter matrix
     * (blended with identity at [intensity]) is rendered onto the bitmap via
     * [Canvas] + [Paint] before JPEG compression — identical to the
     * ColorFilter.colorMatrix overlay shown in the preview. This ensures the
     * uploaded bytes match what the user saw (WYSIWYG).
     */
    private suspend fun uploadOne(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        jpegQuality: Int,
        servers: List<String>,
        filter: PhotoFilter? = null,
        intensity: Float = 1f,
    ): UploadedEntry {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("Failed to decode image")
        val resized = downsample(bitmap, maxDimension)
        // Bake the filter into pixel data using the same blendMatrix logic as
        // FilterCarousel / EditPhotoScreen, so the JPEG matches the preview.
        val toCompress = if (filter != null && filter.name != "Original") {
            bakeFilter(resized, filter.matrix, intensity)
        } else {
            resized
        }
        val dim = "${toCompress.width}x${toCompress.height}"

        val tmp = File.createTempFile("olas_upload_", ".jpg", context.cacheDir)
        try {
            tmp.outputStream().use { out ->
                toCompress.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }

            var terminal: NMPBridge.ActionTerminal? = null
            var lastError = "No servers tried"
            for (server in servers) {
                val uploadInput = NMPBridge.blossomUploadInputJson(
                    filePath  = tmp.absolutePath,
                    mime      = "image/jpeg",
                    serverUrl = server,
                ) ?: continue
                val result = NMPBridge.dispatchAndAwaitResult("nmp.blossom.upload", uploadInput)
                if (result != null && result.succeeded && result.resultJson != "null") {
                    terminal = result
                    break
                }
                val reason = result?.reason?.takeIf { it.isNotEmpty() }
                    ?: result?.resultJson?.take(150)
                    ?: "dispatch rejected"
                lastError = "[$server] $reason"
            }
            terminal ?: throw IllegalStateException("All servers failed. Last: $lastError")
            if (terminal.resultJson == "null") {
                throw IllegalStateException("Upload returned empty result")
            }
            return UploadedEntry(descriptorJson = terminal.resultJson, dim = dim)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Apply [matrix] at [intensity] to [src] by drawing through a [Paint] with
     * a [ColorMatrixColorFilter] onto a fresh [Bitmap]. Uses the same
     * [blendMatrix] / [identityMatrix] functions as [FilterCarousel] so the
     * result is pixel-identical to the Compose preview overlay.
     */
    private fun bakeFilter(src: Bitmap, matrix: FloatArray, intensity: Float): Bitmap {
        val blended = blendMatrix(identityMatrix(), matrix, intensity)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(blended))
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
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
