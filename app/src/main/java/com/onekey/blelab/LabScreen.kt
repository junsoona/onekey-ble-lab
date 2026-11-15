package com.onekey.blelab

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private val lightColors = lightColorScheme(
    primary = Color(0xFF00665D),
    tertiary = Color(0xFF3B6177),
    primaryContainer = Color(0xFFBEEFE6),
    onPrimaryContainer = Color(0xFF003A35),
    surface = Color(0xFFF5FAF8),
    surfaceContainer = Color(0xFFE8F0ED),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF83D8CA),
    tertiary = Color(0xFF9ECBE4),
    primaryContainer = Color(0xFF194B46),
    onPrimaryContainer = Color(0xFFBEEFE6),
    surface = Color(0xFF101715),
    surfaceContainer = Color(0xFF1C2724),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabScreen(
    editor: EditorState,
    bluetooth: BluetoothStatus,
    session: SessionState,
    entries: List<DiagnosticEntry>,
    edit: ((AdvertisingConfiguration) -> AdvertisingConfiguration) -> Unit,
    save: () -> Unit,
    load: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    requestBluetooth: () -> Unit,
    openBluetooth: () -> Unit,
    openAppSettings: () -> Unit,
    openNotificationSettings: () -> Unit,
    applyDeviceName: (String) -> Unit,
    copyLogs: () -> Unit,
    exportLogs: () -> Unit,
) {
    val configuration = editor.configuration
    val validation = remember(configuration) { configuration.validate() }
    val editable = session.phase == Phase.IDLE && !editor.busy
    val validNameOnDevice = configuration.localName.isEmpty() || configuration.localName == bluetooth.name
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("OneKey BLE Lab") },
                        navigationIcon = {
                            Icon(
                                painterResource(R.drawable.ic_notification), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp).size(24.dp),
                            )
                        },
                    )
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("송신") })
                        Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("진단") })
                    }
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (selectedTab == 0) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (session.phase == Phase.TRANSMITTING)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = if (session.phase == Phase.TRANSMITTING)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Text(session.phase.label, style = MaterialTheme.typography.headlineSmall)
                                    StatusLine("Bluetooth", bluetooth.label)
                                    StatusLine("BLE 광고", bluetooth.capability.label)
                                    StatusLine("Foreground Service", if (session.foreground) "실행 중" else "중지")
                                    session.lastError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = start,
                                        enabled = editable && validation.packet != null && validNameOnDevice &&
                                            bluetooth.capability == Capability.SUPPORTED,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("광고 시작") }
                                    OutlinedButton(
                                        onClick = stop,
                                        enabled = session.phase != Phase.IDLE && session.phase != Phase.STOPPING,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("광고 중지") }
                                }
                                if (!bluetooth.hasPermissions && bluetooth.capability != Capability.UNSUPPORTED) {
                                    OutlinedButton(onClick = requestBluetooth) { Text("주변 기기 권한 허용") }
                                    Caption("권한 요청이 열리지 않으면 앱 설정에서 주변 기기를 허용하세요.")
                                }
                                if (bluetooth.hasPermissions && bluetooth.state != BluetoothAdapter.STATE_ON &&
                                    bluetooth.capability != Capability.UNSUPPORTED
                                ) {
                                    OutlinedButton(onClick = openBluetooth) { Text("Bluetooth 설정") }
                                }
                                if (!bluetooth.notificationsEnabled) {
                                    TextButton(onClick = openNotificationSettings) { Text("송신 알림 허용") }
                                }
                            }
                        }
                        item {
                            Section("광고 패킷") {
                                Caption("Legacy · 비연결형 · 기본값은 테스트 데이터입니다.")
                                val deviceName = bluetooth.name
                                OutlinedTextField(
                                    value = configuration.localName,
                                    onValueChange = { text -> edit { it.copy(localName = text) } },
                                    label = { Text("Local Name") },
                                    singleLine = true,
                                    enabled = editable,
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = validation.nameError != null,
                                    supportingText = {
                                        Text(validation.nameError ?: if (deviceName != null)
                                            "기기 Bluetooth 이름 · $deviceName"
                                        else "비워두면 생략 · UTF-8 최대 26 B")
                                    },
                                )
                                if (!validNameOnDevice && validation.nameError == null) {
                                    Caption("지정한 이름이 기기 Bluetooth 이름과 일치해야 광고를 시작할 수 있습니다.")
                                    OutlinedButton(
                                        onClick = { pendingName = configuration.localName },
                                        enabled = editable && bluetooth.state == BluetoothAdapter.STATE_ON,
                                    ) { Text("기기 이름 적용") }
                                }
                                OutlinedTextField(
                                    value = configuration.manufacturerIdHex,
                                    onValueChange = { text -> edit { it.copy(manufacturerIdHex = text) } },
                                    label = { Text("Manufacturer ID") },
                                    singleLine = true,
                                    enabled = editable,
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = validation.idError != null,
                                    supportingText = { Text(validation.idError ?: "16비트 HEX · 예시 FFFF") },
                                    keyboardOptions = hexKeyboard,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                )
                                OutlinedTextField(
                                    value = configuration.manufacturerDataHex,
                                    onValueChange = { text -> edit { it.copy(manufacturerDataHex = text) } },
                                    label = { Text("Manufacturer Data (HEX)") },
                                    minLines = 2,
                                    maxLines = 3,
                                    enabled = editable,
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = validation.payloadError != null,
                                    supportingText = { Text(validation.payloadError ?: "ID를 제외한 데이터 · 최대 27 B") },
                                    keyboardOptions = hexKeyboard,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                )
                                validation.packet?.let {
                                    Caption("ADV ${it.advertisingBytes}/31 B    ·    SCAN_RSP ${it.scanResponseBytes}/31 B")
                                }
                                Choice(
                                    title = "광고 주기",
                                    options = AdvertisingMode.entries,
                                    selected = configuration.mode,
                                    enabled = editable,
                                    label = { "${it.label} · 약 ${it.intervalMs} ms" },
                                    onSelected = { mode -> edit { it.copy(mode = mode) } },
                                )
                                Choice(
                                    title = "TX Power",
                                    options = TxPower.entries,
                                    selected = configuration.txPower,
                                    enabled = editable,
                                    label = { it.label },
                                    onSelected = { power -> edit { it.copy(txPower = power) } },
                                )
                            }
                        }
                        item {
                            Section("저장 및 자동 시작") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("재부팅 후 자동 시작", style = MaterialTheme.typography.titleSmall)
                                        Caption("저장한 설정으로 시도합니다. 첫 잠금 해제와 Bluetooth 및 권한 상태, 시스템 정책에 따라 시작이 생략될 수 있습니다.")
                                    }
                                    Switch(
                                        checked = configuration.startOnBoot,
                                        onCheckedChange = { checked -> edit { it.copy(startOnBoot = checked) } },
                                        enabled = editable,
                                    )
                                }
                                if ((editor.saved?.startOnBoot ?: false) != configuration.startOnBoot) {
                                    Caption("자동 시작 변경을 적용하려면 저장하세요.")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = save, enabled = editable && validation.packet != null,
                                    ) { Text("저장") }
                                    TextButton(onClick = load, enabled = editable) { Text("불러오기") }
                                }
                                editor.message?.let { Caption(it) }
                            }
                        }
                        item {
                            Section("백그라운드 실행") {
                                Caption("Galaxy에서 송신이 끊기면 앱 배터리를 ‘제한 없음’으로 바꾸고, 배터리의 백그라운드 사용 제한에서 절전 예외 앱에 추가한 뒤 다시 측정하세요.")
                                TextButton(onClick = openAppSettings) { Text("앱 설정 열기") }
                            }
                        }
                    } else {
                        item {
                            Section("최근 송신 세션") {
                                StatusLine("시작 결과", session.lastStartResult ?: "—")
                                StatusLine("시작 시각", formatTime(session.startedAt))
                                StatusLine("중지 요청 시각", formatTime(session.stoppedAt))
                                StatusLine("마지막 오류", session.lastError ?: "—")
                                session.summary?.let {
                                    SelectionContainer {
                                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    }
                                }
                            }
                        }
                        item {
                            Section("최근 로그") {
                                Caption("현재 앱 프로세스에서 최대 100개를 보관합니다.")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = copyLogs, enabled = entries.isNotEmpty()) { Text("복사") }
                                    OutlinedButton(onClick = exportLogs, enabled = entries.isNotEmpty()) { Text("내보내기") }
                                }
                                if (entries.isEmpty()) Caption("기록된 이벤트가 없습니다.")
                            }
                        }
                        items(entries.asReversed()) { entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                SelectionContainer {
                                    Text(entry.format(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        pendingName?.let { name ->
            AlertDialog(
                onDismissRequest = { pendingName = null },
                title = { Text("기기 Bluetooth 이름 변경") },
                text = { Text("기기 전체 Bluetooth 이름을 “$name”(으)로 변경합니다. 광고를 중지해도 기기 이름이 유지되며 Bluetooth 설정에서 다시 바꿀 수 있습니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingName = null
                        applyDeviceName(name)
                    }) { Text("기기 이름 변경") }
                },
                dismissButton = { TextButton(onClick = { pendingName = null }) { Text("취소") } },
            )
        }
    }
}

private val hexKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Characters,
    autoCorrectEnabled = false,
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$title · ${label(selected)}")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelected(option)
                })
            }
        }
    }
}
