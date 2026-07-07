package com.hodoor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = Room.databaseBuilder(applicationContext, HodoorDb::class.java, "hodoor.db").build()
        setContent {
            val vm: HodoorViewModel = viewModel(factory = HodoorViewModel.factory(db))
            MaterialTheme {
                AttendanceScreen(
                    state = vm.state,
                    onNameChange = vm::onNameChange,
                    onStartChange = vm::onStartChange,
                    onEndChange = vm::onEndChange,
                    onOvertimeHoursChange = vm::onOvertimeHoursChange,
                    onCheckIn = { authenticateAndRun { vm.checkIn() } },
                    onCheckOut = { authenticateAndRun { vm.checkOut() } }
                )
            }
        }
    }

    private fun authenticateAndRun(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد الهوية")
            .setSubtitle("سجل حضور/انصراف بالبصمة")
            .setNegativeButtonText("إلغاء")
            .build()
        prompt.authenticate(promptInfo)
    }
}

@Composable
private fun AttendanceScreen(
    state: StateFlow<UiState>,
    onNameChange: (String) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onOvertimeHoursChange: (String) -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    val uiState by state.collectAsState()
    var currentDate by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        currentDate = LocalDate.now().toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تسجيل حضور وانصراف", style = MaterialTheme.typography.headlineSmall)
        Text("تاريخ اليوم: $currentDate")

        OutlinedTextField(
            value = uiState.employeeName,
            onValueChange = onNameChange,
            label = { Text("اسم الموظف") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.shiftStart,
            onValueChange = onStartChange,
            label = { Text("بداية الدوام (HH:mm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.shiftEnd,
            onValueChange = onEndChange,
            label = { Text("نهاية الدوام (HH:mm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.overtimeAfterHours,
            onValueChange = onOvertimeHoursChange,
            label = { Text("يبدأ أوفر تايم بعد كام ساعة") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCheckIn) { Text("حضور بالبصمة") }
            Button(onClick = onCheckOut) { Text("انصراف بالبصمة") }
        }

        Text(uiState.message, color = MaterialTheme.colorScheme.primary)

        Text("السجل", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.records) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("الموظف: ${r.employeeName}")
                        Text("حضور: ${r.checkIn}")
                        Text("انصراف: ${r.checkOut ?: "-"}")
                        Text("أوفر تايم: ${"%.2f".format(r.overtimeHours)} ساعة")
                    }
                }
            }
        }
    }
}

class HodoorViewModel(private val db: HodoorDb) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.dao().observeAll().collect { list ->
                _state.update { it.copy(records = list) }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(employeeName = v) }
    fun onStartChange(v: String) = _state.update { it.copy(shiftStart = v) }
    fun onEndChange(v: String) = _state.update { it.copy(shiftEnd = v) }
    fun onOvertimeHoursChange(v: String) = _state.update { it.copy(overtimeAfterHours = v) }

    fun checkIn() {
        val s = _state.value
        if (s.employeeName.isBlank()) {
            _state.update { it.copy(message = "اكتب اسم الموظف الأول") }
            return
        }
        viewModelScope.launch {
            db.dao().insert(
                AttendanceRecord(
                    employeeName = s.employeeName.trim(),
                    date = LocalDate.now().toString(),
                    checkIn = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                )
            )
            _state.update { it.copy(message = "تم تسجيل الحضور") }
        }
    }

    fun checkOut() {
        val s = _state.value
        if (s.employeeName.isBlank()) {
            _state.update { it.copy(message = "اكتب اسم الموظف الأول") }
            return
        }
        viewModelScope.launch {
            val latest = db.dao().latestOpenRecord(s.employeeName.trim(), LocalDate.now().toString()).first()
            if (latest == null) {
                _state.update { it.copy(message = "لا يوجد حضور مفتوح لهذا الموظف اليوم") }
                return@launch
            }

            val now = LocalDateTime.now()
            val end = parseTime(s.shiftEnd) ?: LocalTime.of(17, 0)
            val overtimeAfterHours = s.overtimeAfterHours.toDoubleOrNull() ?: 8.0
            val checkInDateTime = LocalDateTime.parse(latest.checkIn, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val workedHours = Duration.between(checkInDateTime, now).toMinutes() / 60.0
            val overtimeByHours = (workedHours - overtimeAfterHours).coerceAtLeast(0.0)
            val overtimeByEndTime = if (now.toLocalTime().isAfter(end)) {
                Duration.between(end, now.toLocalTime()).toMinutes() / 60.0
            } else 0.0
            val overtime = maxOf(overtimeByHours, overtimeByEndTime)

            db.dao().updateCheckout(
                id = latest.id,
                checkOut = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                overtimeHours = overtime
            )
            _state.update { it.copy(message = "تم تسجيل الانصراف. الأوفر تايم: ${"%.2f".format(overtime)} ساعة") }
        }
    }

    companion object {
        fun factory(db: HodoorDb) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HodoorViewModel(db) as T
        }
    }
}

data class UiState(
    val employeeName: String = "",
    val shiftStart: String = "09:00",
    val shiftEnd: String = "17:00",
    val overtimeAfterHours: String = "8",
    val message: String = "",
    val records: List<AttendanceRecord> = emptyList()
)

private fun parseTime(value: String): LocalTime? = runCatching {
    LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
}.getOrNull()

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeName: String,
    val date: String,
    val checkIn: String,
    val checkOut: String? = null,
    val overtimeHours: Double = 0.0
)

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY id DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<AttendanceRecord>>

    @Insert
    suspend fun insert(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE employeeName = :employee AND date = :date AND checkOut IS NULL ORDER BY id DESC LIMIT 1")
    fun latestOpenRecord(employee: String, date: String): kotlinx.coroutines.flow.Flow<AttendanceRecord?>

    @Query("UPDATE attendance_records SET checkOut = :checkOut, overtimeHours = :overtimeHours WHERE id = :id")
    suspend fun updateCheckout(id: Long, checkOut: String, overtimeHours: Double)
}

@Database(entities = [AttendanceRecord::class], version = 1, exportSchema = false)
abstract class HodoorDb : RoomDatabase() {
    abstract fun dao(): AttendanceDao
}
