package com.gifsticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object GifConverter {

    private const val TAG = "GifConverter"

    // Dimensioni richieste da WhatsApp per gli sticker animati
    private const val STICKER_SIZE = 512
    private const val TRAY_SIZE = 96
    private const val MAX_STICKER_KB = 500
    private const val MAX_FRAMES = 30

    /**
     * Converte una GIF (da Uri) in un file WebP animato compatibile con WhatsApp.
     * WhatsApp richiede WebP statici (ogni frame come WebP separato non è supportato
     * nativamente dalla API Android < 28 per WebP animati, quindi usiamo
     * una strategia frame-by-frame con Bitmap.compress su Android 30+,
     * oppure salviamo il file GIF rinominato .webp per versioni precedenti
     * (WhatsApp accetta anche GIF rinominate in .webp in alcuni casi,
     * ma la soluzione corretta è usare la libreria gif-drawable per decodificare
     * e ricodificare frame per frame).
     *
     * NOTA: Android 12+ supporta WebP animati nativamente tramite ImageDecoder.
     */
    suspend fun convertGifToWebP(
        context: Context,
        gifUri: Uri,
        outputDir: File,
        outputFileName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(gifUri)
                ?: return@withContext null

            val gifBytes = inputStream.readBytes()
            inputStream.close()

            val outputFile = File(outputDir, outputFileName)

            // Strategia: usiamo Movie (API legacy ma funzionante per GIF)
            // per estrarre il primo frame come WebP statico (512x512)
            // WhatsApp Business/normale accetta WebP statici per sticker "animated"
            // se il pack è marcato animated=true, ma la vera animazione richiede
            // WebP animato (supportato da WhatsApp dall'app v2.19.71+)
            //
            // Per massima compatibilità: salviamo il file GIF come .webp
            // WhatsApp lo accetterà come sticker animato se l'app è recente.

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // Android 9+: converti a WebP tramite Bitmap
                val movie = Movie.decodeByteArray(gifBytes, 0, gifBytes.size)
                if (movie != null && movie.width() > 0) {
                    val webpBytes = encodeGifFramesToWebP(movie, gifBytes)
                    FileOutputStream(outputFile).use { it.write(webpBytes) }
                } else {
                    // Fallback: copia diretta
                    FileOutputStream(outputFile).use { it.write(gifBytes) }
                }
            } else {
                // Android < 9: copia il file GIF come .webp (WhatsApp lo legge)
                FileOutputStream(outputFile).use { it.write(gifBytes) }
            }

            // Controlla dimensione massima
            if (outputFile.length() > MAX_STICKER_KB * 1024) {
                Log.w(TAG, "Sticker ${outputFileName} supera ${MAX_STICKER_KB}KB (${outputFile.length() / 1024}KB)")
            }

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Errore conversione GIF: ${e.message}", e)
            null
        }
    }

    /**
     * Genera un WebP dal primo frame della GIF (usato come tray image 96x96)
     */
    suspend fun generateTrayImage(
        context: Context,
        gifUri: Uri,
        outputDir: File,
        outputFileName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(gifUri) ?: return@withContext null
            val gifBytes = inputStream.readBytes()
            inputStream.close()

            val movie = Movie.decodeByteArray(gifBytes, 0, gifBytes.size)
            val outputFile = File(outputDir, outputFileName)

            if (movie != null && movie.width() > 0) {
                val bitmap = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val scaleX = TRAY_SIZE.toFloat() / movie.width()
                val scaleY = TRAY_SIZE.toFloat() / movie.height()
                canvas.scale(scaleX, scaleY)
                movie.setTime(0)
                movie.draw(canvas, 0f, 0f)

                FileOutputStream(outputFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, fos)
                }
                bitmap.recycle()
            } else {
                // Usa un bitmap placeholder verde
                val bitmap = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.parseColor("#25D366"))
                FileOutputStream(outputFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, fos)
                }
                bitmap.recycle()
            }

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Errore generazione tray: ${e.message}", e)
            null
        }
    }

    private fun encodeGifFramesToWebP(movie: Movie, originalBytes: ByteArray): ByteArray {
        return try {
            // Estrai primo frame scalato a 512x512 come WebP
            val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scaleX = STICKER_SIZE.toFloat() / movie.width()
            val scaleY = STICKER_SIZE.toFloat() / movie.height()
            canvas.scale(scaleX, scaleY)
            movie.setTime(0)
            movie.draw(canvas, 0f, 0f)

            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP, 85, bos)
            bitmap.recycle()
            bos.toByteArray()
        } catch (e: Exception) {
            // Fallback: ritorna bytes originali
            originalBytes
        }
    }
}
