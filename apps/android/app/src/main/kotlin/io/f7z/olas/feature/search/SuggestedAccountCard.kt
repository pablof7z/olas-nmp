package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors

data class SuggestedAccount(
    val pubkey: String,
    val name: String,
    val avatarUrl: String?,
    val photoUrls: List<String>,
)

@Composable
fun SuggestedAccountCard(account: SuggestedAccount, modifier: Modifier = Modifier) {
    val followedPubkeys by NMPBridge.followedPubkeys.collectAsStateWithLifecycle()
    val isFollowing = followedPubkeys.contains(account.pubkey)

    Column(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OlasColors.Surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2x2 photo grid (first 4 photos)
        val photos = account.photoUrls.take(4)
        if (photos.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    val half = photos.take(2)
                    val half2 = if (photos.size > 2) photos.drop(2).take(2) else emptyList()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        half.forEach { url ->
                            AsyncImage(
                                model              = url,
                                contentDescription = null,
                                modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
                                contentScale       = ContentScale.Crop,
                            )
                        }
                    }
                    if (half2.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            half2.forEach { url ->
                                AsyncImage(
                                    model              = url,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale       = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 56dp avatar
        AsyncImage(
            model              = account.avatarUrl,
            contentDescription = "Avatar of ${account.name}",
            modifier           = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(OlasColors.Surface2),
            contentScale       = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(account.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick  = {
                if (isFollowing) NMPBridge.unfollow(account.pubkey) else NMPBridge.follow(account.pubkey)
            },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) OlasColors.Surface2 else OlasColors.Text1,
                contentColor   = if (isFollowing) OlasColors.Text1    else OlasColors.Background,
            ),
        ) {
            Text(if (isFollowing) "Following" else "Follow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
