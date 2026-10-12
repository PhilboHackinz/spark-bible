package app.sparkbible.remind

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sparkbible.MainActivity
import app.sparkbible.R
import app.sparkbible.SparkApp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Reminder {
    private const val WORK = "spark-reminder"
    const val CHANNEL = "spark_remind"

    fun schedule(context: Context, time: String) {
        val delay = millisUntil(time).coerceAtLeast(15_000L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun millisUntil(hhmm: String): Long {
        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
        val now = LocalDateTime.now()
        var target = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SparkApp
        val settings = app.settings.current()
        if (!settings.reminderOn) return Result.success()
        val today = LocalDate.now().toString()
        val row = app.repo.progressRow()
        val already = app.repo.momentsOnce().any { moment ->
            Instant.ofEpochMilli(moment.at).atZone(ZoneId.systemDefault()).toLocalDate().toString() == today
        }
        if (!already && row?.lastNotified != today) {
            val open = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN, "daily")
            }
            val pending = PendingIntent.getActivity(
                applicationContext,
                9,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val note = NotificationCompat.Builder(applicationContext, Reminder.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_spark)
                .setContentTitle("A short spark is here")
                .setContentText("If you want it. No pressure, and missing it changes nothing.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(77, note)
            app.repo.markNotified(today)
        }
        Reminder.schedule(applicationContext, settings.reminderTime)
        return Result.success()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as SparkApp
                val settings = app.settings.current()
                if (settings.reminderOn) Reminder.schedule(context, settings.reminderTime)
            } finally {
                pending.finish()
            }
        }
    }
}
