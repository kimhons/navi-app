package com.navi.data.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceAssistantService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false
    
    // State flows
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()
    
    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error.asStateFlow()
    
    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }
    
    // MARK: - Initialization
    
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } else {
            _error.value = VoiceError.RecognitionNotAvailable
        }
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTextToSpeechReady = true
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }
    
    // MARK: - Speech Recognition
    
    fun startListening() {
        if (_isListening.value) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            _transcribedText.value = ""
            _error.value = null
        } catch (e: Exception) {
            _error.value = VoiceError.RecognitionFailed(e.message)
        }
    }
    
    fun stopListening() {
        if (!_isListening.value) return
        
        speechRecognizer?.stopListening()
        _isListening.value = false
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }
        
        override fun onBeginningOfSpeech() {}
        
        override fun onRmsChanged(rmsdB: Float) {}
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            _isListening.value = false
        }
        
        override fun onError(error: Int) {
            _isListening.value = false
            _error.value = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> VoiceError.AudioError
                SpeechRecognizer.ERROR_CLIENT -> VoiceError.ClientError
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PermissionDenied
                SpeechRecognizer.ERROR_NETWORK -> VoiceError.NetworkError
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NoMatch
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.RecognizerBusy
                SpeechRecognizer.ERROR_SERVER -> VoiceError.ServerError
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.SpeechTimeout
                else -> VoiceError.Unknown
            }
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                _transcribedText.value = text
                processVoiceCommand(text)
            }
            _isListening.value = false
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                _transcribedText.value = matches[0]
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    // MARK: - Command Processing
    
    private fun processVoiceCommand(command: String) {
        _isProcessing.value = true
        
        val intent = analyzeIntent(command)
        executeCommand(intent, command)
    }
    
    private fun analyzeIntent(command: String): VoiceIntent {
        val lowercased = command.lowercase()
        
        return when {
            lowercased.contains("navigate") || lowercased.contains("take me") || lowercased.contains("directions") -> {
                VoiceIntent.Navigate(extractLocation(command))
            }
            lowercased.contains("find") || lowercased.contains("search") || lowercased.contains("look for") -> {
                VoiceIntent.Search(extractSearchQuery(command))
            }
            lowercased.contains("add stop") || lowercased.contains("add waypoint") || lowercased.contains("stop at") -> {
                VoiceIntent.AddWaypoint(extractLocation(command))
            }
            lowercased.contains("avoid tolls") -> {
                VoiceIntent.AvoidTolls(true)
            }
            lowercased.contains("avoid highways") -> {
                VoiceIntent.AvoidHighways(true)
            }
            lowercased.contains("fastest route") -> {
                VoiceIntent.FastestRoute
            }
            lowercased.contains("traffic") || lowercased.contains("how's traffic") -> {
                VoiceIntent.CheckTraffic
            }
            lowercased.contains("eta") || lowercased.contains("arrival time") || lowercased.contains("how long") -> {
                VoiceIntent.GetETA
            }
            lowercased.contains("nearby") || lowercased.contains("near me") -> {
                VoiceIntent.FindNearby(extractCategory(command))
            }
            lowercased.contains("save") || lowercased.contains("bookmark") -> {
                VoiceIntent.SaveLocation(extractLocationName(command))
            }
            lowercased.contains("cancel") || lowercased.contains("stop navigation") -> {
                VoiceIntent.CancelNavigation
            }
            lowercased.contains("mute") -> {
                VoiceIntent.MuteVoice
            }
            lowercased.contains("unmute") -> {
                VoiceIntent.UnmuteVoice
            }
            lowercased.contains("share") && lowercased.contains("location") -> {
                VoiceIntent.ShareLocation
            }
            else -> VoiceIntent.Unknown(command)
        }
    }
    
    private fun executeCommand(intent: VoiceIntent, originalCommand: String) {
        when (intent) {
            is VoiceIntent.Navigate -> {
                speak("Searching for ${intent.destination}")
                VoiceCommandBroadcaster.sendNavigateCommand(context, intent.destination)
            }
            is VoiceIntent.Search -> {
                speak("Searching for ${intent.query}")
                VoiceCommandBroadcaster.sendSearchCommand(context, intent.query)
            }
            is VoiceIntent.AddWaypoint -> {
                speak("Adding ${intent.location} as a waypoint")
                VoiceCommandBroadcaster.sendAddWaypointCommand(context, intent.location)
            }
            is VoiceIntent.AvoidTolls -> {
                speak(if (intent.avoid) "Avoiding tolls" else "Including tolls in route")
                VoiceCommandBroadcaster.sendAvoidTollsCommand(context, intent.avoid)
            }
            is VoiceIntent.AvoidHighways -> {
                speak(if (intent.avoid) "Avoiding highways" else "Including highways in route")
                VoiceCommandBroadcaster.sendAvoidHighwaysCommand(context, intent.avoid)
            }
            VoiceIntent.FastestRoute -> {
                speak("Calculating fastest route")
                VoiceCommandBroadcaster.sendFastestRouteCommand(context)
            }
            VoiceIntent.CheckTraffic -> {
                speak("Checking traffic conditions")
                VoiceCommandBroadcaster.sendCheckTrafficCommand(context)
            }
            VoiceIntent.GetETA -> {
                speak("Calculating estimated time of arrival")
                VoiceCommandBroadcaster.sendGetETACommand(context)
            }
            is VoiceIntent.FindNearby -> {
                speak("Finding nearby ${intent.category}")
                VoiceCommandBroadcaster.sendFindNearbyCommand(context, intent.category)
            }
            is VoiceIntent.SaveLocation -> {
                val locationName = intent.name ?: "this location"
                speak("Saving $locationName")
                VoiceCommandBroadcaster.sendSaveLocationCommand(context, intent.name)
            }
            VoiceIntent.CancelNavigation -> {
                speak("Canceling navigation")
                VoiceCommandBroadcaster.sendCancelNavigationCommand(context)
            }
            VoiceIntent.MuteVoice -> {
                speak("Voice guidance muted")
                VoiceCommandBroadcaster.sendMuteCommand(context)
            }
            VoiceIntent.UnmuteVoice -> {
                speak("Voice guidance unmuted")
                VoiceCommandBroadcaster.sendUnmuteCommand(context)
            }
            VoiceIntent.ShareLocation -> {
                speak("Sharing your current location")
                VoiceCommandBroadcaster.sendShareLocationCommand(context)
            }
            is VoiceIntent.Unknown -> {
                speak("I'm not sure how to help with that. Try saying 'Navigate to' followed by a location, or 'Find nearby' followed by what you're looking for.")
            }
        }
        
        _isProcessing.value = false
    }
    
    // MARK: - Text Extraction
    
    private fun extractLocation(command: String): String {
        val keywords = listOf("navigate to", "take me to", "directions to", "add stop at", "stop at")
        for (keyword in keywords) {
            val index = command.lowercase().indexOf(keyword)
            if (index != -1) {
                return command.substring(index + keyword.length).trim()
            }
        }
        return command
    }
    
    private fun extractSearchQuery(command: String): String {
        val keywords = listOf("find", "search for", "look for")
        for (keyword in keywords) {
            val index = command.lowercase().indexOf(keyword)
            if (index != -1) {
                return command.substring(index + keyword.length).trim()
            }
        }
        return command
    }
    
    private fun extractCategory(command: String): String {
        val categories = listOf("restaurants", "gas stations", "parking", "hotels", "cafes", "atms", "pharmacies", "hospitals")
        for (category in categories) {
            if (command.lowercase().contains(category)) {
                return category
            }
        }
        return "places"
    }
    
    private fun extractLocationName(command: String): String? {
        val index = command.lowercase().indexOf("save")
        if (index != -1) {
            val name = command.substring(index + 4).trim()
            return if (name.isEmpty()) null else name
        }
        return null
    }
    
    // MARK: - Text-to-Speech
    
    fun speak(text: String) {
        _lastResponse.value = text
        
        if (isTextToSpeechReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NaviTTS")
        }
    }
    
    fun stopSpeaking() {
        textToSpeech?.stop()
    }
    
    // MARK: - Cleanup
    
    fun cleanup() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }
}

// MARK: - Voice Intent Sealed Class
sealed class VoiceIntent {
    data class Navigate(val destination: String) : VoiceIntent()
    data class Search(val query: String) : VoiceIntent()
    data class AddWaypoint(val location: String) : VoiceIntent()
    data class AvoidTolls(val avoid: Boolean) : VoiceIntent()
    data class AvoidHighways(val avoid: Boolean) : VoiceIntent()
    object FastestRoute : VoiceIntent()
    object CheckTraffic : VoiceIntent()
    object GetETA : VoiceIntent()
    data class FindNearby(val category: String) : VoiceIntent()
    data class SaveLocation(val name: String?) : VoiceIntent()
    object CancelNavigation : VoiceIntent()
    object MuteVoice : VoiceIntent()
    object UnmuteVoice : VoiceIntent()
    object ShareLocation : VoiceIntent()
    data class Unknown(val command: String) : VoiceIntent()
}

// MARK: - Voice Error Sealed Class
sealed class VoiceError {
    object PermissionDenied : VoiceError()
    object RecognitionNotAvailable : VoiceError()
    object AudioError : VoiceError()
    object ClientError : VoiceError()
    object NetworkError : VoiceError()
    object NoMatch : VoiceError()
    object RecognizerBusy : VoiceError()
    object ServerError : VoiceError()
    object SpeechTimeout : VoiceError()
    object Unknown : VoiceError()
    data class RecognitionFailed(val message: String?) : VoiceError()
    
    fun getMessage(): String = when (this) {
        is PermissionDenied -> "Microphone permission denied. Please enable it in Settings."
        is RecognitionNotAvailable -> "Speech recognition is not available on this device."
        is AudioError -> "Audio recording error."
        is ClientError -> "Client error occurred."
        is NetworkError -> "Network error. Please check your connection."
        is NoMatch -> "No speech detected. Please try again."
        is RecognizerBusy -> "Speech recognizer is busy. Please try again."
        is ServerError -> "Server error occurred."
        is SpeechTimeout -> "Speech timeout. Please try again."
        is Unknown -> "An unknown error occurred."
        is RecognitionFailed -> "Recognition failed: ${message ?: "Unknown reason"}"
    }
}

// MARK: - Voice Command Broadcaster
object VoiceCommandBroadcaster {
    private const val ACTION_PREFIX = "com.navi.VOICE_COMMAND"
    
    fun sendNavigateCommand(context: Context, destination: String) {
        val intent = Intent("$ACTION_PREFIX.NAVIGATE").apply {
            putExtra("destination", destination)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendSearchCommand(context: Context, query: String) {
        val intent = Intent("$ACTION_PREFIX.SEARCH").apply {
            putExtra("query", query)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendAddWaypointCommand(context: Context, location: String) {
        val intent = Intent("$ACTION_PREFIX.ADD_WAYPOINT").apply {
            putExtra("location", location)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendAvoidTollsCommand(context: Context, avoid: Boolean) {
        val intent = Intent("$ACTION_PREFIX.AVOID_TOLLS").apply {
            putExtra("avoid", avoid)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendAvoidHighwaysCommand(context: Context, avoid: Boolean) {
        val intent = Intent("$ACTION_PREFIX.AVOID_HIGHWAYS").apply {
            putExtra("avoid", avoid)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendFastestRouteCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.FASTEST_ROUTE")
        context.sendBroadcast(intent)
    }
    
    fun sendCheckTrafficCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.CHECK_TRAFFIC")
        context.sendBroadcast(intent)
    }
    
    fun sendGetETACommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.GET_ETA")
        context.sendBroadcast(intent)
    }
    
    fun sendFindNearbyCommand(context: Context, category: String) {
        val intent = Intent("$ACTION_PREFIX.FIND_NEARBY").apply {
            putExtra("category", category)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendSaveLocationCommand(context: Context, name: String?) {
        val intent = Intent("$ACTION_PREFIX.SAVE_LOCATION").apply {
            putExtra("name", name)
        }
        context.sendBroadcast(intent)
    }
    
    fun sendCancelNavigationCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.CANCEL_NAVIGATION")
        context.sendBroadcast(intent)
    }
    
    fun sendMuteCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.MUTE")
        context.sendBroadcast(intent)
    }
    
    fun sendUnmuteCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.UNMUTE")
        context.sendBroadcast(intent)
    }
    
    fun sendShareLocationCommand(context: Context) {
        val intent = Intent("$ACTION_PREFIX.SHARE_LOCATION")
        context.sendBroadcast(intent)
    }
}
