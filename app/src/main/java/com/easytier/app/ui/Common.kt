package com.easytier.app.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isCopyable: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showCopied by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (isCopyable) {
                    Modifier.combinedClickable(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(value))
                            showCopied = true
                            Toast.makeText(context, "'$value' 已复制", Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(value))
                            showCopied = true
                            Toast.makeText(context, "'$value' 已复制", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCopyable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSwitchWithHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String,
    enabled: Boolean
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    modifier = Modifier
                        .padding(8.dp)
                        .widthIn(max = 300.dp)
                        .background(
                            MaterialTheme.colorScheme.inverseSurface,
                            MaterialTheme.shapes.small
                        )
                ) {
                    Text(
                        helpText,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            },
            state = tooltipState
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "帮助: $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        scope.launch {
                            if (tooltipState.isVisible) {
                                tooltipState.dismiss()
                            } else {
                                tooltipState.show()
                            }
                        }
                    }
            )
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        )
    }
}

@Composable
fun ConfigSwitchWithInlineHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String,
    enabled: Boolean
) {
    var helpVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { helpVisible = !helpVisible }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    imageVector = if (helpVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (helpVisible) "折叠帮助" else "展开帮助",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        }

        AnimatedVisibility(
            visible = helpVisible,
            enter = slideInVertically { -it / 2 } + fadeIn(),
            exit = slideOutVertically { -it / 2 } + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = helpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim.value - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim.value, 0f)
    )
    Box(
        modifier = modifier
            .background(brush, MaterialTheme.shapes.small)
    )
}

/**
 * 按下时缩放的微交互修饰符。用 graphicsLayer lambda 形式只重绘 layer，不触发外层重组。
 * 应用到启停/刷新/复制等按钮上，增加 "按压反馈" 的高级感。
 */
fun Modifier.pressableScale(
    scale: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val anim by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    graphicsLayer { scaleX = anim; scaleY = anim }
}

@Preview(showBackground = true)
@Composable
fun StatusRowPreview() {
    StatusRow(label = "示例标签", value = "示例值", isCopyable = true)
}

@Preview(showBackground = true)
@Composable
fun ConfigSwitchWithHelpPreview() {
    ConfigSwitchWithHelp(
        label = "示例开关",
        checked = true,
        onCheckedChange = {},
        helpText = "这是一个示例开关的帮助文本。",
        enabled = true
    )
}

@Preview(showBackground = true)
@Composable
fun ConfigSwitchWithInlineHelpPreview() {
    ConfigSwitchWithInlineHelp(
        label = "示例开关",
        checked = true,
        onCheckedChange = {},
        helpText = "这是一个示例开关的帮助文本。",
        enabled = true
    )
}
