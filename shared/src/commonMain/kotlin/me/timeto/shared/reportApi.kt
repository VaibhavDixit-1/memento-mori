package me.timeto.shared

/** Kept for platform code that still reports local errors; this never sends data. */
fun reportApi(message: String) = zlog(message)
