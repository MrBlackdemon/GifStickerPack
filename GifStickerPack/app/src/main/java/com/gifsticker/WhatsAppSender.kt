package com.gifsticker

import android.content.Context
import android.content.Intent
import android.util.Log

object WhatsAppSender {

    private const val TAG = "WhatsAppSender"

    // Action ufficiale WhatsApp per aggiungere sticker pack
    private const val ACTION_ADD_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"
    private const val PLAY_STORE_SUFFIX = "/details?id="

    fun sendPackToWhatsApp(context: Context, pack: StickerPack): Result<Unit> {
        return try {
            val authority = "${context.packageName}.stickercontentprovider"

            // Valida il pack prima di inviare
            val errors = StickerPackManager.getInstance(context).validatePack(pack)
            if (errors.isNotEmpty()) {
                return Result.failure(Exception("Errore validazione:\n${errors.joinToString("\n")}"))
            }

            val intent = Intent(ACTION_ADD_PACK).apply {
                putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
                putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
                putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
            }

            // Prova WhatsApp normale
            intent.setPackage("com.whatsapp")
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return Result.success(Unit)
            }

            // Prova WhatsApp Business
            intent.setPackage("com.whatsapp.w4b")
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return Result.success(Unit)
            }

            Result.failure(Exception("WhatsApp non trovato sul dispositivo"))
        } catch (e: Exception) {
            Log.e(TAG, "Errore invio pack: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
