package com.neochildclinic.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.R
import com.neochildclinic.core.designsystem.ClinicBlue
import kotlinx.coroutines.delay

/**
 * Full-screen animated launch/splash screen.
 *
 * Animation sequence:
 * 1. Logo scales 30% → 100%        (0.00–0.70 s)
 * 2. "Neo Child Clinic" arc reveal (0.70–1.80 s)
 * 3. Tagline script reveal          (1.20–2.00 s)
 * 4. Hold completed composition     (2.00–2.70 s)
 * 5. Transition to main app         (2.70–3.00 s)
 */
@Composable
fun SplashScreen(onAnimationComplete: () -> Unit) {
    // --- Colours derived from logo / theme ---
    val nameBlue = ClinicBlue
    val taglineRed = Color(0xFFD32F2F)

    // --- Fonts ---
    val albertusFont = FontFamily(Font(R.font.albertus_mt, FontWeight.Normal))
    val dancingFont = try {
        FontFamily(Font(R.font.dancing_script, FontWeight.Normal))
    } catch (_: Exception) {
        FontFamily.Cursive
    }

    // --- Logo scale ---
    val logoProgress = remember { Animatable(0f) }
    // --- Name clip (0 = fully hidden, 1 = fully revealed) ---
    val nameClip = remember { Animatable(0f) }
    // --- Tagline clip ---
    val taglineClip = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 1 – logo scale 0.30 → 1.00 over 700 ms
        logoProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = Easing { it })
        )
        // Phase 2 – clinic-name arc reveal (starts at 700 ms, runs 1100 ms)
        nameClip.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = Easing { it })
        )

        delay(500) // Hold the completed composition
        onAnimationComplete()
    }

    // Phase 3 – tagline starts slightly after the name begins, runs 800 ms
    LaunchedEffect(Unit) {
        delay(500) // starts at ~500 ms into the animation
        taglineClip.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = Easing { it })
        )
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ---------- Logo ----------
            val scale = 0.30f + (logoProgress.value * 0.70f)
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Clinic Logo",
                modifier = Modifier
                    .width(160.dp)
                    .height(160.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---------- Texts ----------
            SplashScreenTexts(
                nameClipProgress = nameClip.value,
                taglineClipProgress = taglineClip.value,
                nameBlue = nameBlue,
                taglineRed = taglineRed,
                albertusFont = albertusFont,
                dancingFont = dancingFont,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun SplashScreenTexts(
    nameClipProgress: Float,
    taglineClipProgress: Float,
    nameBlue: Color,
    taglineRed: Color,
    albertusFont: FontFamily,
    dancingFont: FontFamily,
    modifier: Modifier = Modifier
) {
    val nameText = "Neo Child Clinic"
    val taglineText = "offering nurturing care"

    val nameStyle = TextStyle(
        fontFamily = albertusFont,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = nameBlue,
        letterSpacing = 0.5.sp
    )
    val taglineStyle = TextStyle(
        fontFamily = dancingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = taglineRed,
        letterSpacing = 0.3.sp
    )

    val nameMeasurer = rememberTextMeasurer()
    val taglineMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .width(300.dp)
            .height(90.dp)
    ) {
        // --- "Neo Child Clinic" along a gentle upward arc ---
        val nameResult = nameMeasurer.measure(nameText, nameStyle)
        val nameWidth = nameResult.size.width.toFloat()
        val nameLayoutWidth = size.width
        val nameStartX = (nameLayoutWidth - nameWidth) / 2f
        val arcMaxOffset = 6.dp.toPx()
        val textHeight = nameResult.size.height.toFloat()

        clipRect(
            left = 0f,
            top = 0f,
            right = nameLayoutWidth * nameClipProgress.coerceIn(0f, 1f),
            bottom = textHeight + arcMaxOffset
        ) {
            nameResult.multiPara.forEach { paragraph ->
                var charX = nameStartX
                for (i in paragraph.runs.indices) {
                    val run = paragraph.runs[i]
                    val runText = run.text
                    for (ch in runText) {
                        val charResult = nameMeasurer.measure(ch.toString(), nameStyle)
                        val charWidth = charResult.size.width.toFloat()
                        val normalizedPos = if (nameWidth > 0f) (charX - nameStartX) / nameWidth else 0f
                        val yOffset = 4f * arcMaxOffset * (normalizedPos - 0.5f) * (normalizedPos - 0.5f) - arcMaxOffset

                        drawText(
                            textLayoutResult = charResult,
                            topLeft = Offset(charX, yOffset)
                        )
                        charX += charWidth
                    }
                }
            }
        }

        // --- "offering nurturing care" (script reveal, left-to-right) ---
        val tagResult = taglineMeasurer.measure(taglineText, taglineStyle)
        val tagWidth = tagResult.size.width.toFloat()
        val tagLayoutWidth = size.width
        val tagStartX = (tagLayoutWidth - tagWidth) / 2f
        val tagHeight = tagResult.size.height.toFloat()
        val taglineY = textHeight + arcMaxOffset + 6.dp.toPx()

        clipRect(
            left = 0f,
            top = taglineY,
            right = tagLayoutWidth * taglineClipProgress.coerceIn(0f, 1f),
            bottom = taglineY + tagHeight
        ) {
            drawText(
                textLayoutResult = tagResult,
                topLeft = Offset(tagStartX, taglineY)
            )
        }
    }
}
