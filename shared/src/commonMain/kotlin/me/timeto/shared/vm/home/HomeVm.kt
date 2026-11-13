package me.timeto.shared.vm.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.timeto.shared.*
import me.timeto.shared.db.*
import me.timeto.shared.limitMin
import me.timeto.shared.TaskUi
import me.timeto.shared.time
import me.timeto.shared.TimerStateUi
import me.timeto.shared.vm.Vm
import me.timeto.shared.vm.home.bar.HomeBarUi
import me.timeto.shared.vm.home.tasks.homeTasksFoldersSorted
import me.timeto.shared.vm.task_form.TaskFormStrategy
import kotlin.time.Duration.Companion.milliseconds

class HomeVm : Vm<HomeVm.State>() {

    companion object {

        private val showStartScreenFlow = MutableSharedFlow<Boolean>()

        fun showStartScreen(toggleIsPurple: Boolean = false) {
            launchExIo {
                showStartScreenFlow.emit(toggleIsPurple)
            }
        }
    }

    data class State(
        val intervalUi: IntervalUi,
        val isPurple: Boolean,
        val allTasksUi: List<TaskUi>,
        val listsContainerSize: ListsContainerSize?,
        val notificationsPermissionUi: NotificationsPermissionUi?,
        val allRepeatingsDb: List<RepeatingDb>,
        val allEventsDb: List<EventDb>,
        val allTaskFoldersUi: List<TaskFolderUi>,
        val homeNoteFoldersUi: List<NoteFolderUi>,
        val homeModePrototype: HomeModePrototype,
        val idToUpdate: Long,
    ) {

        val intervalDb: IntervalDb =
            intervalUi.intervalDb
        val activityDb: ActivityDb =
            intervalUi.activityDb

        val timerStateUi = TimerStateUi(
            intervalUi = intervalUi,
            todayTasksDb = allTasksUi.filter { it.taskDb.isToday }.map { it.taskDb },
            isPurple = isPurple,
        )

        val tfForTriggers: TextFeatures =
            timerStateUi.tfForTriggers

        val checklistDb: ChecklistDb? =
            tfForTriggers.checklistsDb.firstOrNull()

        val checklistHintUi: ChecklistHintUi? = run {
            if (checklistDb != null)
                return@run null
            if (activityDb.checklist_hint > 0)
                return@run null
            ChecklistHintUi(
                activityDb = activityDb,
            )
        }

        val extraTriggers = ExtraTriggers(
            checklistsDb = tfForTriggers.checklistsDb.filter {
                it.id != checklistDb?.id
            },
            shortcutsDb = tfForTriggers.shortcutsDb,
        )

        val widgetNewTaskFormLogic = TaskFormStrategy.NewTask(
            activityDb = Cache.activitiesDb.first { it.isOther },
            taskFolderDb = Cache.todayTaskFolderDb,
        )

        val homeMode: HomeMode = when (homeModePrototype) {
            is HomeModePrototype.TaskFolder -> HomeMode.TaskFolder(
                taskFolderDb = homeModePrototype.taskFolderDb,
                allTasksUi = allTasksUi,
                allRepeatingsDb = allRepeatingsDb,
                allEventsDb = allEventsDb,
                allTaskFoldersUi = allTaskFoldersUi,
                homeNoteFoldersUi = homeNoteFoldersUi,
            )
            is HomeModePrototype.NoteFolder -> HomeMode.NoteFolder(
                noteFolderDb = homeModePrototype.noteFolderDb,
            )
        }

        val homeBarUi = HomeBarUi(
            homeMode = homeMode,
            taskFoldersUi = allTaskFoldersUi.homeTasksFoldersSorted(),
            noteFoldersUi = homeNoteFoldersUi,
        )

        val listsSizes: ListsSizes = run {
            val lc = listsContainerSize ?: return@run ListsSizes(0f, 0f)
            //
            // Only for HomeMode.TaskFolder
            if (homeMode !is HomeMode.TaskFolder)
                return@run ListsSizes(0f, 0f)
            val homeTasksItemsUi = homeMode.homeTasksItemsUi
            //
            // No one
            if (checklistDb == null && homeTasksItemsUi.isEmpty())
                return@run ListsSizes(0f, 0f)
            //
            // Only one
            if (checklistDb != null && homeTasksItemsUi.isEmpty())
                return@run ListsSizes(checklist = lc.totalHeight, mainTasks = 0f)
            if (checklistDb == null && homeTasksItemsUi.isNotEmpty())
                return@run ListsSizes(checklist = 0f, mainTasks = lc.totalHeight)
            //
            // Both
            checklistDb!!
            val halfHeight: Float = lc.totalHeight / 2
            val tasksFullHeight: Float = homeTasksItemsUi.size * lc.itemHeight
            // Tasks smaller the half
            if (tasksFullHeight < halfHeight)
                return@run ListsSizes(
                    checklist = lc.totalHeight - tasksFullHeight,
                    mainTasks = tasksFullHeight,
                )
            // Tasks bigger the half
            val checklistCount: Int = checklistDb.getItemsCached().size.limitMin(1)
            val checklistFullHeight: Float = checklistCount * lc.itemHeight
            if (checklistFullHeight < halfHeight)
                return@run ListsSizes(
                    checklist = checklistFullHeight,
                    mainTasks = lc.totalHeight - checklistFullHeight,
                )
            ListsSizes(checklist = halfHeight, mainTasks = halfHeight)
        }

        fun startFromTimer(seconds: Int) {
            launchExIo {
                activityDb.startInterval(
                    note = (intervalDb.note ?: "").textFeatures().copy(
                        timerType = TextFeatures.TimerType.Timer(seconds)
                    ).textWithFeatures(),
                )
            }
        }
    }

    override val state = MutableStateFlow(
        State(
            intervalUi = run {
                val intervalDb: IntervalDb = Cache.lastIntervalDb
                IntervalUi(intervalDb, intervalDb.selectActivityDbCached())
            },
            isPurple = false,
            allTasksUi = Cache.tasksDb.map { it.toUi() },
            listsContainerSize = null,
            notificationsPermissionUi = null, // todo init data
            allRepeatingsDb = Cache.repeatingsDb,
            allEventsDb = Cache.eventsDb,
            allTaskFoldersUi = Cache.taskFoldersDbSorted.map {
                TaskFolderUi(it, it.selectActivityDbOrNullCached())
            },
            homeNoteFoldersUi = Cache.noteFoldersDb
                .map { NoteFolderUi(it) }
                .filter { it.noteFolderDb.onHome },
            homeModePrototype = HomeModePrototype.TaskFolder(
                taskFolderDb = Cache.todayTaskFolderDb,
            ),
            idToUpdate = 0,
        )
    )

    init {
        val scopeVm = scopeVm()

        combine(
            IntervalDb.selectLastOneOrNullFlow().filterNotNull(),
            ActivityDb.selectAllFlow(),
            NotificationsPermission.flow,
            RepeatingDb.selectAscFlow(),
            EventDb.selectAscByTimeFlow(),
            TaskDb.selectAscFlow(),
            TaskFolderDb.selectAllSortedFlow(),
            NoteFolderDb.selectAllSortedFlow(),
        ) { lastIntervalDb,
            activitiesDb,
            notificationsPermission,
            allRepeatingsDb,
            allEventsDb,
            allTasksDb,
            allTaskFoldersDb,
            allNoteFoldersDb ->

            val notificationsPermissionUi: NotificationsPermissionUi? = when (notificationsPermission) {
                NotificationsPermission.notAsked -> NotificationsPermissionUi.NotAsked
                NotificationsPermission.denied -> NotificationsPermissionUi.Denied
                NotificationsPermission.rationale -> NotificationsPermissionUi.Rationale
                NotificationsPermission.granted -> null
                null -> null
            }

            state.update { state ->
                val isNewInterval: Boolean =
                    state.intervalDb.id != lastIntervalDb.id
                state.copy(
                    intervalUi = IntervalUi(
                        intervalDb = lastIntervalDb,
                        activityDb = activitiesDb.first { it.id == lastIntervalDb.activityId },
                    ),
                    isPurple = if (isNewInterval) false else state.isPurple,
                    notificationsPermissionUi = notificationsPermissionUi,
                    allRepeatingsDb = allRepeatingsDb,
                    allEventsDb = allEventsDb,
                    allTasksUi = allTasksDb.map { it.toUi() },
                    allTaskFoldersUi = allTaskFoldersDb.map { taskFolderDb ->
                        TaskFolderUi(
                            taskFolderDb = taskFolderDb,
                            activityDb = activitiesDb.firstOrNull { it.id == taskFolderDb.activity_id },
                        )
                    },
                    homeNoteFoldersUi = allNoteFoldersDb
                        .map { NoteFolderUi(it) }
                        .filter { it.noteFolderDb.onHome },
                    homeModePrototype =
                        if (isNewInterval)
                            HomeModePrototype.TaskFolder(Cache.todayTaskFolderDb)
                        else
                            state.homeModePrototype,
                )
            }
        }.launchIn(scopeVm)

        showStartScreenFlow.onEachExIn(scopeVm) { togglePurple ->
            setTodayTaskFolder()
            if (togglePurple) toggleIsPurple()
        }

        ///

        scopeVm.launch {
            while (true) {
                state.update {
                    val lastIntervalDb = Cache.lastIntervalDb
                    it.copy(
                        intervalUi = IntervalUi(
                            intervalDb = lastIntervalDb,
                            activityDb = lastIntervalDb.selectActivityDbCached(),
                        ),
                        idToUpdate = it.idToUpdate + 1, // Force update
                    )
                }
                delay(1_000.milliseconds)
            }
        }
    }

    fun updateTaskFolder(taskFolderUi: TaskFolderUi) {
        state.update {
            it.copy(homeModePrototype = HomeModePrototype.TaskFolder(taskFolderUi.taskFolderDb))
        }
    }

    fun updateTaskFolderById(taskFolderId: Int) {
        val taskFolderUi: TaskFolderUi = Cache.taskFoldersDbSorted
            .first { it.id == taskFolderId }
            .let { TaskFolderUi(it, it.selectActivityDbOrNullCached()) }
        updateTaskFolder(taskFolderUi)
    }

    fun updateNoteFolder(noteFolderUi: NoteFolderUi) {
        state.update {
            it.copy(homeModePrototype = HomeModePrototype.NoteFolder(noteFolderUi.noteFolderDb))
        }
    }

    fun updateNoteFolderById(noteFolderId: Int) {
        val noteFolderUi: NoteFolderUi = Cache.noteFoldersDb
            .first { it.id == noteFolderId }
            .let { NoteFolderUi(it) }
        updateNoteFolder(noteFolderUi)
    }

    fun setTodayTaskFolder() {
        updateTaskFolder(
            TaskFolderUi(
                taskFolderDb = Cache.todayTaskFolderDb,
                activityDb = null, // Always null for today
            )
        )
    }

    fun upListsContainerSize(
        totalHeight: Float,
        itemHeight: Float,
    ) {
        val lc = ListsContainerSize(totalHeight, itemHeight)
        if (lc == state.value.listsContainerSize)
            return
        state.update { it.copy(listsContainerSize = lc) }
    }

    fun toggleIsPurple() {
        state.update { it.copy(isPurple = !it.isPurple) }
    }

    ///

    data class ListsContainerSize(
        val totalHeight: Float,
        val itemHeight: Float,
    )

    data class ListsSizes(
        val checklist: Float,
        val mainTasks: Float,
    )

    ///

    data class ExtraTriggers(
        val checklistsDb: List<ChecklistDb>,
        val shortcutsDb: List<ShortcutDb>,
    )

    data class ChecklistHintUi(
        val activityDb: ActivityDb,
    ) {

        val title: String =
            "New Checklist for ${activityDb.name.textFeatures().textNoFeatures}"

        fun hide() {
            launchExIo {
                activityDb.updateChecklistHint(time())
            }
        }

        fun create(
            dialogsManager: DialogsManager,
            onSuccess: (ChecklistDb) -> Unit,
        ) {
            launchExIo {
                try {
                    val oldGoalTf: TextFeatures =
                        activityDb.name.textFeatures()
                    val checklistName: String =
                        oldGoalTf.textNoFeatures
                    val existingChecklistDb: ChecklistDb? =
                        ChecklistDb.selectAsc().firstOrNull { it.name.textFeatures().textNoFeatures == checklistName }
                    val actualChecklistDb: ChecklistDb =
                        existingChecklistDb ?: ChecklistDb.insertWithValidation(checklistName, isResetOnDayStarts = true)
                    val newGoalTf: TextFeatures =
                        oldGoalTf.copy(checklistsDb = (oldGoalTf.checklistsDb + actualChecklistDb))
                    activityDb.updateNameWithValidation(newGoalTf.textWithFeatures())
                    onSuccess(actualChecklistDb)
                } catch (uiException: UiException) {
                    dialogsManager.alert(uiException.uiMessage)
                }
            }
        }
    }

    sealed class NotificationsPermissionUi {

        val title = "Allow notifications to show\ntimer always on display."
        val buttonText = "Allow Notifications"

        object NotAsked : NotificationsPermissionUi()
        object Rationale : NotificationsPermissionUi()
        object Denied : NotificationsPermissionUi()
    }
}
