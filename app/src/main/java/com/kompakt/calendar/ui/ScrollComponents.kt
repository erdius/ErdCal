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
import androidx.compose.material3.MaterialTheme
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
    // pointerInput(Unit) installs the gesture-detecting coroutine exactly
    // once, so a plain captured Boolean would freeze at whatever value it
    // had on the first composition (before the list has measured any
    // items). rememberUpdatedState keeps the check reading the latest value.
    val isScrollableState = rememberUpdatedState(isScrollable)
    return this.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = { isDragging = false }
        ) { _, dragAmount ->
            if (!isDragging && isScrollableState.value) {
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
                tint = if (canScrollBackward) Color.Black else MaterialTheme.colorScheme.outline
            )
        }

        Canvas(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .border(0.5.dp, Color.Black, RoundedCornerShape(4.dp))
        ) {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@Canvas

            val totalItems = layoutInfo.totalItemsCount
            val firstItem = visibleItems.first()
            val lastItem = visibleItems.last()

            val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportSize <= 0) return@Canvas

            // Estimate total content height based on average item size
            val visibleItemsHeight = lastItem.offset + lastItem.size - firstItem.offset
            val averageItemSize = visibleItemsHeight.toFloat() / visibleItems.size
            val estimatedTotalSize = (averageItemSize * totalItems)
                .coerceAtLeast(viewportSize.toFloat())

            // If we can scroll, we want the thumb to be smaller than the track
            val isActuallyScrollable = state.canScrollForward || state.canScrollBackward
            val sliderFraction = if (isActuallyScrollable) {
                (viewportSize.toFloat() / estimatedTotalSize).coerceIn(0.1f, 0.9f)
            } else {
                1f
            }

            val sliderHeight = (size.height * sliderFraction).coerceAtLeast(16.dp.toPx())
            val maxOffset = size.height - sliderHeight

            // Smooth scroll fraction calculation
            val currentScrollOffset = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset
            val maxScrollOffset = (estimatedTotalSize - viewportSize).coerceAtLeast(1f)
            val scrollFraction = (currentScrollOffset / maxScrollOffset).coerceIn(0f, 1f)

            val sliderTop = maxOffset * scrollFraction

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
                tint = if (canScrollForward) Color.Black else MaterialTheme.colorScheme.outline
            )
        }
    }
}
