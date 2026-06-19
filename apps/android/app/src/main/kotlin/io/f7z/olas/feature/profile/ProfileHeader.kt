package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun ProfileHeader(
    profile: OlasProfile,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Banner — 16:9 aspect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(OlasColors.Surface),
        ) {
            if (profile.banner != null) {
                AsyncImage(
                    model              = profile.banner,
                    contentDescription = "Profile banner",
                    modifier           = Modifier.fillMaxWidth().height(120.dp),
                    contentScale       = ContentScale.Crop,
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Avatar overlapping banner
            AsyncImage(
                model              = profile.picture,
                contentDescription = "Avatar of ${profile.displayName ?: profile.name}",
                modifier           = Modifier
                    .padding(start = 16.dp)
                    .offset(y = (-40).dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, OlasColors.Background, CircleShape)
                    .background(OlasColors.Surface),
                contentScale       = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp)) {
            val displayName = profile.displayName ?: profile.name ?: profile.pubkey.take(8)
            Text(displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OlasColors.Text1)
            if (profile.nip05 != null) {
                Text(profile.nip05, fontSize = 13.sp, color = OlasColors.Text2)
            }
            if (!profile.about.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(profile.about, fontSize = 14.sp, color = OlasColors.Text1, maxLines = 5)
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            if (isOwnProfile) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor   = OlasColors.Text1,
                    ),
                ) {
                    Text("Edit profile", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row {
                    Button(
                        onClick  = onFollow,
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) OlasColors.Surface else OlasColors.Text1,
                            contentColor   = if (isFollowing) OlasColors.Text1   else OlasColors.Background,
                        ),
                    ) {
                        Text(if (isFollowing) "Following" else "Follow", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
