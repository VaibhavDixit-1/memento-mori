package me.timeto.shared.vm.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import me.timeto.shared.backups.AutoBackup
import me.timeto.shared.backups.Backup
import me.timeto.shared.Cache
import me.timeto.shared.DayStartOffsetUtils
import me.timeto.shared.HomeButtonSort
import me.timeto.shared.UnixTime
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asDayStartOffsetSeconds
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.launchExIo
import me.timeto.shared.reportApi
import me.timeto.shared.combine
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.KvDb.Companion.isZenModeEnabled
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.textFeatures
import me.timeto.shared.toTimerHintNote
import me.timeto.shared.vm.app.AppVm.Companion.backupStateFlow
import me.timeto.shared.vm.Vm

class SettingsVm : Vm<SettingsVm.State>() {

    data class DayStartOffsetListItem(
        val seconds: Int,
        val note: String,
    )

    data class State(
        val activitiesUi: List<ActivityUi>,
        val checklistsDb: List<ChecklistDb>,
        val shortcutsDb: List<ShortcutDb>,
        val noteFoldersDb: List<NoteFolderDb>,
        val isZenModeEnabled: Boolean,
        val dayStartSeconds: Int,
        val autoBackupTimeString: String,
    ) {

        val headerTitle = "timeto.me"
        val dayStartNote: String = dayStartSecondsToString(dayStartSeconds)
        val dayStartListItems = (-10..10).map { hour ->
            DayStartOffsetListItem(
                seconds = hour * 3_600,
                note = dayStartSecondsToString(hour * 3_600)
            )
        }
    }

    override val state = MutableStateFlow(
        State(
            activitiesUi = ActivityUi.buildList(Cache.activitiesDb),
            checklistsDb = Cache.checklistsDb,
            shortcutsDb = Cache.shortcutsDb,
            noteFoldersDb = Cache.noteFoldersDb,
            isZenModeEnabled = KvDb.KEY.ZEN_MODE_ENABLED.selectOrNullCached().isZenModeEnabled(),
            dayStartSeconds = DayStartOffsetUtils.getOffsetSecondsCached(),
            autoBackupTimeString = prepAutoBackupTimeString(AutoBackup.lastTimeCache.value),
        )
    )

    init {
        val scopeVm = scopeVm()
        combine(
            ActivityDb.selectAllFlow(),
            ChecklistDb.selectAscFlow(),
            ShortcutDb.selectAscFlow(),
            NoteFolderDb.selectAllSortedFlow(),
            KvDb.KEY.DAY_START_OFFSET_SECONDS.selectOrNullFlow(),
            AutoBackup.lastTimeCache,
        ) { activitiesDb: List<ActivityDb>,
            checklistsDb: List<ChecklistDb>,
            shortcutsDb: List<ShortcutDb>,
            noteFoldersDb: List<NoteFolderDb>,
            dayStartOffsetSeconds: KvDb?,
            autoBackupLastTime: UnixTime? ->
            state.update {
                it.copy(
                    activitiesUi = ActivityUi.buildList(activitiesDb),
                    checklistsDb = checklistsDb,
                    shortcutsDb = shortcutsDb,
                    noteFoldersDb = noteFoldersDb,
                    dayStartSeconds = dayStartOffsetSeconds.asDayStartOffsetSeconds(),
                    autoBackupTimeString = prepAutoBackupTimeString(autoBackupLastTime),
                )
            }
        }.launchIn(scopeVm)
    }

    fun startInterval(activityDb: ActivityDb, seconds: Int) {
        launchExIo {
            activityDb.startTimer(seconds = seconds)
        }
    }

    fun setDayStartOffsetSeconds(seconds: Int) {
        launchExIo {
            KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(seconds)
        }
    }

    fun setZenModeEnabled(enabled: Boolean) {
        state.update { it.copy(isZenModeEnabled = enabled) }
        launchExIo {
            KvDb.KEY.ZEN_MODE_ENABLED.upsertBoolean(enabled)
        }
    }

    fun procRestore(jString: String) {
        launchExIo {
            try {
                backupStateFlow.emit("Loading..")
                Backup.restore(jString)
                backupStateFlow.emit("Please restart the app.")
            } catch (e: Throwable) {
                backupStateFlow.emit("Error")
                reportApi("SettingsSheetVM.procRestore() error:\n$e")
            }
        }
    }

    fun prepBackupFileName(): String =
        Backup.prepFileName(UnixTime(), prefix = "timetome_")

    ///

    data class ActivityUi(
        val activityDb: ActivityDb,
        val nestedLevel: Int,
    ) {

        companion object {

            fun buildList(activitiesDb: List<ActivityDb>): List<ActivityUi> {
                val resList: MutableList<ActivityUi> = mutableListOf()
                val sortedActivitiesDb: List<ActivityDb> = activitiesDb.sortedWith { activityDb1, activityDb2 ->
                    val sort1: HomeButtonSort =
                        HomeButtonSort.parseOrNull(activityDb1.home_button_sort) ?: HomeButtonSort(0, 0, 0)
                    val sort2: HomeButtonSort =
                        HomeButtonSort.parseOrNull(activityDb2.home_button_sort) ?: HomeButtonSort(0, 0, 0)
                    if (sort1.rowIdx != sort2.rowIdx)
                        return@sortedWith if (sort1.rowIdx > sort2.rowIdx) 1 else -1
                    if (sort1.cellIdx > sort2.cellIdx) 1 else -1
                }

                fun addRecursive(activityDb: ActivityDb, nestedLevel: Int) {
                    resList.add(ActivityUi(activityDb = activityDb, nestedLevel = nestedLevel))
                    sortedActivitiesDb.filter { it.parent_id == activityDb.id }.forEach { childrenActivityDb ->
                        addRecursive(childrenActivityDb, nestedLevel + 1)
                    }
                }
                sortedActivitiesDb.filter { it.parent_id == null }.forEach { activityDb ->
                    addRecursive(activityDb, nestedLevel = 0)
                }
                return resList
            }
        }

        ///

        val title: String =
            activityDb.name.textFeatures().textNoFeatures

        val timerHintsUi: List<TimerHintUi> =
            activityDb.buildTimerHintsOrDefault().map { seconds ->
                TimerHintUi(
                    seconds = seconds,
                    onTap = {
                        launchExIo {
                            activityDb.startTimer(
                                seconds = seconds,
                            )
                        }
                    },
                )
            }

        ///

        class TimerHintUi(
            val seconds: Int,
            val onTap: () -> Unit,
        ) {
            val title: String =
                seconds.toTimerHintNote(isShort = true)
        }
    }
}

///

private fun dayStartSecondsToString(seconds: Int): String {
    if ((seconds % 3_600) != 0) {
        reportApi("Invalid seconds in SettingsVm.dayStartSecondsToString($seconds)")
        return "error"
    }
    val h = seconds / 3_600
    if (h >= 0)
        return "$h:00".padStart(5, '0')
    return "${24 + h}:00".padStart(5, '0')
}

private fun prepAutoBackupTimeString(
    unixTime: UnixTime?,
): String {
    if (unixTime == null)
        return ""
    return unixTime.getStringByComponents(
        UnixTime.StringComponent.dayOfMonth,
        UnixTime.StringComponent.space,
        UnixTime.StringComponent.month3,
        UnixTime.StringComponent.comma,
        UnixTime.StringComponent.space,
        UnixTime.StringComponent.dayOfWeek3,
        UnixTime.StringComponent.space,
        UnixTime.StringComponent.hhmm24,
    )
}
