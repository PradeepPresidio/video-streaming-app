package com.example.reelbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reelbox.ui.theme.RbAccent
import com.example.reelbox.ui.theme.ReelBoxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ReelBox)
        super.onCreate(savedInstanceState)
        setContent {
            ReelBoxTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(900); showSplash = false }
                if (showSplash) SplashContent() else PlaceholderHome()
            }
        }
    }
}

@Composable
fun SplashContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
            Text("ReelBox", color = RbAccent, fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PlaceholderHome() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text("Home", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
    }
}
