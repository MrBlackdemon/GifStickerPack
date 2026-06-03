package com.gifsticker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * ContentProvider richiesto dal protocollo WhatsApp Sticker.
 * WhatsApp interroga questo provider per ottenere la lista dei pack
 * e i singoli file WebP degli sticker.
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerContentProvider"

        // Colonne metadata pack
        val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )

        // Colonne sticker
        val STICKER_COLUMNS = arrayOf(
            "sticker_file_name",
            "sticker_emoji"
        )

        private const val METADATA = 1
        private const val METADATA_CODE = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4
        private const val STICKER_PACK_TRAY_ICON = 5

        private lateinit var AUTHORITY: String
        private lateinit var uriMatcher: UriMatcher

        private const val METADATA_PATH = "metadata"
        private const val STICKERS_PATH = "stickers"
        private const val STICKERS_ASSET_PATH = "stickers_asset"
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        AUTHORITY = "${context.packageName}.stickercontentprovider"

        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, METADATA_PATH, METADATA)
            addURI(AUTHORITY, "$METADATA_PATH/*", METADATA_CODE)
            addURI(AUTHORITY, "$STICKERS_PATH/*", STICKERS)
            addURI(AUTHORITY, "$STICKERS_ASSET_PATH/*/*", STICKERS_ASSET)
            addURI(AUTHORITY, "$STICKERS_ASSET_PATH/*/$METADATA_PATH", STICKER_PACK_TRAY_ICON)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val packManager = StickerPackManager.getInstance(context)

        return when (uriMatcher.match(uri)) {
            METADATA -> {
                // Ritorna tutti i pack
                val cursor = MatrixCursor(METADATA_COLUMNS)
                for (pack in packManager.getAllPacks()) {
                    cursor.addRow(packToRow(pack))
                }
                cursor
            }
            METADATA_CODE -> {
                // Ritorna un pack specifico
                val identifier = uri.lastPathSegment ?: return null
                val pack = packManager.getPackById(identifier) ?: return null
                val cursor = MatrixCursor(METADATA_COLUMNS)
                cursor.addRow(packToRow(pack))
                cursor
            }
            STICKERS -> {
                // Ritorna gli sticker di un pack
                val identifier = uri.lastPathSegment ?: return null
                val pack = packManager.getPackById(identifier) ?: return null
                val cursor = MatrixCursor(STICKER_COLUMNS)
                for (sticker in pack.stickers) {
                    cursor.addRow(arrayOf(
                        sticker.imageFileName,
                        sticker.emojis.joinToString(",")
                    ))
                }
                cursor
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null

        return when (uriMatcher.match(uri)) {
            STICKERS_ASSET -> {
                val pathSegments = uri.pathSegments
                if (pathSegments.size != 3) return null
                val packIdentifier = pathSegments[1]
                val stickerFileName = pathSegments[2]

                val stickerFile = StickerPackManager.getInstance(context)
                    .getStickerFile(packIdentifier, stickerFileName)

                if (stickerFile != null && stickerFile.exists()) {
                    ParcelFileDescriptor.open(stickerFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }
            STICKER_PACK_TRAY_ICON -> {
                val packIdentifier = uri.pathSegments[1]
                val trayFile = StickerPackManager.getInstance(context)
                    .getTrayFile(packIdentifier)

                if (trayFile != null && trayFile.exists()) {
                    ParcelFileDescriptor.open(trayFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }
            else -> null
        }
    }

    private fun packToRow(pack: StickerPack): Array<Any> = arrayOf(
        pack.identifier,
        pack.name,
        pack.publisher,
        pack.trayImageFile,
        pack.androidPlayStoreLink,
        pack.iosAppStoreLink,
        pack.publisherEmail,
        pack.publisherWebsite,
        pack.privacyPolicyWebsite,
        pack.licenseAgreementWebsite,
        "1",            // image_data_version
        0,              // avoid_cache
        if (pack.animatedStickerPack) 1 else 0
    )

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
