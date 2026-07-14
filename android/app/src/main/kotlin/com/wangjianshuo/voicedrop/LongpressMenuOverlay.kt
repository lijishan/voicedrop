package com.wangjianshuo.voicedrop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

private val PAPER = Color(0xFFFAF6EF)
private val INK = Color(0xFF2A2521)
private val HAIRLINE = Color(0xFFEFE7D9)
private val THICK_SEP = Color(0xFFF1EBE0)
private val HINT = Color(0xFFA79F93)
private val CHEV_TINT = Color(0xFFB8AE9E)
private val BACK_INK = Color(0xFF6B6357)
private val BACK_SEP = Color(0xFFE8DFCD)

private const val MENU_WIDTH = 240
private const val ROW_HEIGHT = 48
private const val BACK_ROW_HEIGHT = 42

@Composable
fun LongpressMenuOverlay(
    anchorY: Float,
    anchorX: Float,
    anchorWidth: Float,
    config: List<MenuNode>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var openSubmenu by remember { mutableStateOf<MenuNode?>(null) }
    var screenHeight by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableFloatStateOf(0f) }
    val scrimColor = INK.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                screenHeight = coords.size.height.toFloat()
                screenWidth = coords.size.width.toFloat()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        )

        if (screenHeight > 0f && screenWidth > 0f) {
            val density = LocalDensity.current
            val menuWidthPx = with(density) { MENU_WIDTH.dp.toPx() }

            val estimatedHeight = estimateCardHeight(openSubmenu, config)

            val spaceBelow = screenHeight - anchorY
            val y = if (spaceBelow >= estimatedHeight + 12f + 16f) {
                anchorY + 12f
            } else {
                (anchorY - 12f - estimatedHeight).coerceAtLeast(16f)
            }
            val x = (anchorX - menuWidthPx / 2)
                .coerceIn(16f, (screenWidth - menuWidthPx - 16f).coerceAtLeast(16f))

            Card(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(MENU_WIDTH.dp),
                shape = RoundedCornerShape(13.dp),
                colors = CardDefaults.cardColors(containerColor = PAPER.copy(alpha = 0.97f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                if (openSubmenu != null) {
                    SubmenuLevel(
                        node = openSubmenu!!,
                        onBack = { openSubmenu = null },
                        onPick = onPick,
                    )
                } else {
                    RootLevel(
                        nodes = config,
                        onOpenSubmenu = { openSubmenu = it },
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

private fun estimateCardHeight(openSubmenu: MenuNode?, config: List<MenuNode>): Float {
    if (openSubmenu != null) {
        val children = openSubmenu.children ?: emptyList()
        return (BACK_ROW_HEIGHT + children.size * ROW_HEIGHT).toFloat()
    }
    var rows = 0
    for (node in config) {
        if (node.type == "submenu" || node.instruction != null) rows++
    }
    return (rows * ROW_HEIGHT).toFloat()
}

@Composable
private fun RootLevel(
    nodes: List<MenuNode>,
    onOpenSubmenu: (MenuNode) -> Unit,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HAIRLINE))
            }
            when {
                node.type == "submenu" && !node.children.isNullOrEmpty() -> {
                    SubmenuRow(node, onOpenSubmenu)
                }
                node.instruction != null -> {
                    ActionRow(node, onPick)
                }
            }
        }
    }
}

@Composable
private fun SubmenuLevel(
    node: MenuNode,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BackRow(node.label, onBack)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BACK_SEP))
        val children = node.children?.filter {
            (it.type == "submenu" && !it.children.isNullOrEmpty()) || it.instruction != null
        } ?: emptyList()
        children.forEachIndexed { index, child ->
            if (index > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HAIRLINE))
            }
            when {
                child.type == "submenu" && !child.children.isNullOrEmpty() -> {
                    SubmenuRow(child) { }
                }
                child.instruction != null -> {
                    ActionRow(child, onPick)
                }
            }
        }
    }
}

@Composable
private fun SubmenuRow(node: MenuNode, onOpen: (MenuNode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .clickable { onOpen(node) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            node.label,
            fontSize = 15.5f.sp,
            color = INK,
            modifier = Modifier.weight(1f),
        )
        val preview = node.children?.take(2)?.joinToString(" · ") { it.label } ?: ""
        Text(
            preview,
            fontSize = 12.5f.sp,
            color = HINT,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = CHEV_TINT,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ActionRow(node: MenuNode, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .clickable { node.instruction?.let { onPick(it) } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(node.label, fontSize = 15.5f.sp, color = INK, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BACK_ROW_HEIGHT.dp)
            .background(THICK_SEP)
            .clickable { onBack() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.KeyboardArrowLeft,
            null,
            tint = BACK_INK,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 13.5f.sp,
            fontWeight = FontWeight.SemiBold,
            color = BACK_INK,
        )
    }
}
