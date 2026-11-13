package me.timeto.shared.vm.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import me.timeto.shared.*
import me.timeto.shared.db.*
import me.timeto.shared.time
import me.timeto.shared.ShortcutPerformer
import me.timeto.shared.db.KvDb.Companion.isZenModeEnabled
import me.timeto.shared.vm.Vm
import me.timeto.shared.widget.WidgetFlow
import kotlin.time.Duration.Companion.milliseconds

class AppVm : Vm<AppVm.State>() {

    companion object {

        val backupStateFlow = MutableStateFlow<String?>(null)
    }

    data class State(
        val isAppReady: Boolean,
        val isZenModeAllowed: Boolean,
        val backupMessage: String?,
    )

    override val state = MutableStateFlow(
        State(
            isAppReady = false,
            isZenModeAllowed = false,
            backupMessage = null,
        )
    )

    init {
        scopeVm().launchEx {

            initKmpDeferred.await()

            if (!Cache.isLateInitInitialized())
                fillInitData()

            // todo remove migration starts May 2026
            ActivityDb.selectAll().forEach { activityDb ->
                val goal_json: String = activityDb.goal_json ?: return@forEach
                val oldTimer: Int = goal_json.toIntOrNull() ?: return@forEach
                activityDb.updateGoal(ActivityDb.GoalType.Timer(seconds = oldTimer))
            }

            // todo remove migration starts July 2026
            val allTaskFolders = TaskFolderDb.selectAllSorted()
            if (!allTaskFolders.any { it.isTomorrow })
                TaskFolderDb.insertNoValidation(id = TaskFolderDb.ID_TOMORROW, sort = 2, activityDb = null, name = "Tomorrow", symbol = Symbol.Icon.IconEnum.moon.toIcon())
            if (!allTaskFolders.any { it.isSomeday }) {
                val smday = allTaskFolders.firstOrNull { it.name.lowercase() == "smday" }
                if (smday != null) {
                    db.transaction {
                        db.taskFolderQueries.updateIdTodoRemove(newId = TaskFolderDb.ID_SOMEDAY, oldId = smday.id)
                        db.taskQueries.updateFolderIdTodoRemove(newFolderId = TaskFolderDb.ID_SOMEDAY, oldFolderId = smday.id)
                    }
                } else {
                    TaskFolderDb.insertNoValidation(id = TaskFolderDb.ID_SOMEDAY, sort = 3, activityDb = null, name = "Someday", symbol = Symbol.Icon.IconEnum.inbox.toIcon())
                }
            }

            state.update { it.copy(isAppReady = true) }

            ///

            backupStateFlow
                .drop(1)
                .onEachExIn(this) { newMessage ->
                    state.update { it.copy(backupMessage = newMessage) }
                }

            IntervalDb
                .selectLastOneOrNullFlow()
                .filterNotNull()
                .onEachExIn(this) { lastIntervalDb ->
                    NotificationAlarm.rescheduleAll()
                    performShortcutForInterval(lastIntervalDb, secondsLimit = 3)
                    keepScreenOnStateFlow.emit(lastIntervalDb.selectActivityDb().keepScreenOn)
                }

            DayStartOffsetUtils.buildTodayFlow().onEachExIn(this) { todayWithDayStartOffset ->
                ChecklistDb.selectAsc().forEach { checklistDb ->
                    checklistDb.resetIfNeeded(todayWithDayStartOffset = todayWithDayStartOffset)
                }
                RepeatingDb.syncTodaySafe(todayWithDayStartOffset)
                syncTomorrow(todayWithDayStartOffset)
            }

            TimeFlows.todayFlow.onEachExIn(this) { today ->
                EventDb.syncTodaySafe(today)
            }

            NotificationsPermission.flow
                .filter { it == NotificationsPermission.granted }
                .onEachExIn(this) {
                    onNotificationsPermissionReady(delayMls = 0)
                }

            WidgetFlow.startSafe()

            KvDb.KEY.ZEN_MODE_ENABLED.selectOrNullFlow()
                .map { it.isZenModeEnabled() }
                .distinctUntilChanged()
                .onEachExIn(this) { isZenModeEnabled ->
                    state.update { it.copy(isZenModeAllowed = isZenModeEnabled) }
                }
        }
    }

    fun onNotificationsPermissionReady(delayMls: Long) {
        scopeVm().launchEx {
            delay(delayMls.milliseconds)
            NotificationAlarm.rescheduleAll()
        }
    }
}

private fun performShortcutForInterval(
    intervalDb: IntervalDb,
    secondsLimit: Int,
) {
    if ((intervalDb.time + secondsLimit) < time())
        return

    val text: String = ("${intervalDb.note ?: ""} ${intervalDb.selectActivityDbCached().name}")
    val shortcutDb: ShortcutDb = text.textFeatures().shortcutsDb.firstOrNull() ?: return

    ShortcutPerformer.perform(shortcutDb)
}

///

private suspend fun syncTomorrow(todayWithDayStartOffset: Int) {
    val todayFolder: TaskFolderDb = TaskFolderDb.selectAllSorted().first { it.isToday }
    val dayStartOffsetSeconds: Int = DayStartOffsetUtils.getOffsetSeconds()
    Cache.tasksDb
        .filter { it.isTomorrow }
        .filter {
            DayStartOffsetUtils.calcDay(
                time = it.id,
                dayStartOffsetSeconds = dayStartOffsetSeconds,
            ) < todayWithDayStartOffset
        }
        .forEach { taskDb ->
            launchExIo {
                taskDb.updateFolder(
                    taskFolderDb = todayFolder,
                    updateFolderActivity = true,
                    replaceIfTmrw = false, // No matter
                )
            }
        }
}

///

private suspend fun fillInitData() {
    TaskFolderDb.insertNoValidation(
        id = TaskFolderDb.ID_TODAY,
        sort = 1,
        activityDb = null,
        name = "Today",
        symbol = Symbol.Icon.IconEnum.sun.toIcon(),
    )
    TaskFolderDb.insertNoValidation(
        id = TaskFolderDb.ID_TOMORROW,
        sort = 2,
        activityDb = null,
        name = "Tomorrow",
        symbol = Symbol.Icon.IconEnum.moon.toIcon(),
    )
    TaskFolderDb.insertNoValidation(
        id = TaskFolderDb.ID_SOMEDAY,
        sort = 3,
        activityDb = null,
        name = "Someday",
        symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
    )


    val initIntervalDb = addOtherActivity().startInterval()

    Cache.fillLateInit(initIntervalDb, initIntervalDb) // To 100% ensure

}

//
// Required initial activity

private val everyDayActivityPeriod: ActivityDb.Period =
    ActivityDb.Period.DaysOfWeek.everyDay

private suspend fun addOtherActivity(): ActivityDb =
    ActivityDb.insertWithValidation(
        name = "Other",
        goalType = null,
        timerType = ActivityDb.TimerType.StopwatchZero,
        period = everyDayActivityPeriod,
        symbol = Symbol.Icon.IconEnum.bulb.toIcon(),
        colorRgba = Palette.gray.dark,
        keepScreenOn = true,
        pomodoroTimer = 5 * 60,
        timerHints = listOf(15 * 60, 60 * 60),
        parentActivityDb = null,
        type = ActivityDb.Type.other,
    ).also { activityDb ->
        activityDb.updateHomeButtonSort(HomeButtonSort(rowIdx = 0, cellIdx = 0, size = 2))
    }
