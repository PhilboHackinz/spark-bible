package app.sparkbible.listen

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

private data class VerseMark(val frame: Int, val index: Int)

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
    private var engineId = ""
    private var useKokoro = false
    private var kokoro: OfflineTts? = null
    private val kokoroLock = Mutex()
    private var stream: AudioTrack? = null
    private var streamRate = 0
    private var framesQueued = 0
    private var holdingBreath = false
    private val verseMarks = ArrayList<VerseMark>()
    private val audioLock = Any()
    private var speakToken = 0
    private var kokoroSize = KokoroFiles.FULL
    private var kokoroRun = KokoroFiles.CPU
    private var loadedKey: String? = null
    private val ahead = HashMap<Int, Deferred<Pair<FloatArray, Int>?>>()
    private var aheadRate = 0f
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
                    engineId = enginePkg
                    val sleepMinutes = intent.getIntExtra(EXTRA_SLEEP, 0)
                    kokoroSize = if (intent.getStringExtra(EXTRA_KOKORO_SIZE) == KokoroFiles.SMALL) KokoroFiles.SMALL else KokoroFiles.FULL
                    kokoroRun = if (intent.getStringExtra(EXTRA_KOKORO_RUN) == KokoroFiles.NNAPI) KokoroFiles.NNAPI else KokoroFiles.CPU
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
                if (lines.isEmpty()) return START_NOT_STICKY
                verses = lines
                val newTitle = intent.getStringExtra(EXTRA_TITLE)
                if (!newTitle.isNullOrBlank()) {
                    title = newTitle
                    ListenBus.title.value = newTitle
                }
                val resumeAt = intent.getIntExtra(EXTRA_INDEX, -1)
                if (resumeAt >= 0) {
                    verseIndex = resumeAt.coerceIn(0, lines.lastIndex)
                    playing = true
                    ListenBus.playing.value = true
                    ListenBus.error.value = false
                    acquire()
                    audioManager.requestAudioFocus(focusRequest)
                    if (!ready) prepare(engineId)
                    publishState()
                    if (ready) speak() else pending = true
                }
            }
            ACTION_RELOAD -> {
                kokoroSize = if (intent.getStringExtra(EXTRA_KOKORO_SIZE) == KokoroFiles.SMALL) KokoroFiles.SMALL else KokoroFiles.FULL
                kokoroRun = if (intent.getStringExtra(EXTRA_KOKORO_RUN) == KokoroFiles.NNAPI) KokoroFiles.NNAPI else KokoroFiles.CPU
                if (verses.isEmpty() || !playing) return START_NOT_STICKY
                verseIndex = if (useKokoro) heardIndex() else verseIndex
                useKokoro = engineId == KokoroFiles.ENGINE
                if (useKokoro) ready = KokoroFiles.ready(this, kokoroSize)
                if (useKokoro && !ready) {
                    ListenBus.error.value = true
                    pauseInternal()
                    return START_NOT_STICKY
                }
                if (ready || useKokoro) speak() else pending = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        playing = false
        speakToken += 1
        stopTrack()
        kokoro?.release()
        kokoro = null
        tts?.stop()
        tts?.shutdown()
        session.release()
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdown()
        super.onTaskRemoved(rootIntent)
    }

    private fun prepare(enginePkg: String) {
        useKokoro = enginePkg == KokoroFiles.ENGINE
        if (useKokoro) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = KokoroFiles.ready(this, kokoroSize)
            if (!ready) {
                ListenBus.error.value = true
                ListenBus.playing.value = false
                playing = false
            }
            return
        }
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
        if (useKokoro) {
            speakKokoro()
            return
        }
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
        val heard = if (useKokoro) heardIndex() else verseIndex
        playing = false
        pending = false
        speakToken += 1
        if (useKokoro) verseIndex = heard
        holdingBreath = false
        stopTrack()
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
        speakToken += 1
        holdingBreath = false
        stopTrack()
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

    private fun speakKokoro() {
        if (!playing) return
        if (verseIndex >= verses.size) {
            finishSpeech()
            return
        }
        val token = ++speakToken
        val speed = rate
        holdingBreath = false
        stopTrack()
        ListenBus.verse.value = verseIndex
        ListenBus.word.value = -1
        ListenBus.playing.value = true
        ListenBus.breath.value = false
        updateNotification()
        publishState()
        scope.launch { runKokoro(token, speed) }
    }

    private suspend fun runKokoro(token: Int, speed: Float) {
        var index = verseIndex
        while (token == speakToken && playing) {
            if (index >= verses.size) {
                awaitDrained(token)
                if (token != speakToken || !playing) return
                if (index >= verses.size) break
            }
            if (synchronized(audioLock) { framesQueued } > 0 && !gapBefore(token)) return
            queueAhead(index, speed, token)
            val clip = takeClip(index, speed, token) ?: run {
                if (token == speakToken) {
                    main.post {
                        ListenBus.error.value = true
                        pauseInternal()
                    }
                }
                return
            }
            if (token != speakToken || !playing) return
            val startFrame = synchronized(audioLock) { framesQueued }
            synchronized(audioLock) { verseMarks.add(VerseMark(startFrame, index)) }
            if (!writeSamples(clip.first, clip.second, token)) return
            verseIndex = index
            if (!walking && index + 1 < verses.size) awaitNearEnd(token, 280)
            index += 1
            verseIndex = index
        }
        if (token != speakToken || !playing) return
        stopTrack()
        main.post {
            if (token == speakToken && playing && verseIndex >= verses.size) finishSpeech()
        }
    }

    private suspend fun gapBefore(token: Int): Boolean {
        if (walking) {
            awaitDrained(token)
            if (token != speakToken || !playing) return false
            holdingBreath = true
            main.post {
                if (token == speakToken && playing) {
                    ListenBus.breath.value = true
                    ListenBus.word.value = -1
                }
            }
            delay(1600)
            holdingBreath = false
            if (token != speakToken || !playing) return false
            main.post { if (token == speakToken) ListenBus.breath.value = false }
            return true
        }
        return writeSilence(120, token)
    }

    private fun kokoroEngine(): OfflineTts {
        val key = "$kokoroSize|$kokoroRun"
        if (kokoro != null && loadedKey == key) return kokoro!!
        kokoro?.release()
        kokoro = null
        loadedKey = null
        val created = try {
            buildKokoro(kokoroRun)
        } catch (_: Exception) {
            if (kokoroRun == KokoroFiles.CPU) throw IllegalStateException("Kokoro didn't start.")
            kokoroRun = KokoroFiles.CPU
            KokoroFiles.noteChipFellBack()
            buildKokoro(KokoroFiles.CPU)
        }
        kokoro = created
        loadedKey = "$kokoroSize|$kokoroRun"
        return created
    }

    private fun buildKokoro(provider: String): OfflineTts {
        val root = KokoroFiles.root(this, kokoroSize) ?: error("Kokoro is not on this phone.")
        return OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = KokoroFiles.model(root).path,
                        voices = java.io.File(root, "voices.bin").path,
                        tokens = java.io.File(root, "tokens.txt").path,
                        dataDir = java.io.File(root, "espeak-ng-data").path,
                    ),
                    numThreads = 4,
                    provider = provider,
                ),
            ),
        )
    }

    private suspend fun synthesize(text: String, speed: Float): Pair<FloatArray, Int>? = kokoroLock.withLock {
        if (text.isBlank()) return null
        runCatching {
            val audio = kokoroEngine().generate(text, sid = 0, speed = speed)
            trimSilence(audio.samples, audio.sampleRate) to audio.sampleRate
        }.getOrNull()
    }

    private fun queueAhead(start: Int, speed: Float, token: Int) {
        if (start >= verses.size || token != speakToken) return
        if (aheadRate != speed) {
            dropAhead()
            aheadRate = speed
        }
        val keep = start..(start + 2).coerceAtMost(verses.lastIndex)
        ahead.keys.filter { it !in keep }.toList().forEach { ahead.remove(it)?.cancel() }
        for (index in keep) {
            val existing = ahead[index]
            if (existing != null && (existing.isActive || existing.isCompleted)) continue
            val text = verses[index]
            ahead[index] = scope.async {
                if (token != speakToken) null else synthesize(text, speed)
            }
        }
    }

    private suspend fun takeClip(index: Int, speed: Float, token: Int): Pair<FloatArray, Int>? {
        val pending = if (aheadRate == speed) ahead.remove(index) else null
        if (pending != null) {
            val readyClip = runCatching { pending.await() }.getOrNull()
            if (token != speakToken) return null
            if (readyClip != null) return readyClip
        }
        if (token != speakToken || index >= verses.size) return null
        return synthesize(verses[index], speed)
    }

    private fun ensureStream(sampleRate: Int): AudioTrack? {
        val existing = stream
        if (existing != null && streamRate == sampleRate && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }
        releaseStream()
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (min <= 0) return null
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) {
                publishHeard(track)
                armNextMarker(track)
            }

            override fun onPeriodicNotification(track: AudioTrack) {
                publishHeard(track)
            }
        }, main)
        player.positionNotificationPeriod = (sampleRate / 8).coerceAtLeast(1)
        if (tokenStill()) player.play()
        stream = player
        streamRate = sampleRate
        return player
    }

    private fun tokenStill(): Boolean = playing

    private fun publishHeard(player: AudioTrack) {
        if (!playing || holdingBreath) return
        val head = runCatching { player.playbackHeadPosition }.getOrDefault(0)
        val index = synchronized(audioLock) { verseMarks.lastOrNull { it.frame <= head }?.index } ?: return
        ListenBus.verse.value = index
        ListenBus.word.value = -1
    }

    private fun armNextMarker(player: AudioTrack) {
        val head = runCatching { player.playbackHeadPosition }.getOrDefault(0)
        val next = synchronized(audioLock) { verseMarks.firstOrNull { it.frame > head }?.frame } ?: return
        if (next > head) player.notificationMarkerPosition = next
    }

    private fun writeSamples(samples: FloatArray, sampleRate: Int, token: Int): Boolean {
        if (samples.isEmpty() || token != speakToken) return false
        val player = ensureStream(sampleRate) ?: return false
        var offset = 0
        while (offset < samples.size) {
            if (token != speakToken || !playing) return false
            val wrote = runCatching {
                player.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            }.getOrDefault(-1)
            if (wrote <= 0) return false
            offset += wrote
        }
        synchronized(audioLock) { framesQueued += samples.size }
        armNextMarker(player)
        return token == speakToken && playing
    }

    private fun writeSilence(ms: Int, token: Int): Boolean {
        val rate = streamRate.takeIf { it > 0 } ?: 24000
        val count = (rate * ms / 1000).coerceAtLeast(1)
        return writeSamples(FloatArray(count), rate, token)
    }

    private suspend fun awaitNearEnd(token: Int, leadMs: Int) {
        awaitPlayback(token, leadMs)
    }

    private suspend fun awaitDrained(token: Int) {
        awaitPlayback(token, 0)
    }

    private suspend fun awaitPlayback(token: Int, leadMs: Int) {
        val rate = streamRate
        if (rate <= 0) return
        val leadFrames = rate * leadMs / 1000
        val queuedNow = synchronized(audioLock) { framesQueued }
        val headNow = synchronized(audioLock) {
            stream?.let { runCatching { it.playbackHeadPosition }.getOrDefault(0) } ?: return
        }
        val remainMs = ((queuedNow - headNow).coerceAtLeast(0) * 1000L / rate) - leadMs
        val deadline = System.currentTimeMillis() + remainMs.coerceAtLeast(0L) + 400L
        while (token == speakToken && playing && System.currentTimeMillis() < deadline) {
            val (head, queued) = synchronized(audioLock) {
                val track = stream ?: return
                val heard = runCatching { track.playbackHeadPosition }.getOrDefault(framesQueued)
                heard to framesQueued
            }
            if (head >= queued - leadFrames) return
            delay(30)
        }
    }

    private fun heardIndex(): Int {
        val player = stream ?: return verseIndex
        val head = runCatching { player.playbackHeadPosition }.getOrDefault(0)
        return synchronized(audioLock) { verseMarks.lastOrNull { it.frame <= head }?.index } ?: verseIndex
    }

    private fun stopTrack() {
        dropAhead()
        releaseStream()
    }

    private fun dropAhead() {
        ahead.values.forEach { it.cancel() }
        ahead.clear()
        aheadRate = 0f
    }

    private fun releaseStream() {
        val player = synchronized(audioLock) {
            framesQueued = 0
            verseMarks.clear()
            val current = stream
            stream = null
            current
        }
        if (player == null) return
        runCatching {
            player.setPlaybackPositionUpdateListener(null)
            player.pause()
            player.flush()
            player.release()
        }
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
        const val ACTION_RELOAD = "app.sparkbible.RELOAD"
        const val EXTRA_LINES = "lines"
        const val EXTRA_RATE = "rate"
        const val EXTRA_WALKING = "walking"
        const val EXTRA_TITLE = "title"
        const val EXTRA_INDEX = "index"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SLEEP = "sleep"
        const val EXTRA_KOKORO_SIZE = "kokoroSize"
        const val EXTRA_KOKORO_RUN = "kokoroRun"
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
            kokoroSize: String = KokoroFiles.FULL,
            kokoroRun: String = KokoroFiles.CPU,
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
                putExtra(EXTRA_KOKORO_SIZE, kokoroSize)
                putExtra(EXTRA_KOKORO_RUN, kokoroRun)
            }
            context.startForegroundService(intent)
        }

        fun reload(context: Context, kokoroSize: String, kokoroRun: String) {
            context.startService(
                Intent(context, ListenService::class.java)
                    .setAction(ACTION_RELOAD)
                    .putExtra(EXTRA_KOKORO_SIZE, kokoroSize)
                    .putExtra(EXTRA_KOKORO_RUN, kokoroRun),
            )
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

        fun extend(context: Context, lines: List<String>, title: String, resumeAt: Int) {
            context.startService(
                Intent(context, ListenService::class.java)
                    .setAction(ACTION_EXTEND)
                    .putStringArrayListExtra(EXTRA_LINES, ArrayList(lines))
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_INDEX, resumeAt),
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

private fun trimSilence(samples: FloatArray, sampleRate: Int): FloatArray {
    if (samples.size < 8) return samples
    val threshold = 0.012f
    var start = 0
    var end = samples.lastIndex
    while (start < end && abs(samples[start]) < threshold) start++
    while (end > start && abs(samples[end]) < threshold) end--
    if (end - start < sampleRate / 20) return samples
    val pad = sampleRate / 50
    val from = (start - pad).coerceAtLeast(0)
    val to = (end + pad).coerceAtMost(samples.lastIndex)
    return if (from == 0 && to == samples.lastIndex) samples else samples.copyOfRange(from, to + 1)
}
