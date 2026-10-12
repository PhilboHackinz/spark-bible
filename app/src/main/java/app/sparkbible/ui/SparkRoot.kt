package app.sparkbible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sparkbible.Screen
import app.sparkbible.SparkViewModel
import app.sparkbible.listen.ListenBus
import app.sparkbible.listen.ListenService

@Composable
fun SparkRoot(vm: SparkViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val screen = stack.last()
    val playing by ListenBus.playing.collectAsStateWithLifecycle()
    val title by ListenBus.title.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = paletteFor(settings.theme)

    SparkTheme(settings) {
        val hideBar = screen is Screen.Setup || screen is Screen.Read || screen is Screen.Reflect
        BackHandler(enabled = screen is Screen.Reflect || stack.size > 1) {
            when (screen) {
                is Screen.Reflect -> vm.home()
                else -> vm.back()
            }
        }
        Scaffold(
            containerColor = palette.bg,
            bottomBar = {
                if (!hideBar) {
                    Column {
                        if (playing && screen !is Screen.Read) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    title.ifBlank { "Listening" },
                                    modifier = Modifier.weight(1f),
                                    color = palette.ink,
                                    maxLines = 1,
                                )
                                TextButton(onClick = { ListenService.toggle(context) }) { Text("Pause") }
                                TextButton(onClick = { vm.resume() }) { Text("Open") }
                            }
                        }
                        NavigationBar(containerColor = palette.surface) {
                            val items = listOf(
                                Triple(Screen.Today, "Today", Icons.Filled.Home),
                                Triple(Screen.Sparks, "Sparks", Icons.Filled.Star),
                                Triple(Screen.Garden, "Garden", Icons.Filled.Spa),
                                Triple(Screen.More, "More", Icons.Filled.Menu),
                            )
                            items.forEach { (dest, label, icon) ->
                                val selected = when (dest) {
                                    Screen.Today -> screen is Screen.Today
                                    Screen.Sparks -> screen is Screen.Sparks || screen is Screen.Topic
                                    Screen.Garden -> screen is Screen.Garden
                                    else -> screen is Screen.More || screen is Screen.Library ||
                                        screen is Screen.Chapters || screen is Screen.Saved || screen is Screen.Settings
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { vm.tab(dest) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = palette.onSage,
                                        selectedTextColor = palette.sage,
                                        indicatorColor = palette.sage,
                                        unselectedIconColor = palette.muted,
                                        unselectedTextColor = palette.muted,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.Today -> TodayScreen(vm)
                    Screen.Sparks -> SparksScreen(vm)
                    Screen.Garden -> GardenScreen(vm)
                    Screen.More -> MoreScreen(vm)
                    Screen.Library -> LibraryScreen(vm)
                    is Screen.Chapters -> ChaptersScreen(vm, screen.book)
                    Screen.Saved -> SavedScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.Topic -> TopicScreen(vm, screen.topicId)
                    is Screen.Setup -> SetupScreen(vm, screen)
                    is Screen.Read -> ReaderScreen(vm, screen.place)
                    is Screen.Reflect -> ReflectScreen(vm, screen)
                }
            }
        }
    }
}
