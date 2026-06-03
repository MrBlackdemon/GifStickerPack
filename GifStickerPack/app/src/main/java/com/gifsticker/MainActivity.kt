package com.gifsticker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.gifsticker.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gifAdapter = GifAdapter()
    private var selectedFolderUri: Uri? = null

    // ── Permission launcher ───────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) openFolderPicker()
        else Toast.makeText(this, "Permessi necessari per leggere le GIF", Toast.LENGTH_LONG).show()
    }

    // ── Folder picker launcher ────────────────────────────────────────────────
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedFolderUri = uri
            binding.tvFolderPath.text = uri.lastPathSegment ?: uri.toString()
            loadGifsFromFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        checkWhatsApp()
    }

    private fun setupRecyclerView() {
        binding.rvGifs.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = gifAdapter
        }
    }

    private fun setupButtons() {
        binding.btnSelectFolder.setOnClickListener {
            requestPermissionsAndPick()
        }

        binding.btnSelectAll.setOnClickListener {
            gifAdapter.selectAll(true)
        }

        binding.btnDeselectAll.setOnClickListener {
            gifAdapter.selectAll(false)
        }

        binding.btnCreatePack.setOnClickListener {
            showCreatePackDialog()
        }
    }

    private fun checkWhatsApp() {
        if (!WhatsAppSender.isWhatsAppInstalled(this)) {
            binding.tvWhatsappWarning.visibility = View.VISIBLE
            binding.btnCreatePack.isEnabled = false
        }
    }

    private fun requestPermissionsAndPick() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) openFolderPicker()
        else permissionLauncher.launch(permissions)
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun loadGifsFromFolder(folderUri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val gifs = findGifsInFolder(folderUri)
            gifAdapter.setItems(gifs)
            binding.progressBar.visibility = View.GONE

            if (gifs.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Nessuna GIF trovata in questa cartella"
            } else {
                binding.tvCount.text = "${gifs.size} GIF trovate"
                binding.btnCreatePack.isEnabled = WhatsAppSender.isWhatsAppInstalled(this@MainActivity)
            }
        }
    }

    private fun findGifsInFolder(folderUri: Uri): List<GifItem> {
        val items = mutableListOf<GifItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)

            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val mimeType = it.getString(2) ?: ""
                    val name = it.getString(1) ?: ""
                    if (mimeType == "image/gif" || name.lowercase().endsWith(".gif")) {
                        val docItemId = it.getString(0)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docItemId)
                        items.add(GifItem(uri = fileUri, name = name))
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Errore lettura cartella: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return items.take(30) // WhatsApp max 30 sticker
    }

    private fun showCreatePackDialog() {
        val selected = gifAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Seleziona almeno 3 GIF", Toast.LENGTH_SHORT).show()
            return
        }
        if (selected.size < 3) {
            Toast.makeText(this, "Seleziona almeno 3 GIF (ne hai selezionate ${selected.size})", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_pack, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etPackName)
        val etAuthor = dialogView.findViewById<android.widget.EditText>(R.id.etPackAuthor)

        AlertDialog.Builder(this)
            .setTitle("Crea Sticker Pack")
            .setView(dialogView)
            .setPositiveButton("Crea e Invia") { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { "Le mie GIF" }
                val author = etAuthor.text.toString().trim().ifEmpty { "GifStickerPack" }
                createAndSendPack(selected, name, author)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun createAndSendPack(gifs: List<GifItem>, packName: String, author: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Conversione GIF in corso..."
        binding.btnCreatePack.isEnabled = false

        lifecycleScope.launch {
            try {
                val packId = "pack_${UUID.randomUUID().toString().take(8)}"
                val manager = StickerPackManager.getInstance(this@MainActivity)
                val packDir = manager.getPackDir(packId)

                val stickers = mutableListOf<Sticker>()

                // Converti ogni GIF selezionata
                gifs.forEachIndexed { index, gifItem ->
                    runOnUiThread {
                        binding.tvStatus.text = "Conversione ${index + 1}/${gifs.size}..."
                    }
                    val fileName = "sticker_${String.format("%02d", index + 1)}.webp"
                    val converted = GifConverter.convertGifToWebP(
                        this@MainActivity,
                        gifItem.uri,
                        packDir,
                        fileName
                    )
                    if (converted != null) {
                        stickers.add(Sticker(imageFileName = fileName))
                    }
                }

                if (stickers.isEmpty()) {
                    runOnUiThread {
                        binding.tvStatus.text = "Errore: nessun sticker convertito"
                        binding.progressBar.visibility = View.GONE
                        binding.btnCreatePack.isEnabled = true
                    }
                    return@launch
                }

                // Genera la tray image (icona del pack 96x96) dalla prima GIF
                runOnUiThread { binding.tvStatus.text = "Generazione icona pack..." }
                val trayName = "tray.webp"
                GifConverter.generateTrayImage(
                    this@MainActivity,
                    gifs.first().uri,
                    packDir,
                    trayName
                )

                // Salva il pack
                val pack = StickerPack(
                    identifier = packId,
                    name = packName,
                    publisher = author,
                    trayImageFile = trayName,
                    stickers = stickers,
                    animatedStickerPack = true
                )
                manager.savePack(pack)

                // Invia a WhatsApp
                runOnUiThread {
                    binding.tvStatus.text = "Invio a WhatsApp..."
                    binding.progressBar.visibility = View.GONE
                }

                val result = WhatsAppSender.sendPackToWhatsApp(this@MainActivity, pack)
                runOnUiThread {
                    binding.tvStatus.visibility = View.GONE
                    binding.btnCreatePack.isEnabled = true
                    result.onSuccess {
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Pack \"$packName\" inviato a WhatsApp! (${stickers.size} sticker)",
                            Toast.LENGTH_LONG
                        ).show()
                    }.onFailure { e ->
                        Toast.makeText(
                            this@MainActivity,
                            "❌ Errore: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.visibility = View.GONE
                    binding.btnCreatePack.isEnabled = true
                    Toast.makeText(this@MainActivity, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
