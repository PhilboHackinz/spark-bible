package app.sparkbible.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.sparkbible.MainActivity
import app.sparkbible.R
import app.sparkbible.SparkApp
import app.sparkbible.data.Logic
import app.sparkbible.data.SettingsStore
import app.sparkbible.data.Translations

class SparkWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SparkWidget::class.java))
            if (ids.isEmpty()) return
            update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as SparkApp
            val saved = context.getSharedPreferences(SettingsStore.WIDGET_PREFS, Context.MODE_PRIVATE)
                .getString(SettingsStore.WIDGET_TRANSLATION, "web") ?: "web"
            app.bible.translation = saved
            val short = Translations.get(saved).short
            val spark = Logic.daily()
            val (label, text) = try {
                val book = app.bible.loadBook(spark.book)
                val slice = book[spark.chapter - 1].subList(
                    (spark.start - 1).coerceAtLeast(0),
                    spark.end.coerceAtMost(book[spark.chapter - 1].size),
                )
                val name = app.bible.bookName(spark.book)
                val ref = if (spark.start == spark.end) "$short · $name ${spark.chapter}:${spark.start}"
                else "$short · $name ${spark.chapter}:${spark.start}–${spark.end}"
                ref to slice.joinToString(" ")
            } catch (_: Exception) {
                "$short · John 14:27" to "Peace I leave with you. My peace I give to you."
            }
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN, "daily")
            }
            val pending = PendingIntent.getActivity(
                context,
                7,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_spark)
                views.setTextViewText(R.id.widget_ref, label)
                views.setTextViewText(R.id.widget_verse, text)
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
