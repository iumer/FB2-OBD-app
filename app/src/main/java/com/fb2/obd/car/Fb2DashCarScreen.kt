package com.fb2.obd.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Android Auto main Dash: RPM / Speed / Gear + the same sensor tiles as the phone
 * Dash, with LOG and CONNECT actions.
 */
class Fb2DashCarScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                VehicleLiveStore.dash.collect {
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val dash = VehicleLiveStore.dash.value
        val list = ItemList.Builder()

        list.addItem(
            Row.Builder()
                .setTitle("RPM  ${dash.rpm}")
                .addText(dash.statusLine)
                .build(),
        )
        list.addItem(
            Row.Builder()
                .setTitle("Speed  ${dash.speedKmh} km/h")
                .addText("Gear ${dash.gear}" + if (dash.gearBadge.isNotBlank()) " · ${dash.gearBadge}" else "")
                .build(),
        )

        // Mirror phone Dash tiles (host may truncate long lists while driving).
        dash.tiles.take(16).forEach { tile ->
            val valueLine = buildString {
                append(tile.value)
                if (tile.unit.isNotBlank()) append(" ${tile.unit}")
                if (!tile.status.isNullOrBlank()) append(" · ${tile.status}")
            }
            list.addItem(
                Row.Builder()
                    .setTitle(tile.label)
                    .addText(valueLine)
                    .build(),
            )
        }

        val logTitle = if (dash.logging) "STOP LOG" else "LOG"
        val logAction = Action.Builder()
            .setTitle(logTitle)
            .setOnClickListener {
                VehicleLiveStore.onToggleLogging?.invoke()
                    ?: CarToast.makeText(carContext, "Open FB2 Diag on phone first", CarToast.LENGTH_SHORT).show()
                invalidate()
            }
            .build()

        val connectAction = Action.Builder()
            .setTitle(dash.connectLabel)
            .setOnClickListener(
                ParkedOnlyOnClickListener.create {
                    VehicleLiveStore.onConnectRequest?.invoke()
                        ?: CarToast.makeText(carContext, "Open FB2 Diag on phone first", CarToast.LENGTH_SHORT).show()
                    CarToast.makeText(
                        carContext,
                        if (dash.sourceIsLive) "Already on live ELM" else "Use phone to pick ELM / Demo",
                        CarToast.LENGTH_SHORT,
                    ).show()
                    invalidate()
                },
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("FB2 DIAG")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(logAction)
                    .addAction(connectAction)
                    .build(),
            )
            .setSingleList(list.build())
            .build()
    }
}
