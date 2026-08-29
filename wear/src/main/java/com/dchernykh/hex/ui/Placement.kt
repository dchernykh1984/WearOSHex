package com.dchernykh.hex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.dchernykh.hex.layout.Box as LayoutBox

/**
 * Place a composable at a box worked out in screen pixels.
 *
 * The layout is computed in whole pixels from the screen diameter, exactly as on
 * the watch this was ported from, so it is placed in pixels too: converting each
 * edge to dp and back would round it twice and pull the board off centre.
 */
fun Modifier.absoluteBox(box: LayoutBox): Modifier =
    this
        .offset { IntOffset(box.x, box.y) }
        .layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(box.w, box.h))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

/**
 * Anything a finger presses, with its own pressed look and nothing else.
 *
 * No ripple: every control here already changes colour under the finger, which is
 * what the Zepp OS original did.
 */
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    this
        .semantics { contentDescription = label }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )

/** A width in screen pixels, without a round trip through dp. */
fun Modifier.absoluteWidth(width: Int): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
