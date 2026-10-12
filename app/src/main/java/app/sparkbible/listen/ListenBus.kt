package app.sparkbible.listen

import kotlinx.coroutines.flow.MutableStateFlow

object ListenBus {
    val playing = MutableStateFlow(false)
    val verse = MutableStateFlow(0)
    val word = MutableStateFlow(-1)
    val breath = MutableStateFlow(false)
    val title = MutableStateFlow("")
    val error = MutableStateFlow(false)
    val counted = MutableStateFlow("")
}
