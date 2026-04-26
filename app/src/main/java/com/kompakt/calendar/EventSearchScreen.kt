package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarEvent
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.eInkVerticalScroll
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSearchScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            scope.launch {
                results = viewModel.search(searchQuery)
                isSearching = false
            }
        } else {
            results = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { TextMMD("Search events...", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                    )
                },
                actions = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black)
            )

            if (searchQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextMMD(
                        "Search for events by title, location, or notes",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            } else if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextMMD("No events found", fontSize = 16.sp, color = Color.Gray)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .eInkVerticalScroll(listState, scope, isScrollable),
                        userScrollEnabled = false
                    ) {
                        items(results) { event ->
                            EventSearchResultItem(
                                event = event,
                                onClick = { navController.navigate("event_detail/${event.id}") }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                    if (isScrollable) {
                        EInkScrollbar(state = listState, scope = scope)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSearchResultItem(event: CalendarEvent, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm a"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextMMD(
                text = event.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            TextMMD(
                text = if (event.allDay)
                    event.start.toLocalDate().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US))
                else
                    event.start.format(DateTimeFormatter.ofPattern("EEE, d MMM · $timePattern", Locale.US)),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
            if (!event.location.isNullOrBlank()) {
                TextMMD(
                    text = event.location!!,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.LightGray)
                .padding(top = 8.dp)
        )
    }
}
