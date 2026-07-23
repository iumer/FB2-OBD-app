package com.fb2.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectDialog
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.DebugLogScreen
import com.fb2.obd.ui.SettingsScreen
import com.fb2.obd.ui.ValueLogScreen
import com.fb2.obd.ui.theme.FB2Theme
import kotlinx.coroutines.delay

private enum class Screen { DASHBOARD, SETTINGS, DEBUG_LOG, VALUE_LOG }

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FB2Theme {
                val state by viewModel.uiState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                var screen by remember { mutableStateOf(Screen.DASHBOARD) }
                var showConnect by remember { mutableStateOf(false) }
                var devices by remember { mutableStateOf(emptyList<BtDeviceUi>()) }

                // Refresh ticker so the log screens show live data.
                var tick by remember { mutableIntStateOf(0) }
                LaunchedEffect(screen) {
                    while (screen == Screen.DEBUG_LOG || screen == Screen.VALUE_LOG) {
                        delay(1000L)
                        tick++
                    }
                }

                val enableBtLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        devices = loadBondedDevices()
                        showConnect = true
                    } else {
                        toast("Bluetooth must be on to connect to the adapter")
                    }
                }

                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    if (result.values.all { it }) {
                        openConnect(
                            onNeedEnable = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                            onReady = { devices = it; showConnect = true },
                        )
                    } else {
                        toast("Bluetooth permission is required to connect to the ELM327")
                    }
                }

                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showEstimatedGear = settings.showEstimatedGear,
                        onConnectClick = {
                            val needed = requiredBtPermissions().filter {
                                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (needed.isEmpty()) {
                                openConnect(
                                    onNeedEnable = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                                    onReady = { devices = it; showConnect = true },
                                )
                            } else {
                                permLauncher.launch(needed.toTypedArray())
                            }
                        },
                        onSettingsClick = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onToggleValueLogging = viewModel::setValueLogging,
                        onToggleEstimatedGear = viewModel::setShowEstimatedGear,
                        onOpenDebugLog = { screen = Screen.DEBUG_LOG },
                        onOpenValueLog = { screen = Screen.VALUE_LOG },
                        onBack = { screen = Screen.DASHBOARD },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.DEBUG_LOG -> {
                        val lines = remember(tick) { ObdLogger.debugLines() }
                        DebugLogScreen(
                            lines = lines,
                            onShare = { shareText("FB2 Diag debug log", ObdLogger.debugText()) },
                            onClear = { ObdLogger.clearDebug(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Screen.VALUE_LOG -> {
                        val rows = remember(tick) { ObdLogger.valueRows() }
                        ValueLogScreen(
                            rows = rows,
                            onShare = { shareText("FB2 Diag value log (CSV)", ObdLogger.valuesCsv()) },
                            onClear = { ObdLogger.clearValues(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (showConnect) {
                    ConnectDialog(
                        devices = devices,
                        onPickDevice = { connectTo(it); showConnect = false },
                        onPickDemo = { viewModel.useSource(DemoObdSource()); showConnect = false },
                        onDismiss = { showConnect = false },
                    )
                }
            }
        }
    }

    private fun shareText(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, subject))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun requiredBtPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyList()
        }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun openConnect(onNeedEnable: () -> Unit, onReady: (List<BtDeviceUi>) -> Unit) {
        val adapter = bluetoothAdapter()
        if (adapter != null && !adapter.isEnabled) {
            onNeedEnable()
        } else {
            onReady(loadBondedDevices())
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices(): List<BtDeviceUi> {
        val adapter = bluetoothAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices
            ?.map { BtDeviceUi(runCatching { it.name }.getOrNull().orEmpty(), it.address) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BtDeviceUi) {
        val adapter = bluetoothAdapter() ?: return
        val remote = adapter.getRemoteDevice(device.address)
        viewModel.useSource(Elm327BluetoothSource(remote))
    }
}
