package com.gifsticker

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Gestisce i StickerPack salvati nella cartella files/ dell'app.
 * Struttura su disco:
 *   files/sticker_packs/<identifier>/
 *       tray.webp
 *       sticker_01.webp
 *       sticker_02.webp
 *       ...
 *   files/sticker_packs/packs.json
 */
class StickerPackManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "StickerPackManager"
        private const val PACKS_DIR = "sticker_packs"
        private const val PACKS_INDEX = "packs.json"

        @Volatile
        private var instance: StickerPackManager? = null

        fun getInstance(context: Context): StickerPackManager =
            instance ?: synchronized(this) {
                instance ?: StickerPackManager(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val packsRoot: File
        get() = File(context.filesDir, PACKS_DIR).also { it.mkdirs() }

    // ── Lettura ──────────────────────────────────────────────────────────────

    fun getAllPacks(): List<StickerPack> {
        val indexFile = File(packsRoot, PACKS_INDEX)
        if (!indexFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<StickerPack>>() {}.type
            gson.fromJson(indexFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Errore lettura index: ${e.message}")
            emptyList()
        }
    }

    fun getPackById(identifier: String): StickerPack? =
        getAllPacks().find { it.identifier == identifier }

    fun getStickerFile(packIdentifier: String, fileName: String): File? {
        val file = File(File(packsRoot, packIdentifier), fileName)
        return if (file.exists()) file else null
    }

    fun getTrayFile(packIdentifier: String): File? {
        val pack = getPackById(packIdentifier) ?: return null
        val file = File(File(packsRoot, packIdentifier), pack.trayImageFile)
        return if (file.exists()) file else null
    }

    fun getPackDir(identifier: String): File =
        File(packsRoot, identifier).also { it.mkdirs() }

    // ── Scrittura ─────────────────────────────────────────────────────────────

    fun savePack(pack: StickerPack) {
        val packs = getAllPacks().toMutableList()
        val existing = packs.indexOfFirst { it.identifier == pack.identifier }
        if (existing >= 0) packs[existing] = pack else packs.add(pack)
        saveIndex(packs)
    }

    fun deletePack(identifier: String) {
        // Rimuovi la cartella
        File(packsRoot, identifier).deleteRecursively()
        // Aggiorna index
        val packs = getAllPacks().filter { it.identifier != identifier }
        saveIndex(packs)
    }

    private fun saveIndex(packs: List<StickerPack>) {
        File(packsRoot, PACKS_INDEX).writeText(gson.toJson(packs))
    }

    // ── Validazione ───────────────────────────────────────────────────────────

    fun validatePack(pack: StickerPack): List<String> {
        val errors = mutableListOf<String>()
        if (pack.stickers.size < 3)
            errors.add("Il pack deve avere almeno 3 sticker (ne ha ${pack.stickers.size})")
        if (pack.stickers.size > 30)
            errors.add("Il pack può avere massimo 30 sticker (ne ha ${pack.stickers.size})")
        val packDir = getPackDir(pack.identifier)
        for (sticker in pack.stickers) {
            val f = File(packDir, sticker.imageFileName)
            if (!f.exists()) errors.add("File mancante: ${sticker.imageFileName}")
            else if (f.length() > 500 * 1024) errors.add("${sticker.imageFileName} supera 500KB")
        }
        return errors
    }
}
