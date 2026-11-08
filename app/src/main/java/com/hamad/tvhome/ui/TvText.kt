package com.hamad.tvhome.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Material is deliberately absent from this project. Text is rendered with
 * foundation's BasicText and the tvOS type ramp defined in [TvType].
 */
@Composable
fun TvText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = tvType().body,
    color: Color = style.color,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}
