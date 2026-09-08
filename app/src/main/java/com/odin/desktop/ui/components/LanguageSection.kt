package com.odin.desktop.ui.components

import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.locale.AppLanguage
import com.odin.desktop.ui.theme.LocalOdinPalette

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoView)
                    .clip(RoundedCornerShape(OdinCorners.control))
                    .background(if (focused) palette.selection else palette.card)
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused || selected) palette.accent else palette.border,
                        RoundedCornerShape(OdinCorners.control)
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onLanguageSelect(language) }
                    .padding(OdinInsets.optionRow),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(language.label),
                    modifier = Modifier.weight(1f),
                    color = if (focused || selected) palette.accent else palette.text,
                    style = OdinTypography.h3
                )
                if (selected) {
                    Text(stringResource(R.string.text_active), color = palette.accent, style = OdinTypography.body)
                }
            }
        }
    }
}
