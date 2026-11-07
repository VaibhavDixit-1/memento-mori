package me.timeto.app.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.timeto.app.MainActivity
import me.timeto.app.R
import me.timeto.app.ui.HStack
import me.timeto.app.ui.H_PADDING
import me.timeto.app.ui.VStack
import me.timeto.app.ui.ZStack
import me.timeto.app.ui.c
import me.timeto.app.ui.squircleShape
import me.timeto.app.ui.timerFont
import me.timeto.app.toColor
import me.timeto.app.ui.daytime_picker.DaytimePickerSheet
import me.timeto.app.ui.history.form.HistoryFormFs
import me.timeto.app.ui.navigation.LocalNavigationFs
import me.timeto.app.ui.timer.TimerSheet
import me.timeto.shared.vm.home.HomeVm
import me.timeto.shared.DaytimeUi
import me.timeto.shared.UnixTime
import me.timeto.shared.launchExIo
import me.timeto.shared.time

@Composable
fun HomeTimerView(
    vm: HomeVm,
    state: HomeVm.State,
) {

    val navigationFs = LocalNavigationFs.current
    val mainActivity = LocalActivity.current as MainActivity
    val statusBarHeight: Dp =
        mainActivity.statusBarHeightFlow.collectAsState().value

    val noteColor = animateColorAsState(state.timerStateUi.noteColor.toColor()).value
    val timerColor = animateColorAsState(state.timerStateUi.timerColor.toColor()).value
    val timerControlsColor = animateColorAsState(
        state.timerStateUi.controlsColorEnum?.toColor() ?: c.mainTabsMenuSecondary
    ).value
    val timerTextHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    VStack(
        modifier = Modifier
            .padding(top = statusBarHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        ZStack(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {

            TimerDataNoteText(
                text = state.timerStateUi.note,
                color = noteColor,
            )

            HStack {

                val timerText = state.timerStateUi.timerText
                val timerFontSize: TextUnit = run {
                    val len = timerText.count()
                    when {
                        len <= 5 -> 40.sp
                        len <= 7 -> 35.sp
                        else -> 28.sp
                    }
                }

                VStack(
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = 1.dp),
                ) {

                    TimerDataNoteText(" ", c.transparent)

                    ZStack(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { timerTextHeightPx.intValue.toDp() })
                            .clip(squircleShape),
                        contentAlignment = Alignment.Center,
                    ) {

                        TimerDataTimerText(" ", timerFontSize, c.transparent)

                        VStack(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            TimerAdjustButton(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                iconResId = R.drawable.sf_plus_medium_bold,
                                description = "Add five minutes",
                                color = timerControlsColor,
                            ) {
                                updateIntervalTimer(state, currentPlannedDurationSeconds(state) + 5 * 60)
                            }
                            TimerAdjustButton(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                iconResId = R.drawable.sf_minus_medium_semibold,
                                description = "Subtract five minutes",
                                color = timerControlsColor,
                            ) {
                                updateIntervalTimer(
                                    state,
                                    (currentPlannedDurationSeconds(state) - 5 * 60).coerceAtLeast(60),
                                )
                            }
                        }
                    }
                }

                VStack(
                    modifier = Modifier
                        .clip(squircleShape)
                        .clickable {
                            state.timerStateUi.togglePomodoro()
                        },
                ) {

                    TimerDataNoteText(" ", c.transparent)

                    TimerDataTimerText(
                        text = timerText,
                        fontSize = timerFontSize,
                        color = timerColor,
                        modifier = Modifier.onSizeChanged { timerTextHeightPx.intValue = it.height },
                    )
                }

                VStack(
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = (-1).dp),
                ) {

                    TimerDataNoteText(" ", c.transparent)

                    ZStack(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(squircleShape)
                            .clickable {
                                navigationFs.push {
                                    HistoryFormFs(
                                        initIntervalDb = state.intervalDb,
                                        withNoteAutoFocus = true,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {

                        TimerDataTimerText(" ", timerFontSize, c.transparent)

                        Icon(
                            painterResource(id = R.drawable.sf_pencil_medium_thin),
                            contentDescription = "Plus",
                            tint = timerControlsColor,
                            modifier = Modifier
                                .alpha(.8f)
                                .size(16.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            state.isPurple,
            enter = purpleAnimEnter,
            exit = purpleAnimExit,
        ) {

            val infoUi = state.timerStateUi.infoUi

            HStack(
                modifier = Modifier
                    .offset(y = (2).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                val intervalDb = state.intervalDb
                val startDaytimeUi = DaytimeUi.byDaytime(
                    intervalDb.time - UnixTime(intervalDb.time).localDayStartTime(),
                )

                TimerInfoButton(
                    text = UnixTime(intervalDb.time).getStringByComponents(UnixTime.StringComponent.hhmm24),
                    iconResId = R.drawable.ph_clock_thin,
                    color = timerControlsColor,
                    onClick = {
                        navigationFs.push {
                            DaytimePickerSheet(
                                title = "Start Time",
                                doneText = "Set",
                                daytimeUi = startDaytimeUi,
                                withRemove = false,
                                onDone = { daytimeUi ->
                                    val dayStart = UnixTime(intervalDb.time).localDayStartTime()
                                    var newTime = dayStart + daytimeUi.seconds
                                    if (newTime > time()) newTime -= 86_400
                                    launchExIo {
                                        intervalDb.updateEx(
                                            newTime = newTime,
                                            newActivityDb = state.activityDb,
                                            newNote = intervalDb.note,
                                        )
                                    }
                                },
                                onRemove = {},
                            )
                        }
                    },
                )

                TimerInfoButton(
                    text = infoUi.timerText,
                    iconResId = R.drawable.ph_hourglass_thin,
                    color = timerColor,
                    onClick = {
                        navigationFs.push {
                            TimerSheet(
                                title = state.timerStateUi.note,
                                doneTitle = "Start",
                                initSeconds = currentPlannedDurationSeconds(state),
                                hints = state.activityDb.buildTimerHints(),
                                onDone = { newTimerSeconds ->
                                    updateIntervalTimer(state, newTimerSeconds)
                                },
                            )
                        }
                    },
                )

                TimerInfoButton(
                    text = when (infoUi.timerType) {
                        is me.timeto.shared.db.IntervalDb.TimerType.Timer -> infoUi.untilDaytimeUi.text
                        is me.timeto.shared.db.IntervalDb.TimerType.OverdueTimer,
                        is me.timeto.shared.db.IntervalDb.TimerType.Stopwatch -> "–"
                    },
                    iconResId = R.drawable.ph_alarm_thin,
                    color = timerColor,
                    onClick = {
                        navigationFs.push {
                            DaytimePickerSheet(
                                title = infoUi.untilPickerTitle,
                                doneText = "Start",
                                daytimeUi = infoUi.untilDaytimeUi,
                                withRemove = false,
                                onDone = { daytimePickerUi ->
                                    infoUi.setUntilDaytime(daytimePickerUi)
                                    vm.toggleIsPurple()
                                },
                                onRemove = {},
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun currentPlannedDurationSeconds(state: HomeVm.State): Int =
    (state.timerStateUi.infoUi.timerType as? me.timeto.shared.db.IntervalDb.TimerType.Timer)
        ?.timer
        ?: (45 * 60)

private fun updateIntervalTimer(state: HomeVm.State, seconds: Int) {
    launchExIo {
        state.intervalDb.updateTimer(seconds)
    }
}

@Composable
private fun TimerAdjustButton(
    modifier: Modifier,
    iconResId: Int,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    ZStack(
        modifier = modifier
            .clip(squircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
    }
}

///

private val purpleAnimEnter =
    fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))

private val purpleAnimExit =
    fadeOut() + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))

///

@Composable
private fun TimerInfoButton(
    text: String,
    iconResId: Int,
    color: Color,
    onClick: () -> Unit,
) {
    HStack(
        modifier = Modifier
            .clip(squircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            color = color,
            fontSize = 19.sp,
            fontWeight = FontWeight.Thin,
        )
    }
}

@Composable
private fun TimerDataNoteText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .padding(horizontal = H_PADDING),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimerDataTimerText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .padding(vertical = 4.dp),
        fontSize = fontSize,
        fontFamily = timerFont,
        color = color,
    )
}
