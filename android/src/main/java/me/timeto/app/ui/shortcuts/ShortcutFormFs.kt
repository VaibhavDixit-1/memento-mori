package me.timeto.app.ui.shortcuts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.timeto.app.ui.c
import me.timeto.app.ui.rememberVm
import me.timeto.app.ui.Screen
import me.timeto.app.ui.form.button.FormButton
import me.timeto.app.ui.form.FormHeader
import me.timeto.app.ui.form.FormInput
import me.timeto.app.ui.form.padding.FormPaddingTop
import me.timeto.app.ui.form.padding.FormPaddingSectionHeader
import me.timeto.app.ui.header.Header
import me.timeto.app.ui.header.HeaderActionButton
import me.timeto.app.ui.header.HeaderCancelButton
import me.timeto.app.ui.navigation.LocalNavigationFs
import me.timeto.app.ui.navigation.LocalNavigationLayer
import me.timeto.app.ui.shortcuts.apps.ShortcutAppsFs
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.vm.shortcuts.ShortcutFormVm

@Composable
fun ShortcutFormFs(
    shortcutDb: ShortcutDb?,
) {

    val navigationFs = LocalNavigationFs.current
    val navigationLayer = LocalNavigationLayer.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val (vm, state) = rememberVm {
        ShortcutFormVm(
            shortcutDb = shortcutDb,
        )
    }

    Screen(
        modifier = Modifier
            .imePadding(),
    ) {

        val scrollState = rememberLazyListState()

        Header(
            title = state.title,
            scrollState = scrollState,
            actionButton = HeaderActionButton(
                text = state.doneText,
                isEnabled = state.isSaveEnabled,
                onClick = {
                    vm.save(
                        dialogsManager = navigationFs,
                        onSuccess = {
                            navigationLayer.close()
                        },
                    )
                },
            ),
            cancelButton = HeaderCancelButton(
                text = "Close",
                onClick = {
                    navigationLayer.close()
                },
            ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(bottom = 25.dp),
        ) {

            item {

                val triggerReinit = remember { mutableStateOf(false) }

                fun reinitForm(
                    name: String,
                    uri: String,
                ) {
                    vm.setName(name)
                    vm.setUri(uri)
                    triggerReinit.value = !triggerReinit.value
                    keyboardController?.hide()
                }

                //
                // Name

                FormPaddingTop()

                FormHeader(state.nameHeader)

                FormInput(
                    initText = state.name,
                    placeholder = state.namePlaceholder,
                    onChange = { newName ->
                        vm.setName(newName)
                    },
                    isFirst = true,
                    isLast = true,
                    isAutoFocus = false,
                    imeAction = ImeAction.Done,
                    triggerReinit = triggerReinit.value,
                )

                //
                // Uri

                FormPaddingSectionHeader()

                FormHeader(state.uriHeader)

                FormInput(
                    initText = state.uri,
                    placeholder = state.uriPlaceholder,
                    onChange = { newUri ->
                        vm.setUri(newUri)
                    },
                    isFirst = true,
                    isLast = true,
                    isAutoFocus = false,
                    imeAction = ImeAction.Done,
                    triggerReinit = triggerReinit.value,
                )

                FormPaddingSectionHeader()

                FormButton(
                    title = "Apps",
                    isFirst = true,
                    isLast = true,
                    onClick = {
                        navigationFs.push {
                            ShortcutAppsFs(
                                onAppSelected = { shortcutApp ->
                                    reinitForm(
                                        name = shortcutApp.name,
                                        uri = vm.prepUriForAndroidPackage(
                                            androidPackage = shortcutApp.androidPackage
                                        ),
                                    )
                                }
                            )
                        }
                    },
                )

                if (shortcutDb != null) {
                    FormPaddingSectionHeader()
                    FormButton(
                        title = state.deleteText,
                        titleColor = c.red,
                        isFirst = true,
                        isLast = true,
                        onClick = {
                            vm.delete(
                                shortcutDb = shortcutDb,
                                dialogsManager = navigationFs,
                                onDelete = {
                                    navigationLayer.close()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
