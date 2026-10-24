package com.adfreepdf.reader.data

import android.content.Context

/** Tiny SharedPreferences-backed store for bookmarks, recents and settings. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("adfree_pdf", Context.MODE_PRIVATE)

    var sortMode: Int
        get() = sp.getInt("sort", SORT_NAME)
        set(v) = sp.edit().putInt("sort", v).apply()

    var lastDark: Boolean
        get() = sp.getBoolean("dark", false)
        set(v) = sp.edit().putBoolean("dark", v).apply()

    // --- bookmarks: set of "uri#page" ---
    fun bookmarks(): MutableSet<String> =
        (sp.getStringSet("bookmarks", emptySet()) ?: emptySet()).toMutableSet()

    fun isBookmarked(key: String) = bookmarks().contains(key)

    fun toggleBookmark(key: String): Boolean {
        val set = bookmarks()
        val added = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
        sp.edit().putStringSet("bookmarks", set).apply()
        return added
    }

    // --- recents: ordered "uri\u0001name" entries, newest first ---
    fun recents(): List<PdfFile> =
        (sp.getString("recents", "") ?: "").split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\u0001")
                if (parts.size < 2) null
                else PdfFile(parts[0], parts[1], parts.getOrNull(2)?.toLongOrNull() ?: 0L, 0L)
            }

    fun addRecent(file: PdfFile) {
        val list = recents().filterNot { it.uri == file.uri }.toMutableList()
        list.add(0, file)
        val trimmed = list.take(30)
        sp.edit().putString(
            "recents",
            trimmed.joinToString("\n") { "${it.uri}\u0001${it.name}\u0001${it.size}" }
        ).apply()
    }

    companion object {
        const val SORT_NAME = 0
        const val SORT_DATE = 1
        const val SORT_SIZE = 2
    }
}
