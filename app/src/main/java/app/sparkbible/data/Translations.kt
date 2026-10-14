package app.sparkbible.data

data class TranslationInfo(
    val id: String,
    val name: String,
    val short: String,
    val note: String,
)

object Translations {
    val all = listOf(
        TranslationInfo(
            "web",
            "World English Bible",
            "WEB",
            "Modern wording. The kindest place to start.",
        ),
        TranslationInfo(
            "kjv",
            "King James Version",
            "KJV",
            "The familiar older rhythm. Public domain.",
        ),
        TranslationInfo(
            "asv",
            "American Standard",
            "ASV",
            "Formal, from 1901. Close to the older text.",
        ),
        TranslationInfo(
            "geneva",
            "Geneva Bible",
            "Geneva",
            "1599 spelling. Same books, older words.",
        ),
        TranslationInfo(
            "dra",
            "Douay-Rheims",
            "DRA",
            "Public-domain Catholic text. Esther and Daniel include extra chapters.",
        ),
        TranslationInfo(
            "bsb",
            "Berean Standard Bible",
            "BSB",
            "Modern wording. Public domain. Already on this phone.",
        ),
        TranslationInfo(
            "lsv",
            "Literal Standard Version",
            "LSV",
            "Word for word. On this phone. Literal Standard Version (LSV), Covenant Press.",
        ),
    )

    fun get(id: String): TranslationInfo = all.find { it.id == id } ?: all.first()
}
