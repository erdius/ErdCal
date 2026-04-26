package com.example.helloworld

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val initialNote by viewModel.eventNote.collectAsState()
    var note by remember { mutableStateOf(initialNote) }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.updateEventNote(note)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextMMD(
                        text = "Add Notes",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
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
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            TextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                placeholder = { TextMMD("Enter your notes here...", color = Color.LightGray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(fontSize = 18.sp, color = Color.Black)
            )
        }
    }
}
