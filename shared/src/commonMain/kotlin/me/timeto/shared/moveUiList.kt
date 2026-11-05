package me.timeto.shared

fun <T> List<T>.moveUiListAndroid(
    fromIdx: Int,
    toIdx: Int,
    action: suspend (List<T>) -> Unit,
) {
    val oldList: List<T> = this
    val fromItem: T = oldList[fromIdx]
    val toItem: T = oldList[toIdx]
    val newItems: MutableList<T> = this.toMutableList()
    newItems[fromIdx] = toItem
    newItems[toIdx] = fromItem
    launchExIo {
        action(newItems)
    }
}
