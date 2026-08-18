package com.fb2.obd.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.ConnectActionPolicy
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.WarnAmber

/** Shared Connect / Disconnect chip colours — Classic + Opt themes. */
fun connectActionColor(kind: ConnectActionPolicy.Kind, accent: Color): Color = when (kind) {
    ConnectActionPolicy.Kind.DISCONNECT,
    ConnectActionPolicy.Kind.RECONNECT -> CritRed
    ConnectActionPolicy.Kind.RETRY -> WarnAmber
    ConnectActionPolicy.Kind.CONNECT -> accent
}

@Composable
fun ConnectActionChip(
    connection: ConnectionState,
    sourceIsLive: Boolean,
    reconnecting: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    textColor: Color? = null,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
) {
    val connect = ConnectActionPolicy.of(connection, sourceIsLive, reconnecting)
    val color = textColor ?: connectActionColor(connect.kind, accent)
    Text(
        text = connect.label,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(surfaceColor)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
