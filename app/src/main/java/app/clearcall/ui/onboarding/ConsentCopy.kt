package app.clearcall.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The disclosure shown during setup and again from Settings. Plain language, no legal advice. */
object ConsentCopy {
    const val TITLE = "Recording and the law"

    val POINTS: List<String> = listOf(
        "Laws on recording calls differ by country, state and situation. Many places require " +
            "everyone on the call to agree; others only require one person.",
        "You are responsible for knowing and following the rules where you and the other person are.",
        "ClearCall does not tell the other person they are being recorded. Tell them yourself, " +
            "and stop recording if they don't agree.",
        "Recordings never leave this device unless you share, export or back them up yourself. " +
            "ClearCall has no internet access at all.",
        "Android decides what apps may capture. On most phones ClearCall can only use the " +
            "microphone, so it may record only your side of the call.",
    )

    const val CHECKBOX =
        "I understand, and I will only record calls where I have the consent the law requires."
}

@Composable
fun ConsentPoints(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConsentCopy.POINTS.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(point, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
            }
        }
    }
}
