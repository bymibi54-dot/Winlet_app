@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.winlet.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class StartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        setContent { StartMenu { finish() } }
    }
    override fun onPause() { super.onPause(); if (!isFinishing) finish() }
}

@Composable
fun StartMenu(done: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val list = Session.apps.filter { it.label.contains(query, true) }
    MaterialTheme(colorScheme = darkColorScheme(primary = AccentColor)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(GlassColor)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp)).padding(16.dp)
        ) {
            TextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(0.08f),
                    unfocusedContainerColor = Color.White.copy(0.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text("Tap to open  •  Long-press for options", fontSize = 12.sp, color = Color.White.copy(0.55f))
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.pkg }) { a ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(
                            onClick = { launchApp(ctx, a); done() },
                            onLongClick = { openPanel(ctx, AppMenuActivity::class.java, 280, 450, false, a.pkg) }
                        ).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(a.icon, a.label, Modifier.size(46.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(a.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, color = Color.White)
                    }
                }
            }
        }
    }
}
