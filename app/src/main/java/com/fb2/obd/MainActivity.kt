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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectDialog
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.theme.FB2Theme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FB2Theme {
                val state by viewModel.uiState.collectAsState()
                var showConnect by remember { mutableStateOf(false) }
                var devices by remember { mutableStateOf(emptyList<BtDeviceUi>()) }

                val enableBtLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    devices = loadBondedDevices()
                    showConnect = true
                }

                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    if (result.values.all { it }) {
                        openConnect(
                            onNeedEnable = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                            onReady = { devices = it; showConnect = true },
                        )
                    }
                }

                DashboardScreen(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
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
                )

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
