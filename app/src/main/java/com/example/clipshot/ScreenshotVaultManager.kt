package com.example.clipshot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class VaultItem(
    val id: String,
    val fileName: String,
    val timestamp: Long,
    var isPinned: Boolean
) {
    fun getRemainingMillis(now: Long = System.currentTimeMillis()): Long {
        val expiry = timestamp + TimeUnit.HOURS.toMillis(24)
        return (expiry - now).coerceAtLeast(0L)
    }

    fun getFormattedStatus(now: Long = System.currentTimeMillis()): String {
        if (isPinned) return "📌 Pinned (Kept Forever)"
        val rem = getRemainingMillis(now)
        if (rem <= 0L) return "Expired"
        val hours = TimeUnit.MILLISECONDS.toHours(rem)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(rem) % 60
        return if (hours > 0) {
            "⏳ Expires in ${hours}h ${minutes}m"
        } else {
            "⏳ Expires in ${minutes}m"
        }
    }
}

object ScreenshotVaultManager {
    private const val PREFS_NAME = "clipshot_vault_prefs"
    private const val KEY_ITEMS = "vault_items_json"
    private val TTL_MILLIS = TimeUnit.HOURS.toMillis(24)

    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    /**
     * Purges any unpinned screenshot older than 24 hours from both disk and metadata.
     */
    @Synchronized
    fun purgeExpired(context: Context): List<VaultItem> {
        val now = System.currentTimeMillis()
        val vaultDir = getVaultDir(context)
        val allItems = loadRawItems(context)
        val validItems = mutableListOf<VaultItem>()
        var changed = false

        for (item in allItems) {
            val file = File(vaultDir, item.fileName)
            val isExpired = !item.isPinned && (now - item.timestamp >= TTL_MILLIS)
            if (isExpired || !file.exists()) {
                if (file.exists()) file.delete()
                changed = true
            } else {
                validItems.add(item)
            }
        }

        // Also clean up any orphan files not tracked in metadata
        val trackedNames = validItems.map { it.fileName }.toSet()
        vaultDir.listFiles()?.forEach { f ->
            if (f.name != ".nomedia" && f.name !in trackedNames) {
                f.delete()
            }
        }

        if (changed) {
            saveRawItems(context, validItems)
        }
        // Sort: Pinned items first, or newest first
        return validItems.sortedByDescending { it.timestamp }
    }

    /**
     * Saves a newly captured Bitmap into the 24h private vault and copies it to the system Clipboard.
     */
    @Synchronized
    fun saveAndCopyToClipboard(context: Context, bitmap: Bitmap): VaultItem {
        purgeExpired(context)

        val now = System.currentTimeMillis()
        val id = now.toString()
        val fileName = "clip_$id.png"
        val vaultDir = getVaultDir(context)
        val file = File(vaultDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val newItem = VaultItem(
            id = id,
            fileName = fileName,
            timestamp = now,
            isPinned = false
        )

        val items = loadRawItems(context).toMutableList()
        items.add(0, newItem)
        saveRawItems(context, items)

        copyFileToClipboard(context, file)
        return newItem
    }

    @Synchronized
    fun togglePin(context: Context, id: String): Boolean {
        val items = loadRawItems(context).toMutableList()
        val target = items.find { it.id == id } ?: return false
        target.isPinned = !target.isPinned
        saveRawItems(context, items)
        return target.isPinned
    }

    @Synchronized
    fun deleteItem(context: Context, id: String) {
        val vaultDir = getVaultDir(context)
        val items = loadRawItems(context).toMutableList()
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.id == id) {
                File(vaultDir, item.fileName).delete()
                iterator.remove()
            }
        }
        saveRawItems(context, items)
    }

    @Synchronized
    fun clearUnpinned(context: Context) {
        val vaultDir = getVaultDir(context)
        val items = loadRawItems(context).toMutableList()
        val remaining = mutableListOf<VaultItem>()
        for (item in items) {
            if (item.isPinned) {
                remaining.add(item)
            } else {
                File(vaultDir, item.fileName).delete()
            }
        }
        saveRawItems(context, remaining)
    }

    fun getFileForItem(context: Context, item: VaultItem): File {
        return File(getVaultDir(context), item.fileName)
    }

    fun copyItemToClipboard(context: Context, item: VaultItem): Boolean {
        val file = getFileForItem(context, item)
        if (!file.exists()) return false
        copyFileToClipboard(context, file)
        return true
    }

    private fun copyFileToClipboard(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.fileprovider",
            file
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Screenshot", uri)
        clipboard.setPrimaryClip(clip)
    }

    private fun loadRawItems(context: Context): List<VaultItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val list = mutableListOf<VaultItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VaultItem(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        timestamp = obj.getLong("timestamp"),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveRawItems(context: Context, items: List<VaultItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("timestamp", item.timestamp)
                put("isPinned", item.isPinned)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }
}
