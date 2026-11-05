package me.timeto.shared

data class SystemInfo(
    val build: Int,
    val version: String,
    val osVersion: String,
    val device: String,
    val flavor: String?,
) {

    companion object {

        lateinit var instance: SystemInfo
    }

    val osFullVersion: String = "android-$osVersion"
}
