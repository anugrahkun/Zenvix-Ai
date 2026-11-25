package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AttachmentUtils {

    private const val MAX_IMAGE_DIM = 1600
    private const val MAX_DOC_BYTES = 14 * 1024 * 1024 // 14 MB (under 20 MB inline limit)

    suspend fun loadImageAttachment(context: Context, uri: Uri): Result<PendingAttachment.ImageAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = queryDisplayName(context, uri) ?: "Image.jpg"
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

                var sampleSize = 1
                val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                if (maxDim > MAX_IMAGE_DIM) {
                    var half = maxDim / 2
                    while (half / sampleSize >= MAX_IMAGE_DIM) {
                        sampleSize *= 2
                    }
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalStateException("Unable to decode selected image."))

                val scaled = scaleBitmapIfNeeded(bitmap, MAX_IMAGE_DIM)
                Result.success(PendingAttachment.ImageAttachment(displayName = name, bitmap = scaled))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadDocumentAttachment(context: Context, uri: Uri): Result<PendingAttachment.DocumentAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = queryDisplayName(context, uri) ?: "document"
                val mimeType = resolver.getType(uri) ?: inferMimeType(name)
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size > MAX_DOC_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("File exceeds 14 MB limit for inline cloud AI analysis.")
                        )
                    }
                    data
                } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file."))

                val isTextBased = mimeType.startsWith("text/") ||
                    mimeType == "application/json" ||
                    name.endsWith(".md", ignoreCase = true) ||
                    name.endsWith(".kt", ignoreCase = true) ||
                    name.endsWith(".py", ignoreCase = true) ||
                    name.endsWith(".js", ignoreCase = true) ||
                    name.endsWith(".ts", ignoreCase = true) ||
                    name.endsWith(".csv", ignoreCase = true) ||
                    name.endsWith(".xml", ignoreCase = true) ||
                    name.endsWith(".html", ignoreCase = true)

                val extractedText = if (isTextBased) {
                    bytes.toString(Charsets.UTF_8).take(80_000)
                } else {
                    null
                }

                Result.success(
                    PendingAttachment.DocumentAttachment(
                        displayName = name,
                        mimeType = mimeType,
                        bytes = bytes,
                        extractedText = extractedText
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width >= height) {
            targetW = maxDim
            targetH = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDim
            targetW = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun inferMimeType(name: String): String = when {
        name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        name.endsWith(".json", ignoreCase = true) -> "application/json"
        name.endsWith(".html", ignoreCase = true) -> "text/html"
        name.endsWith(".md", ignoreCase = true) -> "text/markdown"
        name.endsWith(".csv", ignoreCase = true) -> "text/csv"
        else -> "text/plain"
    }
}
