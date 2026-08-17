package com.fb2.obd.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fb2.obd.obd.PidCategory
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.SensorPickerReading
import com.fb2.obd.obd.SensorPickerReadings
import com.fb2.obd.obd.SensorReadKind
import com.fb2.obd.obd.VehicleSnapshot

/** Torque-style greens/cyan so readable sensors jump out on a dark full-screen list. */
private val PickerBg = Color(0xFF000000)
private val PickerHeader = Color(0xFF101010)
private val PickerLiveBg = Color(0xFF1B7A32)
private val PickerLiveBar = Color(0xFF2E9A44)
private val PickerCyan = Color(0xFF7EC8E3)
private val PickerMuted = Color(0xFF8A8A8A)
private val PickerDivider = Color(0xFF3A3A3A)
private val PickerChipOn = Color(0xFF1B7A32)
private val PickerChipOff = Color(0xFF1C1C1C)

private sealed class PickerFilter {
    data object All : PickerFilter()
    data object Readable : PickerFilter()
    data class Category(val cat: PidCategory) : PickerFilter()
}

/**
 * Full-screen sensor browser (replaces the tiny AlertDialog picker).
 * Live-green rows mean the ECU/ELM already answered with a value — tap to add.
 */
@Composable
fun SensorPickerDialog(
    catalog: List<PidDefinition>,
    snapshot: VehicleSnapshot,
    probeById: Map<String, PidProbeResult>,
    scanning: Boolean,
    extraValues: Map<String, String> = emptyMap(),
    restoreLabel: String? = null,
    onRestore: (() -> Unit)? = null,
    onPick: (PidDefinition) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        DisposableEffect(Unit) {
            onOpen()
            onDispose { onClose() }
        }
        SensorPickerContent(
            catalog = catalog,
            snapshot = snapshot,
            probeById = probeById,
            scanning = scanning,
            extraValues = extraValues,
            restoreLabel = restoreLabel,
            onRestore = onRestore,
            onPick = onPick,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorPickerContent(
    catalog: List<PidDefinition>,
    snapshot: VehicleSnapshot,
    probeById: Map<String, PidProbeResult>,
    scanning: Boolean,
    extraValues: Map<String, String> = emptyMap(),
    restoreLabel: String? = null,
    onRestore: (() -> Unit)? = null,
    onPick: (PidDefinition) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<PickerFilter>(PickerFilter.All) }

    val readings = remember(catalog, snapshot, probeById, extraValues) {
        catalog.associate { pid ->
            pid.id to SensorPickerReadings.resolve(pid, snapshot, probeById, extraValues)
        }
    }
    val liveCount = readings.values.count { it.isReadable }

    val filtered = remember(catalog, readings, query, filter) {
        val currentFilter = filter
        catalog.filter { pid ->
            if (!SensorPickerReadings.matchesQuery(pid, query)) return@filter false
            when (currentFilter) {
                PickerFilter.All -> true
                PickerFilter.Readable -> readings[pid.id]?.isReadable == true
                is PickerFilter.Category -> pid.category == currentFilter.cat
            }
        }
    }

    val grouped = remember(filtered, readings) {
        filtered
            .groupBy { it.category }
            .toSortedMap(compareBy { SensorPickerReadings.categoryLabel(it) })
            .mapValues { (_, pids) ->
                pids.sortedWith(
                    compareBy<PidDefinition> { pid ->
                        when (readings[pid.id]?.kind) {
                            SensorReadKind.LIVE -> 0
                            SensorReadKind.WAITING -> 1
                            SensorReadKind.NONE -> 2
                            null -> 3
                        }
                    }.thenBy { it.label.lowercase() },
                )
            }
    }

    val categoriesPresent = remember(catalog) {
        catalog.map { it.category }.distinct().sortedBy { SensorPickerReadings.categoryLabel(it) }
    }

    Column(
        modifier = modifier.background(PickerBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PickerHeader)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select sensor",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Close",
                    color = PickerCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                text = if (scanning) {
                    "Scanning ECU… $liveCount readable"
                } else {
                    "$liveCount readable · tap a green row to add"
                },
                color = PickerCyan,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Search sensors (name or PID)", color = PickerMuted, fontSize = 14.sp)
                },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PickerLiveBar,
                    unfocusedBorderColor = PickerDivider,
                    cursorColor = PickerCyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip("All", filter is PickerFilter.All) { filter = PickerFilter.All }
                FilterChip("Readable", filter is PickerFilter.Readable) { filter = PickerFilter.Readable }
                categoriesPresent.forEach { cat ->
                    val selected = (filter as? PickerFilter.Category)?.cat == cat
                    FilterChip(SensorPickerReadings.categoryLabel(cat), selected) {
                        filter = PickerFilter.Category(cat)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                text = if (query.isNotBlank()) "No sensors match \"$query\"" else "No sensors in this filter",
                color = PickerMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (restoreLabel != null && onRestore != null && query.isBlank() && filter is PickerFilter.All) {
                    item(key = "restore") {
                        RestoreRow(label = restoreLabel, onClick = onRestore)
                    }
                }
                grouped.forEach { (cat, pids) ->
                    stickyHeader(key = "cat-${cat.name}") {
                        CategoryHeader(
                            title = SensorPickerReadings.categoryLabel(cat),
                            readable = pids.count { readings[it.id]?.isReadable == true },
                            total = pids.size,
                        )
                    }
                    items(pids, key = { it.id }) { pid ->
                        val reading = readings[pid.id] ?: SensorPickerReading(SensorReadKind.WAITING)
                        SensorPickerRow(
                            pid = pid,
                            reading = reading,
                            onClick = { onPick(pid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PickerChipOn else PickerChipOff)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun CategoryHeader(title: String, readable: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$readable / $total readable",
            color = PickerCyan,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RestoreRow(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Restore default", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = PickerCyan, fontSize = 14.sp)
        HorizontalDivider(color = PickerDivider, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SensorPickerRow(
    pid: PidDefinition,
    reading: SensorPickerReading,
    onClick: () -> Unit,
) {
    val live = reading.isReadable
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (live) PickerLiveBg else PickerBg),
    ) {
        if (live) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.28f)
                    .height(56.dp)
                    .background(PickerLiveBar.copy(alpha = 0.45f)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = pid.label,
                color = if (live) Color.White else Color(0xFFC8C8C8),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reading.subtitle,
                color = if (live) PickerCyan else Color(0xFF6EA8C0),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(
            color = if (live) Color(0xFF4CAF50).copy(alpha = 0.45f) else PickerDivider,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
