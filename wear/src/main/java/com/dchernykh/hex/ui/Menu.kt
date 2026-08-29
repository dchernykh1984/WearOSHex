package com.dchernykh.hex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dchernykh.hex.layout.centeredBox
import kotlin.math.roundToInt
import com.dchernykh.hex.layout.Box as LayoutBox

// The start menu and the lines the play screen writes in its caps. A stack of
// lines and buttons centred on the screen, sized from the diameter so the same
// stack fills the same proportion of a 384px watch and a 466px one.

/** Below this a label is unreadable on a watch, so a tight box clips rather than shrink further. */
const val MIN_TEXT_PX = 12f

sealed interface MenuItem {
    val height: Int

    data class Line(
        override val height: Int,
        val color: Color,
        val text: String,
    ) : MenuItem

    data class Action(
        override val height: Int,
        val text: String,
        val onClick: () -> Unit,
    ) : MenuItem

    data class Gap(
        override val height: Int,
    ) : MenuItem
}

/** The type scale of the menu, derived from the screen. */
class MenuMetrics(
    screenSize: Int,
) {
    val big = (screenSize * 0.098f).roundToInt()
    val small = (screenSize * 0.058f).roundToInt()
    val button = (screenSize * 0.098f).roundToInt()
    val gap = (screenSize * 0.016f).roundToInt()
    val maxWidth = screenSize * 0.84f
}

@Composable
fun MenuOverlay(
    screenSize: Int,
    metrics: MenuMetrics,
    items: List<MenuItem>,
) {
    // Opaque and over everything: a menu here replaces the board rather than
    // pausing over it, so a tap cannot land on a cell nobody can see.
    Box(modifier = Modifier.absoluteBox(LayoutBox(0, 0, screenSize, screenSize)).background(ColorBackground))

    val stackHeight = items.sumOf { it.height }
    var y = (screenSize / 2f).roundToInt() - (stackHeight / 2f).roundToInt()
    for (item in items) {
        val box = centeredBox(screenSize, y, item.height, metrics.maxWidth, SCREEN_PADDING)
        when (item) {
            is MenuItem.Gap -> Unit
            is MenuItem.Line -> MenuLine(box, item.color, item.text)
            is MenuItem.Action -> PillButton(box, item.text, item.onClick)
        }
        y += item.height
    }
}

@Composable
fun MenuLine(
    box: LayoutBox,
    color: Color,
    text: String,
    fraction: Float = 0.72f,
) {
    Box(modifier = Modifier.absoluteBox(box), contentAlignment = Alignment.Center) {
        FittedText(text = text, color = color, boxHeight = box.h, boxWidth = box.w, fraction = fraction)
    }
}

@Composable
fun PillButton(
    box: LayoutBox,
    text: String,
    onClick: () -> Unit,
    label: String = text,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .absoluteBox(box)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (pressed) ColorButtonPressed else ColorButton)
                .pressable(interactionSource, label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FittedText(text = text, color = ColorText, boxHeight = box.h, boxWidth = box.w, fraction = 0.46f)
    }
}

/**
 * Text sized to fit the box it is in.
 *
 * Zepp OS drew text at exactly the size it was given and clipped the rest, so a
 * label chosen for the four letters of "Easy" cut its Russian counterpart in half.
 * Compose measures instead, and steps the size down only as far as the real glyphs
 * require.
 */
@Composable
fun FittedText(
    text: String,
    color: Color,
    boxHeight: Int,
    boxWidth: Int,
    fraction: Float,
    weight: FontWeight = FontWeight.Normal,
) {
    val density = LocalDensity.current
    val ceilingPx = maxOf(boxHeight * fraction, MIN_TEXT_PX)
    BasicText(
        text = text,
        modifier = Modifier.absoluteWidth(boxWidth),
        style = TextStyle(color = color, fontWeight = weight, textAlign = TextAlign.Center),
        maxLines = 1,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = with(density) { MIN_TEXT_PX.toSp() },
                maxFontSize = with(density) { ceilingPx.toSp() },
            ),
    )
}
