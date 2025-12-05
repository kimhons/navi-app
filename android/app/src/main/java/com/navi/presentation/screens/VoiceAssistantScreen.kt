package com.navi.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navi.data.services.VoiceAssistantService
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun VoiceAssistantScreen(
    onDismiss: () -> Unit,
    voiceService: VoiceAssistantService = hiltViewModel()
) {
    val isListening by voiceService.isListening.collectAsState()
    val transcribedText by voiceService.transcribedText.collectAsState()
    val isProcessing by voiceService.isProcessing.collectAsState()
    val lastResponse by voiceService.lastResponse.collectAsState()
    val error by voiceService.error.collectAsState()
    
    var waveformAmplitudes by remember { mutableStateOf(List(50) { 0.3f }) }
    
    // Waveform animation
    LaunchedEffect(isListening) {
        if (isListening) {
            while (isListening) {
                waveformAmplitudes = List(50) { Random.nextFloat() * 0.7f + 0.3f }
                delay(100)
            }
        } else {
            waveformAmplitudes = List(50) { 0.3f }
        }
    }
    
    // Show error dialog
    error?.let { err ->
        AlertDialog(
            onDismissRequest = { /* error.value = null */ },
            title = { Text("Error") },
            text = { Text(err.getMessage()) },
            confirmButton = {
                TextButton(onClick = { /* error.value = null */ }) {
                    Text("OK")
                }
            }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2563EB).copy(alpha = 0.1f),
                        Color(0xFF2563EB).copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Voice Assistant Icon with pulsing animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Pulsing circles
                if (isListening) {
                    repeat(3) { index ->
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse$index")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, delayMillis = index * 300),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "scale$index"
                        )
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, delayMillis = index * 300),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "alpha$index"
                        )
                        
                        Surface(
                            modifier = Modifier
                                .size((120 + index * 40).dp)
                                .scale(scale),
                            shape = CircleShape,
                            color = Color(0xFF2563EB).copy(alpha = alpha * 0.3f),
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Color(0xFF2563EB).copy(alpha = alpha * 0.3f)
                            )
                        ) {}
                    }
                }
                
                // Main microphone button
                Surface(
                    onClick = {
                        if (isListening) {
                            voiceService.stopListening()
                        } else {
                            voiceService.startListening()
                        }
                    },
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = if (isListening) Color(0xFF2563EB) else Color(0xFF2563EB).copy(alpha = 0.2f),
                    shadowElevation = 20.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = if (isListening) "Listening" else "Tap to speak",
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Waveform visualization
            if (isListening) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveformAmplitudes.forEach { amplitude ->
                        Surface(
                            modifier = Modifier
                                .width(3.dp)
                                .height((amplitude * 60).dp)
                                .padding(horizontal = 1.5.dp),
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFF2563EB)
                        ) {}
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when {
                        isListening -> "Listening..."
                        isProcessing -> "Processing..."
                        else -> "Tap to speak"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (transcribedText.isNotEmpty()) {
                    Text(
                        text = transcribedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                
                if (lastResponse.isNotEmpty() && !isListening) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lastResponse,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Example commands
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Try saying:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ExampleCommand(icon = Icons.Default.LocationOn, text = "Navigate to Starbucks")
                Spacer(modifier = Modifier.height(12.dp))
                ExampleCommand(icon = Icons.Default.Search, text = "Find gas stations nearby")
                Spacer(modifier = Modifier.height(12.dp))
                ExampleCommand(icon = Icons.Default.AddCircle, text = "Add a stop at McDonald's")
                Spacer(modifier = Modifier.height(12.dp))
                ExampleCommand(icon = Icons.Default.DirectionsCar, text = "Avoid tolls")
                Spacer(modifier = Modifier.height(12.dp))
                ExampleCommand(icon = Icons.Default.Schedule, text = "What's my ETA?")
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            voiceService.stopListening()
            voiceService.stopSpeaking()
        }
    }
}

@Composable
fun ExampleCommand(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
