package org.eu.freex.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    // 读取 SharedPrefs
    val prefs = remember {
        context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    }
    // 使用 State 管理开关状态
    var useRoot by remember {
        mutableStateOf(prefs.getBoolean("use_root", false))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "系统设置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Root 模式开关项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // 点击整行也能切换
                    val newState = !useRoot
                    useRoot = newState
                    prefs.edit { putBoolean("use_root", newState) }
                    Toast.makeText(context, "模式已切换，下次启动生效", Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Root 模式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "使用 Root 权限执行点击 (需设备已 Root)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = useRoot,
                onCheckedChange = { newState ->
                    useRoot = newState
                    prefs.edit { putBoolean("use_root", newState) }
                    Toast.makeText(context, "模式已切换，下次启动生效", Toast.LENGTH_SHORT).show()
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 这里可以继续添加其他设置项...
    }
}