package com.nikhil.yt.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.nikhil.yt.R
import com.nikhil.yt.constants.LastFlashMessageDismissedTimeKey
import com.nikhil.yt.constants.LastFlashMessageIndexKey
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.reportException
import kotlinx.coroutines.launch

val FLASH_MESSAGES = listOf(
    "🎧 Hope you're loving Cynk!",
    "🖤 Enjoying Cynk? Follow me on Instagram.",
    "🎵 Cynk is still evolving — more improvements are on the way."
)

fun launchInstagram(context: Context, username: String = "ayu5h2") {
    try {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/_u/$username")).apply {
            setPackage("com.instagram.android")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(appIntent)
    } catch (_: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/$username")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            reportException(e)
        }
    }
}

@Composable
fun CynkFlashMessageBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences by context.dataStore.data.collectAsState(initial = null)

    var isVisible by remember { mutableStateOf(false) }
    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(preferences) {
        val prefs = preferences ?: return@LaunchedEffect
        val lastDismissed = prefs[LastFlashMessageDismissedTimeKey] ?: 0L
        val savedIndex = prefs[LastFlashMessageIndexKey] ?: 0
        val now = System.currentTimeMillis()
        val cooldownMs = 48L * 60L * 60L * 1000L // 48 hours

        if (now - lastDismissed >= cooldownMs) {
            messageIndex = savedIndex % FLASH_MESSAGES.size
            isVisible = true
        } else {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = FLASH_MESSAGES.getOrElse(messageIndex) { FLASH_MESSAGES[0] },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                launchInstagram(context, "ayu5h2")
                            }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.instagram),
                            contentDescription = "Instagram",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "@ayu5h2",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isVisible = false
                        coroutineScope.launch {
                            val nextIndex = (messageIndex + 1) % FLASH_MESSAGES.size
                            context.dataStore.edit { prefs ->
                                prefs[LastFlashMessageDismissedTimeKey] = System.currentTimeMillis()
                                prefs[LastFlashMessageIndexKey] = nextIndex
                            }
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
