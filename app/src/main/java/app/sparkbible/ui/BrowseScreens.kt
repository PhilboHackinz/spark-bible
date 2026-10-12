package app.sparkbible.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sparkbible.SparkViewModel
import app.sparkbible.data.Energy
import app.sparkbible.data.Logic
import app.sparkbible.data.Origin
import app.sparkbible.data.ProgressStyle
import app.sparkbible.data.SparkCatalog
import app.sparkbible.data.ThemePref
import app.sparkbible.data.Translations
import app.sparkbible.listen.ListenService
import app.sparkbible.listen.OpenVoices
import app.sparkbible.listen.VoiceProbe
import app.sparkbible.listen.VoiceSnapshot
import app.sparkbible.widget.SparkWidget

@Composable
fun MoreScreen(vm: SparkViewModel) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("More", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text("Library, saved lines, and settings. Nothing here is a test.", color = palette.muted)
        MoreRow("Library", "Jump to a book, chapter, or reference.") { vm.push(app.sparkbible.Screen.Library) }
        MoreRow("Saved verses", "Lines you chose to keep.") { vm.push(app.sparkbible.Screen.Saved) }
        MoreRow("Settings", "Type, sound, reminders, garden.") { vm.push(app.sparkbible.Screen.Settings) }
    }
}

@Composable
private fun MoreRow(title: String, body: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(22.dp))
            .border(1.dp, palette.line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = palette.ink)
        Text(body, color = palette.muted)
    }
}

@Composable
fun SparksScreen(vm: SparkViewModel) {
    val palette = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Sparks", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 18.dp))
            Text(
                "Short passages for a mood, not a curriculum. Surprise yourself if choosing feels hard.",
                color = palette.muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            PrimaryButton("Surprise me") {
                val spark = SparkCatalog.sparks.random()
                vm.openSetup(Energy.MEDIUM, Origin.SparkItem(spark.id))
            }
        }
        items(SparkCatalog.topics, key = { it.id }) { topic ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, palette.line, RoundedCornerShape(20.dp))
                    .clickable { vm.push(app.sparkbible.Screen.Topic(topic.id)) }
                    .padding(16.dp),
            ) {
                Text(topic.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = palette.ink)
                Text(topic.blurb, color = palette.muted)
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
    }
}

@Composable
fun TopicScreen(vm: SparkViewModel, topicId: String) {
    val palette = LocalPalette.current
    val topic = SparkCatalog.topics.first { it.id == topicId }
    val sparks = SparkCatalog.sparks.filter { it.topicId == topicId }
    val books by vm.books.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(vertical = 14.dp))
            Text(topic.name, style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
            Text(topic.blurb, color = palette.muted, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(sparks, key = { it.id }) { spark ->
            val name = books.getOrNull(spark.book)?.name ?: "Passage"
            val ref = if (spark.start == spark.end) "$name ${spark.chapter}:${spark.start}"
            else "$name ${spark.chapter}:${spark.start}–${spark.end}"
            Column(
                Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, palette.line, RoundedCornerShape(20.dp))
                    .clickable { vm.openSetup(Energy.MEDIUM, Origin.SparkItem(spark.id)) }
                    .padding(16.dp),
            ) {
                Text(ref, color = palette.sage, fontSize = 13.sp)
                Text(spark.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = palette.ink)
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: SparkViewModel) {
    val palette = LocalPalette.current
    val books by vm.books.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val jump = Logic.parse(query, books)
    val filtered = books.filter { it.name.contains(query.trim(), ignoreCase = true) || query.isBlank() }
    Column(Modifier.fillMaxSize()) {
        Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(horizontal = 20.dp, vertical = 14.dp))
        Text("Library", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 20.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.sage),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(16.dp))
                .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                .padding(14.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Jump to John 3:16 or a book name", color = palette.muted)
                inner()
            },
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            if (jump != null) {
                val name = books.getOrNull(jump.book)?.name ?: "Passage"
                item {
                    Text(
                        "Go to $name ${jump.chapter}:${jump.verse}",
                        color = palette.onSage,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .fillMaxWidth()
                            .background(palette.sage, RoundedCornerShape(16.dp))
                            .clickable { vm.openSetup(Energy.MEDIUM, Origin.Jump(jump)) }
                            .padding(16.dp),
                    )
                }
            }
            val ot = filtered.filter { it.testament == "ot" }
            val nt = filtered.filter { it.testament == "nt" }
            if (nt.isNotEmpty()) {
                item { Text("New Testament", color = palette.ember, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(nt, key = { it.i }) { book ->
                BookRow(book.name, book.chapters) { vm.push(app.sparkbible.Screen.Chapters(book.i)) }
            }
            if (ot.isNotEmpty()) {
                item { Text("Hebrew Scriptures", color = palette.ember, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(ot, key = { it.i }) { book ->
                BookRow(book.name, book.chapters) { vm.push(app.sparkbible.Screen.Chapters(book.i)) }
            }
        }
    }
}

@Composable
private fun BookRow(name: String, chapters: Int, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 18.sp, color = palette.ink)
        Text("$chapters", color = palette.muted)
    }
}

@Composable
fun ChaptersScreen(vm: SparkViewModel, book: Int) {
    val palette = LocalPalette.current
    val books by vm.books.collectAsStateWithLifecycle()
    val info = books.getOrNull(book)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(bottom = 8.dp))
        Text(info?.name ?: "Book", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text("Pick a chapter. You can stop in the middle. That still counts.", color = palette.muted, modifier = Modifier.padding(bottom = 12.dp))
        if (info != null) {
            LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..info.chapters).toList()) { chapter ->
                    Text(
                        chapter.toString(),
                        color = palette.ink,
                        modifier = Modifier
                            .background(palette.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, palette.line, RoundedCornerShape(14.dp))
                            .clickable { vm.openSetup(Energy.HIGH, Origin.Jump(app.sparkbible.data.VersePin(book, chapter, 1))) }
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SavedScreen(vm: SparkViewModel) {
    val palette = LocalPalette.current
    val saved by vm.highlights.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(vertical = 14.dp))
            Text("Saved", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        }
        if (saved.isEmpty()) {
            item {
                Text(
                    "Nothing kept yet. Tap a verse number while you read — only if you want to.",
                    color = palette.muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(saved, key = { it.id }) { item ->
            val name = books.getOrNull(item.book)?.name ?: "Verse"
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, palette.line, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "$name ${item.chapter}:${item.verse}",
                    color = palette.sage,
                    modifier = Modifier.clickable {
                        vm.openSetup(Energy.LOW, Origin.Jump(app.sparkbible.data.VersePin(item.book, item.chapter, item.verse)))
                    },
                )
                Text(item.text, color = palette.ink, modifier = Modifier.padding(top = 6.dp))
                if (item.note.isNotBlank()) Text(item.note, color = palette.muted, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Remove",
                    color = palette.ember,
                    modifier = Modifier.clickable { vm.deleteHighlight(item.id) }.padding(top = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SparkViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(top = 14.dp))
            Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
            Text("Make the words easier to stay with. Nothing here is required.", color = palette.muted)
        }
        item {
            Text("Reading size ${settings.fontSize.toInt()}", color = palette.ink)
            Slider(
                value = settings.fontSize,
                onValueChange = { value -> vm.updateSettings { it.copy(fontSize = value) } },
                valueRange = 17f..32f,
            )
            Text("Line spacing", color = palette.ink)
            Slider(
                value = settings.lineHeight,
                onValueChange = { value -> vm.updateSettings { it.copy(lineHeight = value) } },
                valueRange = 1.4f..2f,
            )
        }
        item {
            Text("Theme", color = palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePref.entries.forEach { pref ->
                    ChoiceChip(pref.name.lowercase().replaceFirstChar { it.titlecase() }, settings.theme == pref) {
                        vm.updateSettings { it.copy(theme = pref) }
                    }
                }
            }
        }
        item {
            SettingSwitch("Bionic reading", settings.bionic) { on -> vm.updateSettings { it.copy(bionic = on) } }
            SettingSwitch("Dyslexia-friendly font", settings.dyslexia) { on -> vm.updateSettings { it.copy(dyslexia = on) } }
            SettingSwitch("Soft noise while reading", settings.ambient) { on -> vm.updateSettings { it.copy(ambient = on) } }
            SettingSwitch("Pause between lines when walking", settings.walking) { on -> vm.updateSettings { it.copy(walking = on) } }
        }
        item {
            Text("Translation", color = palette.muted)
            Text(
                "All five are public domain and already on this phone. Switching never resets your place.",
                color = palette.muted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Translations.all.forEach { translation ->
                    SelectRow(
                        title = translation.name,
                        body = translation.note,
                        selected = settings.translation == translation.id,
                    ) { vm.setTranslation(translation.id) }
                }
            }
        }
        item {
            Text("Voice speed", color = palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "Slower", 1f to "Natural", 1.15f to "Quicker").forEach { (rate, label) ->
                    ChoiceChip(label, settings.speechRate == rate) { vm.updateSettings { it.copy(speechRate = rate) } }
                }
            }
        }
        item {
            VoiceSection(settings.voiceEngine, settings.voiceName, settings.speechRate) { engine, voice ->
                vm.updateSettings { it.copy(voiceEngine = engine, voiceName = voice) }
            }
        }
        item {
            Text("Progress", color = palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Garden", settings.progressStyle == ProgressStyle.GARDEN) {
                    vm.updateSettings { it.copy(progressStyle = ProgressStyle.GARDEN) }
                }
                ChoiceChip("Sky", settings.progressStyle == ProgressStyle.CONSTELLATION) {
                    vm.updateSettings { it.copy(progressStyle = ProgressStyle.CONSTELLATION) }
                }
            }
            Text("Soft weekly aim: ${settings.weeklyAim}", color = palette.ink, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Less", false) { vm.updateSettings { it.copy(weeklyAim = (it.weeklyAim - 1).coerceAtLeast(1)) } }
                ChoiceChip("More", false) { vm.updateSettings { it.copy(weeklyAim = (it.weeklyAim + 1).coerceAtMost(7)) } }
            }
            Text("A soft aim, not a contract. Missed days stay kind.", color = palette.muted)
        }
        item {
            SettingSwitch("Gentle reminder", settings.reminderOn) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.updateSettings { it.copy(reminderOn = on) }
            }
            Text("When", color = palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("07:30", "08:30", "12:30", "17:30", "21:00").forEach { time ->
                    ChoiceChip(time, settings.reminderTime == time) { vm.updateSettings { it.copy(reminderTime = time) } }
                }
            }
            Text("Reminders never scold you for a missed day.", color = palette.muted)
        }
        item {
            PrimaryButton("Add home screen spark") {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, SparkWidget::class.java)
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(component, null, null)
                }
            }
            Text(
                "Scripture stays on this phone. The five translations above are public domain. Your place, highlights, and garden stay here too.",
                color = palette.muted,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun VoiceSection(
    engine: String,
    voice: String,
    rate: Float,
    onPick: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var snapshot by remember { mutableStateOf(VoiceSnapshot()) }
    var look by remember { mutableStateOf(0) }
    DisposableEffect(look) {
        val probe = VoiceProbe(context) { snapshot = it }
        probe.start()
        onDispose { probe.stop() }
    }
    Text("Voice", color = palette.muted)
    Text(
        "Tap Hear on a row to try it. The name is the voice. Network and offline copies of the same voice are one row.",
        color = palette.muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceRow(
            title = "Phone default",
            body = "Whatever Android is set to use.",
            selected = engine.isBlank() && voice.isBlank(),
            onSelect = { onPick("", "") },
            onHear = { hear(context, rate, "", "") },
        )
        if (!snapshot.ready) {
            Text("Looking for voices…", color = palette.muted)
        }
        snapshot.engines.forEach { group ->
            if (group.voices.isEmpty()) return@forEach
            Text(
                if (group.openSource) "${group.label} · open source" else shortEngineLabel(group.label),
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            group.voices.groupBy { it.accent }.forEach { (accent, voices) ->
                Text(accent, color = palette.muted, modifier = Modifier.padding(top = 2.dp))
                voices.forEach { item ->
                    VoiceRow(
                        title = item.label,
                        body = item.detail,
                        selected = engine == item.engine && (voice == item.name || voice in item.aliases),
                        onSelect = { onPick(item.engine, item.name) },
                        onHear = { hear(context, rate, item.engine, item.name) },
                    )
                }
            }
        }
        Text("Open-source engines", color = palette.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Spark can't ship their recordings inside the install file. They're free, they stay on the phone, and once Android enables them their voices join the list above.",
            color = palette.muted,
        )
        snapshot.offers.forEach { offer ->
            val status = when {
                offer.enabled -> "On this phone. Its English voices are in the list above."
                offer.installed -> "Installed, but Android hasn't enabled it yet."
                else -> offer.engine.blurb
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Text(offer.engine.name, fontWeight = FontWeight.SemiBold, color = palette.ink)
                Text(status, color = palette.muted, modifier = Modifier.padding(top = 4.dp))
                if (!offer.enabled) {
                    Text(
                        if (offer.installed) "Enable speech engine" else "Install ${offer.engine.name}",
                        color = palette.sage,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                if (offer.installed) OpenVoices.openTtsSettings(context)
                                else OpenVoices.openStore(context, offer.engine.pkg)
                            }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            "Look again",
            color = palette.sage,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { look += 1 }
                .padding(vertical = 8.dp),
        )
    }
}

private fun shortEngineLabel(label: String): String =
    if (label.contains("Google", ignoreCase = true)) "Google" else label

private fun hear(context: android.content.Context, rate: Float, engine: String, voice: String) {
    ListenService.play(
        context,
        listOf("Peace I leave with you. My peace I give to you."),
        0,
        rate,
        false,
        "Voice sample",
        engine,
        voice,
    )
}

@Composable
private fun VoiceRow(
    title: String,
    body: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onHear: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.sageSoft else palette.surface)
            .border(1.dp, if (selected) palette.sage else palette.line, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .padding(vertical = 6.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = palette.ink)
            Text(body, color = palette.muted, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            "Hear",
            color = palette.onSage,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(palette.sage)
                .clickable(onClick = onHear)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SelectRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.sageSoft else palette.surface)
            .border(1.dp, if (selected) palette.sage else palette.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = palette.ink)
        Text(body, color = palette.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.ink, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.sage, checkedThumbColor = palette.onSage),
        )
    }
}
