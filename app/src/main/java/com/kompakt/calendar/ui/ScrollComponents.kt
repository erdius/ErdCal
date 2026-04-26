package com.kompakt.calendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val SCROLL_STEP = 4

@Composable
fun Modifier.eInkVerticalScroll(
    state: LazyListState,
    scope: CoroutineScope,
    isScrollable: Boolean
): Modifier {
    var isDragging by remember { mutableStateOf(false) }
    return this.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = { isDragging = false }
        ) { _, dragAmount ->
            if (!isDragging && isScrollable) {
                isDragging = true
                val direction = if (dragAmount > 0) -1 else 1
                val newIdx = (state.firstVisibleItemIndex + direction * SCROLL_STEP)
                    .coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                scope.launch { state.scrollToItem(newIdx) }
            }
        }
    }
}

@Composable
fun EInkScrollbar(
    state: LazyListState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val canScrollForward by remember { derivedStateOf { state.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { state.canScrollBackward } }
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = {
                val newIdx = (state.firstVisibleItemIndex - SCROLL_STEP).coerceAtLeast(0)
                scope.launch { state.scrollToItem(newIdx) }
            },
            modifier = Modifier.size(32.dp).padding(top = 8.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll up",
                modifier = Modifier.size(20.dp),
                tint = if (canScrollBackward) Color.Black else Color.LightGray
            )
        }

        Canvas(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .border(0.5.dp, Color.Black, RoundedCornerShape(4.dp))
        ) {
            val totalItems = state.layoutInfo.totalItemsCount
            val visibleItemsCount = state.layoutInfo.visibleItemsInfo.size
            val firstVisibleIndex = state.firstVisibleItemIndex
            
            val safeTotal = totalItems.coerceAtLeast(1)
            val sliderFraction = (visibleItemsCount.toFloat() / safeTotal).coerceIn(0.1f, 1f)
            val sliderHeight = (size.height * sliderFraction).coerceAtLeast(16.dp.toPx())
            val maxOffset = size.height - sliderHeight
            val scrollFraction = if (safeTotal > visibleItemsCount)
                firstVisibleIndex.toFloat() / (safeTotal - visibleItemsCount).toFloat()
            else 0f
            val sliderTop = (maxOffset * scrollFraction).coerceIn(0f, maxOffset)

            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(0f, sliderTop),
                size = Size(size.width, sliderHeight),
                cornerRadius = CornerRadius(size.width / 2, size.width / 2)
            )
        }

        IconButton(
            onClick = {
                val newIdx = (state.firstVisibleItemIndex + SCROLL_STEP)
                    .coerceAtMost(state.layoutInfo.totalItemsCount - 1)
                scope.launch { state.scrollToItem(newIdx) }
            },
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll down",
                modifier = Modifier.size(20.dp),
                tint = if (canScrollForward) Color.Black else Color.LightGray
            )
        }
    }
}
