package com.gifsticker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,          // 96x96 WebP icona del pack
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val iosAppStoreLink: String = "",
    val androidPlayStoreLink: String = "",
    val stickers: List<Sticker> = emptyList(),
    val animatedStickerPack: Boolean = true  // GIF animate → true
) : Parcelable

@Parcelize
data class Sticker(
    val imageFileName: String,          // nome file WebP
    val emojis: List<String> = listOf("😀")
) : Parcelable
