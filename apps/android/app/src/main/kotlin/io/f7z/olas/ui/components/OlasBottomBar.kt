package io.f7z.olas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun OlasBottomBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onCompose: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(OlasColors.Background),
    ) {
        // Top 1dp divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OlasColors.Border)
                .align(Alignment.TopCenter)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcon(Icons.Filled.Home, "Home", currentRoute == "home", onHome)
            TabIcon(Icons.Filled.Search, "Search", currentRoute == "search", onSearch)
            // Centre Post tab
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = onCompose) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Create post",
                                tint = Color.Black,
                            )
                        }
                    }
                    Text("Post", fontSize = 10.sp, color = OlasColors.Text3)
                }
            }
            TabIcon(Icons.Filled.Notifications, "Notifications", currentRoute == "notifications", onNotifications)
            TabIcon(Icons.Filled.Person, "Profile", currentRoute?.startsWith("profile") == true, onProfile)
        }
    }
}

@Composable
private fun RowScope.TabIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) OlasColors.Text1 else OlasColors.Text3,
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (active) OlasColors.Text1 else OlasColors.Text3,
            )
        }
    }
}
