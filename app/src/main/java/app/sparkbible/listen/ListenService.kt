package app.sparkbible.listen

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import app.sparkbible.MainActivity
import app.sparkbible.R
import app.sparkbible.SparkApp
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ListenService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending = false
    private var playing = false
    private var verses: List<String> = emptyList()
    private var verseIndex = 0
    private var rate = 1f
    private var walking = false
    private var title = "Spark Bible"
    private var voiceName = ""
    private var boundEngine: String? = null
    private var currentText = ""
    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var wake: PowerManager.WakeLock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepStop = Runnable {
        if (verses.isNotEmpty()) countIt() else shutdown()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spark:listen")
            .apply { setReferenceCounted(false) }
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    main.post { pauseInternal() }
                }
            }
            .build()
        session = MediaSessionCompat(this, "spark").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumeInternal()
                override fun onPause() = pauseInternal()
                override fun onStop() = shutdown()
            })
            isActive = true
        }
        startForeground(NOTIF, notification("Ready when you are", false))
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ListenBus.error.value = true
            ListenBus.playing.value = false
            return
        }
        ready = true
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        applyVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                ListenBus.breath.value = false
                ListenBus.verse.value = verseIndex
                ListenBus.word.value = 0
            }

            override fun onDone(utteranceId: String?) {
                main.post {
                    if (!playing) return@post
                    verseIndex += 1
                    if (verseIndex >= verses.size) {
                        finishSpeech()
                    } else if (walking) {
                        ListenBus.breath.value = true
                        ListenBus.word.value = -1
                        main.postDelayed({ if (playing) speak() }, 1600)
                    } else {
                        main.postDelayed({ if (playing) speak() }, 220)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ListenBus.error.value = true
                ListenBus.playing.value = false
                playing = false
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                ListenBus.word.value = wordIndexAt(currentText, start)
                ListenBus.verse.value = verseIndex
                ListenBus.breath.value = false
            }
        })
        if (pending && playing) {
            pending = false
            speak()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF, notification(title, playing))
        when (intent?.action) {
            ACTION_PLAY -> {
                val lines = intent.getStringArrayListExtra(EXTRA_LINES).orEmpty()
                if (lines.isNotEmpty()) {
                    verses = lines
                    verseIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, lines.lastIndex)
                    rate = intent.getFloatExtra(EXTRA_RATE, 1f)
                    walking = intent.getBooleanExtra(EXTRA_WALKING, false)
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "Spark Bible"
                    voiceName = intent.getStringExtra(EXTRA_VOICE) ?: ""
                    val enginePkg = intent.getStringExtra(EXTRA_ENGINE) ?: ""
                    val sleepMinutes = intent.getIntExtra(EXTRA_SLEEP, 0)
                    playing = true
                    ListenBus.title.value = title
                    ListenBus.playing.value = true
                    ListenBus.error.value = false
                    ListenBus.counted.value = ""
                    acquire()
                    audioManager.requestAudioFocus(focusRequest)
                    publishState()
                    prepare(enginePkg)
                    armSleep(sleepMinutes)
                    if (ready) {
                        applyVoice()
                        speak()
                    } else {
                        pending = true
                    }
                }
            }
            ACTION_TOGGLE -> if (playing) pauseInternal() else if (verses.isNotEmpty()) resumeInternal()
            ACTION_STOP -> shutdown()
            ACTION_COUNT -> countIt()
            ACTION_SLEEP -> {
                if (verses.isEmpty()) shutdown()
                else armSleep(intent.getIntExtra(EXTRA_SLEEP, 0))
            }
            ACTION_EXTEND -> {
                val lines = intent.getStringArrayListExtra(EXTRA_LINES).orEmpty()
                if (lines.isNotEmpty()) verses = lines
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        playing = false
        tts?.stop()
        tts?.shutdown()
        session.release()
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    private fun prepare(enginePkg: String) {
        if (tts != null && boundEngine == enginePkg) return
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        boundEngine = enginePkg
        tts = if (enginePkg.isBlank()) {
            TextToSpeech(this, this)
        } else {
            TextToSpeech(this, this, enginePkg)
        }
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val wanted = voiceName
        val match = if (wanted.isBlank()) null else engine.voices?.firstOrNull { it.name == wanted }
        if (match != null) {
            engine.voice = match
        } else {
            engine.language = Locale.US
        }
    }

    private fun speak() {
        val engine = tts ?: return
        if (!playing) return
        if (verseIndex >= verses.size) {
            finishSpeech()
            return
        }
        currentText = verses[verseIndex]
        engine.setSpeechRate(rate)
        ListenBus.verse.value = verseIndex
        ListenBus.playing.value = true
        updateNotification()
        publishState()
        engine.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, "v$verseIndex")
    }

    private fun pauseInternal() {
        playing = false
        pending = false
        tts?.stop()
        ListenBus.playing.value = false
        ListenBus.breath.value = false
        updateNotification()
        publishState()
        releaseWake()
    }

    private fun resumeInternal() {
        if (verses.isEmpty()) return
        playing = true
        ListenBus.playing.value = true
        acquire()
        audioManager.requestAudioFocus(focusRequest)
        if (ready) speak() else pending = true
    }

    private fun finishSpeech() {
        playing = false
        ListenBus.playing.value = false
        ListenBus.breath.value = false
        ListenBus.word.value = -1
        releaseWake()
        val done = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spark)
            .setContentTitle("That's the end")
            .setContentText("Tap count if you want this kept. Or don't. Both are fine.")
            .setContentIntent(openIntent())
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_spark, "Count it", serviceIntent(ACTION_COUNT))
            .build()
        stopForeground(STOP_FOREGROUND_DETACH)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF, done)
        publishState()
    }

    private fun countIt() {
        val label = title
        scope.launch {
            val place = (application as SparkApp).repo.completeOpen(label)
            if (place != null) ListenBus.counted.value = place.label
            main.post { shutdown() }
        }
    }

    private fun armSleep(minutes: Int) {
        main.removeCallbacks(sleepStop)
        ListenBus.sleepMinutes.value = minutes.coerceAtLeast(0)
        if (minutes > 0) main.postDelayed(sleepStop, minutes * 60_000L)
    }

    private fun shutdown() {
        main.removeCallbacks(sleepStop)
        ListenBus.sleepMinutes.value = 0
        playing = false
        pending = false
        tts?.stop()
        ListenBus.playing.value = false
        ListenBus.breath.value = false
        ListenBus.word.value = -1
        releaseWake()
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquire() {
        if (!wake.isHeld) wake.acquire(30 * 60 * 1000L)
    }

    private fun releaseWake() {
        if (wake.isHeld) wake.release()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF, notification(title, playing))
    }

    private fun notification(text: String, active: Boolean): Notification {
        val action = if (active) {
            NotificationCompat.Action(R.drawable.ic_stat_pause, "Pause", serviceIntent(ACTION_TOGGLE))
        } else {
            NotificationCompat.Action(R.drawable.ic_stat_play, "Play", serviceIntent(ACTION_TOGGLE))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spark)
            .setContentTitle("Spark Bible")
            .setContentText(text)
            .setContentIntent(openIntent())
            .setOngoing(active)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action)
            .addAction(R.drawable.ic_stat_spark, "Stop", serviceIntent(ACTION_STOP))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    private fun openIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, ListenService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun publishState() {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Spark Bible")
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) rate else 0f)
                .build(),
        )
    }

    companion object {
        const val ACTION_PLAY = "app.sparkbible.PLAY"
        const val ACTION_TOGGLE = "app.sparkbible.TOGGLE"
        const val ACTION_STOP = "app.sparkbible.STOP"
        const val ACTION_COUNT = "app.sparkbible.COUNT"
        const val ACTION_SLEEP = "app.sparkbible.SLEEP"
        const val ACTION_EXTEND = "app.sparkbible.EXTEND"
        const val EXTRA_LINES = "lines"
        const val EXTRA_RATE = "rate"
        const val EXTRA_WALKING = "walking"
        const val EXTRA_TITLE = "title"
        const val EXTRA_INDEX = "index"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SLEEP = "sleep"
        const val CHANNEL = "spark_listen"
        private const val NOTIF = 42

        fun play(
            context: Context,
            lines: List<String>,
            index: Int,
            rate: Float,
            walking: Boolean,
            title: String,
            engine: String = "",
            voice: String = "",
            sleepMinutes: Int = 0,
        ) {
            val intent = Intent(context, ListenService::class.java).apply {
                action = ACTION_PLAY
                putStringArrayListExtra(EXTRA_LINES, ArrayList(lines))
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_RATE, rate)
                putExtra(EXTRA_WALKING, walking)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ENGINE, engine)
                putExtra(EXTRA_VOICE, voice)
                putExtra(EXTRA_SLEEP, sleepMinutes)
            }
            context.startForegroundService(intent)
        }

        fun toggle(context: Context) {
            context.startService(Intent(context, ListenService::class.java).setAction(ACTION_TOGGLE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListenService::class.java).setAction(ACTION_STOP))
        }

        fun sleep(context: Context, minutes: Int) {
            context.startService(
                Intent(context, ListenService::class.java)
                    .setAction(ACTION_SLEEP)
                    .putExtra(EXTRA_SLEEP, minutes),
            )
        }

        fun extend(context: Context, lines: List<String>) {
            context.startService(
                Intent(context, ListenService::class.java)
                    .setAction(ACTION_EXTEND)
                    .putStringArrayListExtra(EXTRA_LINES, ArrayList(lines)),
            )
        }
    }
}

private fun wordIndexAt(text: String, charStart: Int): Int {
    val matches = Regex("\\S+").findAll(text).toList()
    if (matches.isEmpty()) return 0
    val found = matches.indexOfFirst { charStart <= it.range.last }
    return if (found < 0) matches.lastIndex else found
}
