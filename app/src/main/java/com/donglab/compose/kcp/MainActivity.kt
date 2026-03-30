package com.donglab.compose.kcp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.donglab.compose.debug.ComposeDebugConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SampleApp()
            }
        }
    }
}

@Composable
fun SampleApp() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            DebugToggle()
            Spacer(modifier = Modifier.height(16.dp))
            SampleList()
        }
    }
}

@Composable
fun DebugToggle() {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Compose Debug Overlay", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Switch(
                checked = ComposeDebugConfig.enabled,
                onCheckedChange = { ComposeDebugConfig.enabled = it },
            )
        }
    }
}

@Composable
fun SampleList() {
    val items = (1..20).map { "Item #$it" }
    LazyColumn {
        items(items) { item ->
            SampleItem(item)
        }
    }
}

@Composable
fun SampleItem(title: String) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
