package com.odin.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.odin.desktop.R
import com.odin.desktop.locale.AppLanguage
import com.odin.desktop.ui.components.base.OdinControl
import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun LanguageSection(
    currentLanguage: AppLanguage,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onLanguageSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(OdinSpacing.md)
    ) {
        SettingsSectionHeader(stringResource(R.string.language_title),
            stringResource(R.string.language_description), modifier = Modifier.padding(bottom = OdinSpacing.xs))
        AppLanguage.entries.forEachIndexed { index, language ->
            val focused = inSubMenu && subFocusIndex == index
            val selected = currentLanguage == language
            val bringIntoView = remember { BringIntoViewRequester() }
            LaunchedEffect(focused) {
                if (focused) bringIntoView.bringIntoView()
            }
            OdinControl(
                text = stringResource(language.label),
                onClick = { onLanguageSelect(language) },
                selected = selected,
                focused = focused,
                radio = true,
                badge = if (selected) stringResource(R.string.text_active) else null,
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)
            )
        }
    }
}
