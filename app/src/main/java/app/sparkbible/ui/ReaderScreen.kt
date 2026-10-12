package app.sparkbible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sparkbible.SparkViewModel
import app.sparkbible.data.Energy
import app.sparkbible.data.Mode
import app.sparkbible.data.PassageVerse
import app.sparkbible.data.Place
import app.sparkbible.data.ThemePref
import app.sparkbible.listen.Ambient
import app.sparkbible.listen.ListenBus
import app.sparkbible.listen.ListenService

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(vm: SparkViewModel, place: Place) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val context = LocalContext.current
    val playing by ListenBus.playing.collectAsStateWithLifecycle()
    val spokenVerse by ListenBus.verse.collectAsStateWithLifecycle()
    val spokenWord by ListenBus.word.collectAsStateWithLifecycle()
    val breath by ListenBus.breath.collectAsStateWithLifecycle()
    val title by ListenBus.title.collectAsStateWithLifecycle()
    val speechError by ListenBus.error.collectAsStateWithLifecycle()
    val counted by ListenBus.counted.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var tools by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable(place.label) { mutableStateOf(false) }
    val active = title == place.label
    val style = readingStyle(settings)

    LaunchedEffect(place.label) {
        if (!started && place.mode != Mode.READ) {
            started = true
            ListenService.play(
                context,
                place.verses.map { it.text },
                0,
                settings.speechRate,
                settings.walking,
                place.label,
                settings.voiceEngine,
                settings.voiceName,
            )
        }
    }
    DisposableEffect(place.label, settings.ambient) {
        if (settings.ambient) Ambient.set(true)
        onDispose { if (!ListenBus.playing.value) Ambient.set(false) }
    }
    LaunchedEffect(counted) {
        if (counted.isNotBlank() && counted == place.label) vm.showReflect(place)
    }
    LaunchedEffect(spokenVerse, playing) {
        if (active && playing) {
            listState.animateScrollToItem(spokenVerse.coerceIn(0, place.verses.lastIndex))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Leave it open",
            color = palette.sage,
            modifier = Modifier
                .clickable { vm.back() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(place.label, color = palette.sage)
            Text(app.sparkbible.data.Translations.get(settings.translation).name, color = palette.muted, fontSize = 13.sp)
            val sizeLine = when (place.energy) {
                Energy.LOW -> "One verse · stop whenever"
                Energy.MEDIUM -> "A short passage · stop whenever"
                Energy.HIGH -> "A longer stretch · stop whenever"
            }
            Text(sizeLine, color = palette.muted)
            if (place.truncated) Text("A long chapter, so this is a piece of it.", color = palette.muted)
        }
        if (active && breath) {
            Text(
                "One breath. Then the next line.",
                color = palette.ember,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (speechError) {
            Text(
                "Listening isn't available on this device. Reading still works.",
                color = palette.ember,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(place.verses, key = { _, verse ->
                "${verse.pin.book}:${verse.pin.chapter}:${verse.pin.verse}"
            }) { index, verse ->
                val id = "${verse.pin.book}:${verse.pin.chapter}:${verse.pin.verse}"
                val kept = highlights.find { it.id == id }
                VerseBlock(
                    verse = verse,
                    kept = kept != null,
                    note = kept?.note.orEmpty(),
                    bionic = settings.bionic,
                    style = style,
                    highlightWord = if (active && index == spokenVerse) spokenWord else -1,
                    onToggle = { vm.toggleHighlight(verse) },
                    onNote = { vm.saveNote(verse, it) },
                )
            }
            item { Text("Tap a verse number to keep it. Only if you want to.", color = palette.muted) }
        }
        Column(
            Modifier.fillMaxWidth().background(palette.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tools) {
                ToolSwitch("Bionic reading", settings.bionic) { on -> vm.updateSettings { it.copy(bionic = on) } }
                ToolSwitch("Dyslexia-friendly type", settings.dyslexia) { on -> vm.updateSettings { it.copy(dyslexia = on) } }
                ToolSwitch("Soft noise", settings.ambient) { on ->
                    vm.updateSettings { it.copy(ambient = on) }
                    Ambient.set(on)
                }
                ToolSwitch("Walking pauses", settings.walking) { on -> vm.updateSettings { it.copy(walking = on) } }
                Text("Theme", color = palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePref.entries.forEach { pref ->
                        ChoiceChip(
                            pref.name.lowercase().replaceFirstChar { it.titlecase() },
                            settings.theme == pref,
                        ) { vm.updateSettings { it.copy(theme = pref) } }
                    }
                }
                Text("Voice speed", color = palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f to "Slower", 1f to "Natural", 1.15f to "Quicker").forEach { (rate, label) ->
                        ChoiceChip(label, settings.speechRate == rate) {
                            vm.updateSettings { it.copy(speechRate = rate) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.updateSettings { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(17f)) } }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Smaller text", tint = palette.ink)
                }
                IconButton(onClick = {
                    vm.updateSettings { it.copy(fontSize = (it.fontSize + 1f).coerceAtMost(32f)) }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Larger text", tint = palette.ink)
                }
                Text(
                    if (tools) "Hide tools" else "Tools",
                    color = palette.sage,
                    modifier = Modifier
                        .clickable { tools = !tools }
                        .padding(12.dp),
                )
                Spacer(Modifier.weight(1f))
                val isPlaying = playing && active
                IconButton(onClick = {
                    if (isPlaying) ListenService.toggle(context)
                    else ListenService.play(
                        context,
                        place.verses.map { it.text },
                        if (active) spokenVerse else 0,
                        settings.speechRate,
                        settings.walking,
                        place.label,
                        settings.voiceEngine,
                        settings.voiceName,
                    )
                }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Listen",
                        tint = palette.sage,
                    )
                }
            }
            PrimaryButton("That's enough") {
                Ambient.set(false)
                vm.finish(place)
            }
        }
    }
}

@Composable
private fun ToolSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = palette.ink)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.sage, checkedThumbColor = palette.onSage),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseBlock(
    verse: PassageVerse,
    kept: Boolean,
    note: String,
    bionic: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    highlightWord: Int,
    onToggle: () -> Unit,
    onNote: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var draft by remember(verse.pin, note) { mutableStateOf(note) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                verse.pin.verse.toString(),
                color = if (kept) palette.onSage else palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(if (kept) palette.sage else palette.sageSoft, RoundedCornerShape(14.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            val words = verse.text.split(Regex("\\s+"))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                words.forEachIndexed { index, word ->
                    val bg = if (index == highlightWord) palette.emberSoft else Color.Transparent
                    val annotated = if (bionic && word.length >= 4) {
                        val cut = (word.length * 0.4f).toInt().coerceAtLeast(1)
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(word.take(cut)) }
                            append(word.drop(cut))
                        }
                    } else {
                        buildAnnotatedString { append(word) }
                    }
                    Text(annotated, style = style, modifier = Modifier.background(bg, RoundedCornerShape(4.dp)))
                }
            }
        }
        if (kept) {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onNote(it)
                },
                textStyle = style.copy(fontSize = style.fontSize * 0.85f),
                cursorBrush = SolidColor(palette.sage),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.sageSoft, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("A note, if you want one.", color = palette.muted)
                    inner()
                },
            )
        }
    }
}
