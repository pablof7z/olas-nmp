package io.f7z.olas.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.core.WOT_NOTIFICATIONS_NOTE
import io.f7z.olas.ui.theme.OlasColors
import androidx.compose.foundation.layout.padding

private enum class NotifTab { ALL, MENTIONS, ZAPS }

@Suppress("UNUSED_PARAMETER")
@Composable
fun NotificationsScreen(navController: NavController) {
    val vm: NotificationsViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(NotifTab.ALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor   = OlasColors.Background,
            contentColor     = OlasColors.Text1,
            indicator        = { tabs ->
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabs[selectedTab.ordinal]),
                    color    = OlasColors.Text1,
                )
            },
        ) {
            NotifTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick  = { selectedTab = tab },
                    text     = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                )
            }
        }

        Text(
            text     = WOT_NOTIFICATIONS_NOTE,
            fontSize = 12.sp,
            color    = OlasColors.Text3,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = OlasColors.Border, thickness = 0.5.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OlasColors.Background),
        ) {
            if (state.isLoading && state.notifications.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = OlasColors.Text2,
                )
            } else {
                Column {
                    val filtered = when (selectedTab) {
                        NotifTab.ALL      -> state.notifications
                        NotifTab.MENTIONS -> state.notifications.filter {
                            it.type == NotificationType.MENTION || it.type == NotificationType.COMMENT
                        }
                        NotifTab.ZAPS     -> state.notifications.filter { it.type == NotificationType.ZAP }
                    }

                    LazyColumn {
                        items(filtered, key = { it.id }) { item ->
                            NotificationItemRow(item = item)
                            HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Box(
                                    modifier         = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Nothing here yet", color = OlasColors.Text2, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
