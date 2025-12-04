package com.example.fuelprices.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Data Models ---

// Enum for fuel types
enum class FuelType(val label: String) {
    PETROL("Petrol (E10)"),
    DIESEL("Diesel"),
    PREMIUM_PETROL("Premium (E5)"),
    LPG("LPG")
}

// Enum for sorting options
enum class SortOption(val label: String) {
    PRICE("Price (Low to High)"),
    DISTANCE("Distance (Near to Far)"),
    NAME("Station Name (A-Z)")
}

// Data Model for a single gas station/price
data class FuelPriceItem(
    val id: String,
    val name: String,
    val address: String,
    val price: Double,
    val fuelType: FuelType,
    val distanceKm: Double,
    val isReported: Boolean = false
)

// Data Model for user settings (DataStore)
data class FuelPricesScreenSettings(
    val fuelTypeFilter: FuelType = FuelType.PETROL,
    val maxDistanceKm: Float = 10f,
    val sortBy: SortOption = SortOption.PRICE,
    val isMapMode: Boolean = false
)

// UI State
data class FuelPricesScreenState(
    val isLoading: Boolean = true,
    val fuelPrices: List<FuelPriceItem> = emptyList(),
    val settings: FuelPricesScreenSettings = FuelPricesScreenSettings(),
    val isReportPriceDialogOpen: Boolean = false,
    val isSettingsDialogOpen: Boolean = false,
    val userMessage: String? = null,
    val isBackupSyncing: Boolean = false,
    val isSubscriptionActive: Boolean = false,
    val reportPriceInput: String = "",
    val reportPriceInputError: String? = null
)

// --- 2. Mock Services/Repositories (Stubs for single file) ---

// Mock API Service
interface ApiService {
    suspend fun getAccountStatus(): Boolean
    suspend fun getSubscriptionStatus(): Boolean
    suspend fun triggerBackupSync(): Boolean
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun getAccountStatus(): Boolean { delay(500); return true }
    override suspend fun getSubscriptionStatus(): Boolean { delay(500); return true }
    override suspend fun triggerBackupSync(): Boolean { delay(1000); return true }
}

// Mock DataStore Repository
class SettingsRepository @Inject constructor() {
    private val _settingsFlow = MutableStateFlow(FuelPricesScreenSettings())
    val settingsFlow: StateFlow<FuelPricesScreenSettings> = _settingsFlow.asStateFlow()

    suspend fun updateSettings(newSettings: FuelPricesScreenSettings) {
        delay(100) // Simulate DataStore write delay
        _settingsFlow.value = newSettings
    }
}

// Mock WorkManager Scheduler
class WorkManagerScheduler @Inject constructor() {
    fun scheduleBackupSync() {
        // In a real app, this would schedule a WorkManager job
    }
}

// --- 3. ViewModel ---

@HiltViewModel
class FuelPricesViewModel @Inject constructor(
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val workManagerScheduler: WorkManagerScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(FuelPricesScreenState())
    val state: StateFlow<FuelPricesScreenState> = _state.asStateFlow()

    private val allFuelPrices = mockFuelPrices()

    init {
        viewModelScope.launch {
            // Combine settings flow with data loading
            settingsRepository.settingsFlow
                .onEach { settings ->
                    _state.update { it.copy(settings = settings) }
                    loadFuelPrices(settings)
                }
                .launchIn(viewModelScope)

            // Load initial API status
            val isSubActive = apiService.getSubscriptionStatus()
            _state.update { it.copy(isSubscriptionActive = isSubActive) }
        }
    }

    private fun mockFuelPrices(): List<FuelPriceItem> {
        val names = listOf("Shell", "BP", "Esso", "Texaco", "TotalEnergies")
        val addresses = listOf("123 Main St", "456 Oak Ave", "789 Pine Ln", "101 Elm Rd")
        val fuelTypes = FuelType.entries.toTypedArray()

        return (1..20).map { i ->
            FuelPriceItem(
                id = i.toString(),
                name = names.random(),
                address = addresses.random(),
                price = Random.nextDouble(1.50, 2.20).format(2),
                fuelType = fuelTypes.random(),
                distanceKm = Random.nextDouble(0.5, 20.0).format(1),
                isReported = Random.nextBoolean()
            )
        }
    }

    private fun loadFuelPrices(settings: FuelPricesScreenSettings) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            delay(500) // Simulate network delay

            val filtered = allFuelPrices
                .filter { it.fuelType == settings.fuelTypeFilter }
                .filter { it.distanceKm <= settings.maxDistanceKm }

            val sorted = when (settings.sortBy) {
                SortOption.PRICE -> filtered.sortedBy { it.price }
                SortOption.DISTANCE -> filtered.sortedBy { it.distanceKm }
                SortOption.NAME -> filtered.sortedBy { it.name }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    fuelPrices = sorted
                )
            }
        }
    }

    fun onSettingsChange(newSettings: FuelPricesScreenSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
        }
    }

    fun showSettingsDialog(show: Boolean) {
        _state.update { it.copy(isSettingsDialogOpen = show) }
    }

    fun showReportPriceDialog(show: Boolean) {
        _state.update { it.copy(isReportPriceDialogOpen = show, reportPriceInput = "", reportPriceInputError = null) }
    }

    fun updateReportPriceInput(input: String) {
        _state.update { it.copy(reportPriceInput = input, reportPriceInputError = null) }
    }

    fun submitReportPrice() {
        val input = _state.value.reportPriceInput
        val price = input.toDoubleOrNull()

        if (price == null || price <= 0.5 || price > 5.0) {
            _state.update { it.copy(reportPriceInputError = "Invalid price. Must be between 0.50 and 5.00.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isReportPriceDialogOpen = false, userMessage = "Price reported successfully: $${"%.2f".format(price)}") }
            // In a real app, this would send the report to the backend
        }
    }

    fun triggerBackup() {
        viewModelScope.launch {
            _state.update { it.copy(isBackupSyncing = true) }
            workManagerScheduler.scheduleBackupSync()
            val success = apiService.triggerBackupSync()
            _state.update {
                it.copy(
                    isBackupSyncing = false,
                    userMessage = if (success) "Backup sync completed." else "Backup sync failed."
                )
            }
        }
    }

    fun userMessageShown() {
        _state.update { it.copy(userMessage = null) }
    }

    // Helper function for formatting doubles
    private fun Double.format(digits: Int) = "%.${digits}f".format(this).toDouble()
}

// --- 4. Compose UI ---

val NaviBlue = Color(0xFF2563EB)

@Composable
fun FuelPricesScreen(
    viewModel: FuelPricesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar Feedback
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Fuel Prices", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.showSettingsDialog(true) },
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Navigate FAB
                ExtendedFloatingActionButton(
                    onClick = { /* Handle navigation to map app */ },
                    modifier = Modifier.padding(bottom = 8.dp).semantics { contentDescription = "Navigate to nearest station" },
                    icon = { Icon(Icons.Filled.Navigation, contentDescription = null) },
                    text = { Text("Navigate") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                // Report Price FAB
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showReportPriceDialog(true) },
                    modifier = Modifier.semantics { contentDescription = "Report a new price" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Report Price") },
                    containerColor = NaviBlue,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FuelTypeFilterChips(
                currentFilter = state.settings.fuelTypeFilter,
                onFilterChange = { newType ->
                    viewModel.onSettingsChange(state.settings.copy(fuelTypeFilter = newType))
                }
            )
            FuelPricesList(state = state)
        }
    }

    if (state.isSettingsDialogOpen) {
        SettingsDialog(
            settings = state.settings,
            isBackupSyncing = state.isBackupSyncing,
            isSubscriptionActive = state.isSubscriptionActive,
            onDismiss = { viewModel.showSettingsDialog(false) },
            onSettingsChange = viewModel::onSettingsChange,
            onTriggerBackup = viewModel::triggerBackup
        )
    }

    if (state.isReportPriceDialogOpen) {
        ReportPriceDialog(
            input = state.reportPriceInput,
            error = state.reportPriceInputError,
            onDismiss = { viewModel.showReportPriceDialog(false) },
            onInputChange = viewModel::updateReportPriceInput,
            onSubmit = viewModel::submitReportPrice
        )
    }
}

@Composable
fun FuelTypeFilterChips(
    currentFilter: FuelType,
    onFilterChange: (FuelType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FuelType.entries.forEach { type ->
            FilterChip(
                selected = type == currentFilter,
                onClick = { onFilterChange(type) },
                label = { Text(type.label) },
                leadingIcon = if (type == currentFilter) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NaviBlue.copy(alpha = 0.1f),
                    selectedLabelColor = NaviBlue,
                    selectedLeadingIconColor = NaviBlue
                )
            )
        }
    }
}

@Composable
fun FuelPricesList(state: FuelPricesScreenState) {
    val sortedPrices by remember(state.fuelPrices, state.settings.sortBy) {
        derivedStateOf {
            // Sorting is already done in the ViewModel, but this demonstrates derivedStateOf
            state.fuelPrices
        }
    }

    AnimatedVisibility(
        visible = state.isLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NaviBlue)
        }
    }

    AnimatedVisibility(
        visible = !state.isLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (sortedPrices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stations found for selected filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedPrices, key = { it.id }) { item ->
                    FuelPriceItemCard(item = item)
                }
            }
        }
    }
}

@Composable
fun FuelPriceItemCard(item: FuelPriceItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle item click/details */ },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NaviBlue
                )
                Text(
                    text = item.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.distanceKm} km",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "$${"%.2f".format(item.price)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun SettingsDialog(
    settings: FuelPricesScreenSettings,
    isBackupSyncing: Boolean,
    isSubscriptionActive: Boolean,
    onDismiss: () -> Unit,
    onSettingsChange: (FuelPricesScreenSettings) -> Unit,
    onTriggerBackup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fuel Price Settings") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // Map Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Map View Mode", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = settings.isMapMode,
                        onCheckedChange = { onSettingsChange(settings.copy(isMapMode = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Max Distance Slider
                Text(
                    text = "Max Distance: ${"%.1f".format(settings.maxDistanceKm)} km",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = settings.maxDistanceKm,
                    onValueChange = { onSettingsChange(settings.copy(maxDistanceKm = it)) },
                    valueRange = 1f..50f,
                    steps = 49,
                    colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Sort Dropdown
                SortOptionDropdown(
                    currentSort = settings.sortBy,
                    onSortChange = { onSettingsChange(settings.copy(sortBy = it)) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Backup Sync Button
                Button(
                    onClick = onTriggerBackup,
                    enabled = !isBackupSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                ) {
                    if (isBackupSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Text("Trigger Backup Sync")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Subscription Status
                Text(
                    text = if (isSubscriptionActive) "Subscription: Active" else "Subscription: Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSubscriptionActive) Color.Green.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NaviBlue)
            }
        }
    )
}

@Composable
fun SortOptionDropdown(
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NaviBlue)
    ) {
        Text("Sort By: ${currentSort.label}")
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onSortChange(option)
                    expanded = false
                },
                leadingIcon = {
                    if (option == currentSort) {
                        Icon(Icons.Filled.Done, contentDescription = null, tint = NaviBlue)
                    }
                }
            )
        }
    }
}

@Composable
fun ReportPriceDialog(
    input: String,
    error: String?,
    onDismiss: () -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report New Price") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        // Input validation: only allow digits and one decimal point
                        val filtered = it.filter { char -> char.isDigit() || char == '.' }
                        onInputChange(filtered)
                    },
                    label = { Text("Price (e.g., 1.89)") },
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Enter the current price per liter/gallon.")
                        }
                    },
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A confirmation dialog will appear after submission.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = input.toDoubleOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NaviBlue)
            }
        }
    )
}

// --- 5. Preview and Lifecycle Stub ---

// Mock implementation of collectAsStateWithLifecycle for preview
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    return this.collectAsState()
}

@Preview(showBackground = true)
@Composable
fun PreviewFuelPricesScreen() {
    // Mock ViewModel for Preview
    val mockViewModel = FuelPricesViewModel(
        apiService = MockApiService(),
        settingsRepository = SettingsRepository(),
        workManagerScheduler = WorkManagerScheduler()
    )
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        FuelPricesScreen(viewModel = mockViewModel)
    }
}

// Hilt stubs for compilation in a single file
annotation class HiltViewModel
annotation class Inject
annotation class Preview
// End of Hilt stubs
