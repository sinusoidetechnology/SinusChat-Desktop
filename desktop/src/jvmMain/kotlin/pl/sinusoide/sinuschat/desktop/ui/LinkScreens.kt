package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings

@Composable
fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SinusColors.surfaceHigh)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = SinusColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(LocalStrings.current.ok, color = SinusColors.primary) }
    }
}

@Composable
fun SearchingScreen(state: LinkState.Searching, onRepair: () -> Unit) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(SinusColors.background).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!state.rejected) {
            CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(24.dp))
        }
        Text(
            if (state.rejected) s.rejectedTitle else s.searching(state.phoneName),
            color = SinusColors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.rejected) s.rejectedHint else s.searchingHint,
            color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )
        Spacer(Modifier.height(28.dp))
        if (state.rejected) {
            Button(
                onClick = onRepair,
                colors = ButtonDefaults.buttonColors(containerColor = SinusColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) { Text(s.reconnect) }
        } else {
            TextButton(onClick = onRepair) { Text(s.otherPhone, color = SinusColors.primary) }
        }
    }
}

