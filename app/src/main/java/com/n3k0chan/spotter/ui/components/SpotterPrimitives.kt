package com.n3k0chan.spotter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme

/* ─── Tarjeta base ─── */

@Composable
fun SpotterCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    padding: Dp = 16.dp,
    background: Color = SpotterTheme.colors.surface,
    border: Color = SpotterTheme.colors.border,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val base = modifier
        .clip(shape)
        .background(background)
        .border(BorderStroke(1.dp, border), shape)
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = withClick.padding(padding)) { content() }
}

/* ─── Top app bar simple ─── */

@Composable
fun SpotterTopBar(
    title: String,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    large: Boolean = false,
) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (large) 76.dp else 56.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = title,
                style = if (large) SpotterText.title1 else SpotterText.title3,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = SpotterText.small,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

/* ─── Icon button circular tappable ─── */

enum class IconButtonTone { Default, Primary, Danger, Muted }

@Composable
fun SpotterIconButton(
    icon: ImageVector,
    onClick: () -> Unit = {},
    contentDescription: String? = null,
    tone: IconButtonTone = IconButtonTone.Default,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    val c = SpotterTheme.colors
    val (bg, fg) = when (tone) {
        IconButtonTone.Default -> Color.Transparent to c.text
        IconButtonTone.Primary -> c.primarySoft to c.primary
        IconButtonTone.Danger -> Color.Transparent to c.danger
        IconButtonTone.Muted -> Color.Transparent to c.textMuted
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** Atajo para el menú "kebab" (3 puntos verticales) que abunda en el diseño. */
@Composable
fun SpotterKebab(onClick: () -> Unit = {}) {
    SpotterIconButton(
        icon = Icons.Filled.MoreVert,
        onClick = onClick,
        contentDescription = "Más",
        tone = IconButtonTone.Muted,
    )
}

/* ─── Botón "pill-ish" ─── */

enum class SpotterButtonVariant { Filled, Tonal, Outlined, Text, Surface, Danger }

@Composable
fun SpotterButton(
    text: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    variant: SpotterButtonVariant = SpotterButtonVariant.Filled,
    full: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val c = SpotterTheme.colors
    val (bg, fg, borderColor) = when (variant) {
        SpotterButtonVariant.Filled -> Triple(c.primary, c.onPrimary, Color.Transparent)
        SpotterButtonVariant.Tonal -> Triple(c.primarySoft, c.primarySoftText, Color.Transparent)
        SpotterButtonVariant.Outlined -> Triple(Color.Transparent, c.text, c.borderStrong)
        SpotterButtonVariant.Text -> Triple(Color.Transparent, c.primary, Color.Transparent)
        SpotterButtonVariant.Surface -> Triple(c.surface, c.text, c.border)
        SpotterButtonVariant.Danger -> Triple(Color.Transparent, c.danger, c.border)
    }
    val shape = RoundedCornerShape(height / 2)
    val base = modifier
        .let { if (full) it.fillMaxWidth() else it }
        .height(height)
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, borderColor), shape)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 20.dp)
    Row(
        modifier = base,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = SpotterText.bodyMd)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailing, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
    }
}

/* ─── Chip pequeño ─── */

enum class SpotterChipTone { Neutral, Selected, Success }

@Composable
fun SpotterChip(
    text: String,
    leading: ImageVector? = null,
    tone: SpotterChipTone = SpotterChipTone.Neutral,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = SpotterTheme.colors
    val (bg, fg, borderColor) = when (tone) {
        SpotterChipTone.Selected -> Triple(c.primarySoft, c.primarySoftText, Color.Transparent)
        SpotterChipTone.Success -> Triple(Color.Transparent, c.success, c.border)
        SpotterChipTone.Neutral -> Triple(Color.Transparent, c.textMuted, c.border)
    }
    val shape = RoundedCornerShape(8.dp)
    val base = modifier
        .heightIn(min = 32.dp)
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, borderColor), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 4.dp)
    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = SpotterText.smallMd)
    }
}

/* ─── Campo de "input" estático tipo card (Workout) ─── */

@Composable
fun SpotterDataField(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = SpotterTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(c.surfaceMuted)
            .border(BorderStroke(1.dp, c.border), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = SpotterText.small.copy(fontSize = SpotterText.small.fontSize),
            color = c.textMuted,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = SpotterText.numS, color = c.text)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = SpotterText.small, color = c.textMuted)
            }
        }
    }
}
