package app.sparkbible

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.sparkbible.data.BibleStore
import app.sparkbible.data.Repository
import app.sparkbible.data.SettingsStore
import app.sparkbible.data.SparkDb
import app.sparkbible.listen.ListenService
import app.sparkbible.remind.Reminder

class SparkApp : Application() {
    lateinit var bible: BibleStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        bible = BibleStore(this)
        settings = SettingsStore(this)
        val db = SparkDb.create(this)
        repo = Repository(bible, db, settings)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                ListenService.CHANNEL,
                getString(R.string.channel_listen),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                Reminder.CHANNEL,
                getString(R.string.channel_remind),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
