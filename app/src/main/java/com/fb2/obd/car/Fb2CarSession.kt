package com.fb2.obd.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class Fb2CarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = Fb2DashCarScreen(carContext)
}
