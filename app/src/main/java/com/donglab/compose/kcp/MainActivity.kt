package com.donglab.compose.kcp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.donglab.compose.debug.ComposeDebugConfig

private const val EXTRA_NAMETAG = "nametag"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 꺼진 채로 시작해 켜는 경우도 확인할 수 있도록: adb shell am start ... --ez nametag false
        ComposeDebugConfig.enabled = intent?.getBooleanExtra(EXTRA_NAMETAG, true) ?: true
        setContent {
            MaterialTheme {
                SampleApp()
            }
        }
    }
}

@Composable
fun SampleApp() {
    Scaffold(containerColor = Color(0xFF0B0B0B)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DebugToggle()
            HomeFeedSample(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DebugToggle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Composable Nametag", color = Color.White)
        Switch(
            checked = ComposeDebugConfig.enabled,
            onCheckedChange = { ComposeDebugConfig.enabled = it },
        )
    }
}
