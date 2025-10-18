package com.easytier.app.ui.common // 新的包名

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