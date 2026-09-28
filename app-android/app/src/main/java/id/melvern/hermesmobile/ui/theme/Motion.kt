package id.melvern.hermesmobile.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** M8 motion: satu keluarga — ease-out emphasized, durasi pendek. */
object Motion {
    /** M3 emphasized decelerate — masuk layar. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** M3 emphasized accelerate — keluar layar. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val NavMs = 220
    const val MessageInMs = 180
    const val PulseMs = 1200
    val NavSlide = 24.dp
    val MessageRise = 8.dp
}

/** "Remove animations" sistem (ANIMATOR_DURATION_SCALE == 0) → tanpa motion. */
fun reduceMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { reduceMotion(ctx) }
}
