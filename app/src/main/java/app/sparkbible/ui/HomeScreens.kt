package app.sparkbible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sparkbible.Screen
import app.sparkbible.SparkViewModel
import app.sparkbible.data.Energy
import app.sparkbible.data.Logic
import app.sparkbible.data.Mode
import app.sparkbible.data.MomentEntity
import app.sparkbible.data.Origin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: SparkViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val today = LocalDate.now()
    val hour = java.time.LocalTime.now().hour
    val daily = Logic.daily(today)
    val bookName = vm.books.collectAsStateWithLifecycle().value.getOrNull(daily.book)?.name ?: "Today"
    val dailyLabel = if (daily.start == daily.end) "$bookName ${daily.chapter}:${daily.start}"
    else "$bookName ${daily.chapter}:${daily.start}–${daily.end}"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Spark Bible", color = palette.ember, fontSize = 13.sp, letterSpacing = 1.2.sp)
        Text(Logic.greeting(hour), style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(Logic.supportLine(today), color = palette.muted, fontSize = 16.sp)
        if (!settings.seenIntro) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.sageSoft)
                    .padding(16.dp),
            ) {
                Text("This isn't a streak app.", fontWeight = FontWeight.SemiBold, color = palette.ink)
                Text(
                    "Leave anytime. The garden keeps whatever you give it. Missed days stay kind.",
                    color = palette.ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Got it",
                    color = palette.sage,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable { vm.updateSettings { it.copy(seenIntro = true) } }
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (progress.openBook >= 0) {
            SoftCard {
                Text("Still open", color = palette.ember, fontSize = 12.sp)
                Text(progress.openLabel.ifBlank { "A passage" }, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Nothing is lost. Come back whenever.", color = palette.muted, modifier = Modifier.padding(top = 4.dp))
                PrimaryButton("Return to it", Modifier.padding(top = 12.dp)) { vm.resume() }
            }
        }
        Text("How's your focus right now?", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        EnergyButton("Scattered", "One verse. About a minute.", Energy.LOW, busy, vm)
        EnergyButton("Steady", "A short passage. Easy to finish.", Energy.MEDIUM, busy, vm)
        EnergyButton("Locked in", "A longer stretch, if you want it.", Energy.HIGH, busy, vm)
        if (error != null) Text(error ?: "", color = palette.ember)
        SoftCard {
            Text("DAILY SPARK", color = palette.ember, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(dailyLabel, color = palette.sage, modifier = Modifier.padding(top = 6.dp))
            Text(daily.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
            val done = progress.sparkDoneOn == today.toString()
            Text(
                if (done) "You already sat with this. Opening it again still counts." else "A small piece, picked for today. No catching up.",
                color = palette.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            PrimaryButton(if (done) "Open it again" else "Sit with this", Modifier.padding(top = 12.dp)) {
                vm.openDaily()
            }
        }
        WeekBlock(moments, settings.weeklyAim)
        Text(
            "${app.sparkbible.data.Translations.get(settings.translation).name} · public domain. Any amount counts.",
            color = palette.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun EnergyButton(title: String, body: String, energy: Energy, busy: Boolean, vm: SparkViewModel) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(22.dp))
            .clickable(enabled = !busy) { vm.openSetup(energy, Origin.Trail) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = palette.ink)
        Text(body, color = palette.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SetupScreen(vm: SparkViewModel, screen: Screen.Setup) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val place = draft
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Back", color = palette.sage, modifier = Modifier.clickable { vm.back() }.padding(vertical = 8.dp))
        Text(
            when (screen.energy) {
                Energy.LOW -> "Scattered"
                Energy.MEDIUM -> "Steady"
                Energy.HIGH -> "Locked in"
            },
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        if (place == null) {
            Text(if (busy) "Opening a short passage…" else "One moment.", color = palette.muted)
        } else {
            Text(place.label, color = palette.sage, fontSize = 16.sp)
            Text(place.verses.first().text, color = palette.ink, maxLines = 4)
            val words = place.verses.sumOf { it.text.split(Regex("\\s+")).size }
            val mins = (words / 170.0).roundToInt().coerceAtLeast(1)
            Text(
                "About $mins minute${if (mins == 1) "" else "s"}. You can stop whenever.",
                color = palette.muted,
            )
            if (place.truncated) {
                Text("This chapter is long, so here's a solid piece. The rest can wait.", color = palette.muted)
            }
        }
        if (!Logic.locksLength(screen.origin)) {
            Text("Size", color = palette.muted, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Energy.entries.forEach { energy ->
                    val on = screen.energy == energy
                    val label = when (energy) {
                        Energy.LOW -> "One verse"
                        Energy.MEDIUM -> "Short"
                        Energy.HIGH -> "Longer"
                    }
                    ChoiceChip(label, on) { vm.setEnergy(energy) }
                }
            }
        }
        Text("How should it arrive?", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ModeButton("Read", "Just the words.") { vm.begin(Mode.READ) }
        ModeButton("Listen", "I'll read it to you. You can leave the app.") { vm.begin(Mode.LISTEN) }
        ModeButton("Both", "Words on screen, voice alongside.") { vm.begin(Mode.BOTH) }
    }
}

@Composable
private fun ModeButton(title: String, body: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.sage)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(title, color = palette.onSage, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(body, color = palette.onSage.copy(alpha = 0.86f))
    }
}

@Composable
fun ReflectScreen(vm: SparkViewModel, screen: Screen.Reflect) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(screen.line, style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(screen.place.label, color = palette.sage)
        Text("A new sprout is in your garden. It will not wilt.", color = palette.muted)
        SoftCard {
            Text(screen.place.prompt, fontSize = 18.sp, color = palette.ink)
        }
        if (screen.place.energy == Energy.HIGH) {
            Text(
                "Only if you're still in it: what's one thing this changes about the next hour, not your whole life?",
                color = palette.muted,
            )
        }
        PrimaryButton("I sat with it") { vm.home() }
        Text(
            "Skip the question",
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { vm.home() }.padding(12.dp),
        )
    }
}

@Composable
fun WeekBlock(moments: List<MomentEntity>, aim: Int) {
    val palette = LocalPalette.current
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val count = moments.count { moment ->
        val day = Instant.ofEpochMilli(moment.at).atZone(ZoneId.systemDefault()).toLocalDate()
        !day.isBefore(monday) && !day.isAfter(monday.plusDays(6))
    }
    val line = Logic.weekLine(count, aim)
    SoftCard {
        Text("This week", color = palette.ember, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { index, day ->
                val on = moments.any {
                    Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate() == day
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (on) palette.sage else palette.bg)
                            .border(1.5.dp, if (on) palette.sage else palette.line, CircleShape),
                    )
                    Text(labels[index], color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(line, color = palette.ink, modifier = Modifier.padding(top = 12.dp))
        Text("Rest days stay open. They don't undo anything.", color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SoftCard(body: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) { body() }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        text,
        color = palette.onSage,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.sage)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        text,
        color = if (selected) palette.onSage else palette.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.sage else palette.surface)
            .border(1.dp, if (selected) palette.sage else palette.line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
