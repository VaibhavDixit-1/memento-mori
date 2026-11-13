package me.timeto.app.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterNotNull
import me.timeto.app.ui.VStack
import me.timeto.app.ui.c
import me.timeto.app.ui.pxToDp
import me.timeto.app.ui.rememberVm
import me.timeto.app.ui.checklists.ChecklistView
import me.timeto.app.ui.Padding
import me.timeto.app.ui.Screen
import me.timeto.app.ui.SpacerW1
import me.timeto.app.ui.calendar.CalendarTabsView
import me.timeto.app.ui.home.bar.HomeBarView
import me.timeto.app.ui.home.buttons.HomeButtonsView
import me.timeto.app.ui.home.tasks.HomeTasksView
import me.timeto.app.ui.navigation.LocalNavigationFs
import me.timeto.app.ui.task_form.TaskFormFs
import me.timeto.app.widget.MyWidgetOpenApp
import me.timeto.shared.vm.home.HomeMode
import me.timeto.shared.vm.home.HomeVm

val HomeScreen__primaryFontSize = 16.sp

val HomeScreen__hPadding = 8.dp
val HomeScreen__itemHeight = 36.dp
val HomeScreen__itemCircleHPadding = 6.dp
val HomeScreen__itemCircleHeight = 24.dp
val HomeScreen__itemCircleFontSize = 13.sp
val HomeScreen__itemCircleFontWeight = FontWeight.SemiBold
val HomeScreen__itemCircleMarginTrailing = 8.dp
val HomeScreen__barTextLineHeight = 18.sp

@Composable
fun HomeScreen() {

    val navigationFs = LocalNavigationFs.current

    val (vm, state) = rememberVm {
        HomeVm()
    }

    val isTodayTaskFolder: Boolean =
        (state.homeMode as? HomeMode.TaskFolder)?.taskFolderDb?.isToday == true

    BackHandler(!isTodayTaskFolder) {
        vm.setTodayTaskFolder()
    }

    val checklistDb = state.checklistDb
    VStack(
        modifier = Modifier
            .fillMaxSize()
            .background(c.black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (isTodayTaskFolder) {

            HomeTimerView(
                vm = vm,
                state = state,
            )

            HomeExtraTriggersView(
                extraTriggers = state.extraTriggers,
                modifier = Modifier.padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 50.dp),
            )

            val checklistHintUi = state.checklistHintUi
            if (checklistHintUi != null) {
                HomeChecklistHintView(hintUi = checklistHintUi)
            }
        }

        //
        // Checklist + Main Tasks

        VStack(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords ->
                    val totalHeight = coords.size.height
                    vm.upListsContainerSize(
                        totalHeight = pxToDp(totalHeight),
                        itemHeight = HomeScreen__itemHeight.value,
                    )
                },
        ) {

            when (val homeMode = state.homeMode) {

                is HomeMode.TaskFolder -> {

                    val checklistScrollState = rememberLazyListState()

                    val isToday: Boolean = homeMode.taskFolderDb.isToday
                    val isMainListItemsExists: Boolean = homeMode.homeTasksItemsUi.isNotEmpty()
                    val listSizes = state.listsSizes

                    if (checklistDb != null && isToday) {
                        ChecklistView(
                            checklistDb = checklistDb,
                            modifier = Modifier
                                .height(listSizes.checklist.dp),
                            scrollState = checklistScrollState,
                            maxLines = 1,
                            fullHeight = true,
                            withAddButton = false,
                            topPadding = 0.dp,
                            bottomPadding = 0.dp,
                            withNavigationPadding = false,
                        )
                    }

                    if (isMainListItemsExists || !isToday) {
                        HomeTasksView(
                            homeModeTaskFolder = homeMode,
                            modifier =
                                if (!isToday)
                                    Modifier.weight(1f)
                                else
                                    Modifier.height(listSizes.mainTasks.dp)
                        )
                    }

                    if (!isMainListItemsExists && checklistDb == null)
                        SpacerW1()
                }

                is HomeMode.NoteFolder -> {
                    HomeNotesView(
                        noteFolderDb = homeMode.noteFolderDb,
                    )
                }
            }
        }

        val notificationsPermissionUi = state.notificationsPermissionUi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            notificationsPermissionUi != null
        ) {
            HomeNotificationsView(notificationsPermissionUi)
        }

        HomeBarView(
            homeBarUi = state.homeBarUi,
            changeTaskFolder = { taskFolderUi ->
                vm.updateTaskFolder(taskFolderUi)
            },
            changeNoteFolder = { noteFolderUi ->
                vm.updateNoteFolder(noteFolderUi)
            },
        )

        LaunchedEffect(Unit) {
            MyWidgetOpenApp.flow.filterNotNull().collect { appAction ->
                when (appAction) {
                    is MyWidgetOpenApp.AppAction.NewTask -> {
                        navigationFs.push {
                            TaskFormFs(
                                strategy = state.widgetNewTaskFormLogic,
                            )
                        }
                    }
                    is MyWidgetOpenApp.AppAction.OpenCalendar -> {
                        navigationFs.push {
                            Screen {
                                CalendarTabsView()
                            }
                        }
                    }
                    is MyWidgetOpenApp.AppAction.OpenTaskFolder -> {
                        vm.updateTaskFolderById(appAction.taskFolderId)
                    }
                    is MyWidgetOpenApp.AppAction.OpenNoteFolder -> {
                        vm.updateNoteFolderById(appAction.noteFolderId)
                    }
                }
                MyWidgetOpenApp.emitFlow(null)
            }
        }

        HomeButtonsView()

        Padding(vertical = 8.dp)
    }
}
