package com.easytier.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip


/**
 * 一个通用的“标签-值”对显示行。
 *
 * @param label 左侧的标签文本。
 * @param value 右侧的值文本。
 * @param modifier 可选的修饰符。
 * @param isCopyable 如果为 true，则允许长按复制值到剪贴板。
 */
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            //如果可复制，则添加长按/点击事件
            .then(
                if (isCopyable) {
                    Modifier.combinedClickable(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(value))
                            Toast.makeText(context, "'$value' 已复制", Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(value))
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
            fontWeight = FontWeight.SemiBold, // 标签加粗
            color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用稍弱的颜色
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}


/**
 * 一个带有帮助图标和工具提示的开关组件。
 * 帮助提示通过【点击】图标来触发。
 *
 * @param label 开关旁边的文本标签。
 * @param checked 开关的当前状态 (开/关)。
 * @param onCheckedChange 开关状态改变时的回调。
 * @param helpText 当用户与帮助图标交互时显示的详细说明。
 * @param enabled 开关是否可交互。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSwitchWithHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String,
    enabled: Boolean
) {
    val tooltipState = rememberTooltipState(isPersistent = true) // isPersistent = true 允许我们手动控制显示/隐藏
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))

        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    modifier = Modifier.padding(8.dp).widthIn(max = 300.dp)
                ) {
                    Text(helpText)
                }
            },
            state = tooltipState
        ) {
            // 为 Icon 添加 clickable 修饰符
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "帮助: $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    scope.launch {
                        // 手动切换 Tooltip 的显示状态
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
            enabled = enabled
        )
    }
}



/**
 * 一个带有【内联可展开】帮助文本的开关组件。
 * 这种实现方式不会锁定UI或阻止滚动。
 *
 * @param label 开关旁边的文本标签。
 * @param checked 开关的当前状态 (开/关)。
 * @param onCheckedChange 开关状态改变时的回调。
 * @param helpText 点击后展开显示的详细说明。
 * @param enabled 开关是否可交互。
 */
@Composable
fun ConfigSwitchWithInlineHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String,
    enabled: Boolean
) {
    // 使用 rememberSaveable 可以在屏幕旋转或滚动后保留展开状态
    var helpVisible by rememberSaveable { mutableStateOf(false) }

    // 整体使用 Column 布局，上方是开关，下方是可展开的帮助文本
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        // --- 开关和触发器行 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 让整行都可点击，以切换帮助文本的可见性
                .clip(RoundedCornerShape(8.dp))
                .clickable { helpVisible = !helpVisible }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 将 Text 和 Icon 包裹在一个带 weight 的 Row 中
            // 这样可以确保它们作为一个整体来占据左侧空间
            Row(
                modifier = Modifier.weight(1f), // 1. 让这个组合体占据所有剩余空间
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2. 告诉 Text 组件，如果空间不足，应该是它自己换行或被压缩
                Text(label, modifier = Modifier.weight(1f, fill = false))

                // 3. 图标现在总是有空间来显示自己
                Icon(
                    imageVector = if (helpVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (helpVisible) "折叠帮助" else "展开帮助",
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 开关本身不参与点击展开的逻辑，通过 Spacer 隔开
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }

        // --- 可动画显示的帮助文本 ---
        AnimatedVisibility(
            visible = helpVisible,
            enter = slideInVertically { -it / 2 } + fadeIn(),
            exit = slideOutVertically { -it / 2 } + fadeOut()
        ) {
            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp, bottom = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusRowPreview() {
    StatusRow(
        label = "示例标签",
        value = "示例值",
        isCopyable = true
    )
}


@Preview(showBackground = true)
@Composable
fun ConfigSwitchWithHelpPreview() {
    ConfigSwitchWithHelp(
        label = "示例开关",
        checked = true,
        onCheckedChange = {},
        helpText = "这是一个示例开关的帮助文本，用于演示如何使用这个组件。",
        enabled = true
    )
}


@Preview(showBackground = true)
@Composable
fun ConfigSwitchWitlineHelp() {
    ConfigSwitchWithInlineHelp(
        label = "示例开关",
        checked = true,
        onCheckedChange = {},
        helpText = "这是一个示例开关的帮助文本，用于演示如何使用这个组件。",
        enabled = true
    )
}
