package app.sparkbible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BibleStore(private val context: Context) {
    @Volatile
    var translation: String = "web"

    private val books = HashMap<String, List<BookInfo>>()
    private val cache = HashMap<String, List<List<String>>>()

    fun index(): List<BookInfo> = indexFor(Translations.get(translation).id)

    fun bookName(i: Int): String = index().getOrNull(i)?.name ?: "Scripture"

    fun loadBook(i: Int): List<List<String>> {
        val id = Translations.get(translation).id
        val key = "$id:$i"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val text = context.assets.open("bible/$id/$i.json").bufferedReader().use { it.readText() }
        val root = JSONArray(text)
        val chapters = ArrayList<List<String>>(root.length())
        for (c in 0 until root.length()) {
            val chapter = root.getJSONArray(c)
            val verses = ArrayList<String>(chapter.length())
            for (v in 0 until chapter.length()) verses.add(chapter.getString(v).trim())
            chapters.add(verses)
        }
        synchronized(cache) { cache[key] = chapters }
        return chapters
    }

    private fun indexFor(id: String): List<BookInfo> {
        synchronized(books) {
            books[id]?.let { return it }
        }
        val text = context.assets.open("bible/$id/index.json").bufferedReader().use { it.readText() }
        val list = JSONObject(text).getJSONArray("books")
        val parsed = List(list.length()) { n ->
            val b = list.getJSONObject(n)
            BookInfo(
                i = b.getInt("i"),
                id = b.getString("id"),
                name = b.getString("name"),
                testament = b.getString("testament"),
                chapters = b.getInt("chapters"),
            )
        }
        synchronized(books) { books[id] = parsed }
        return parsed
    }
}
