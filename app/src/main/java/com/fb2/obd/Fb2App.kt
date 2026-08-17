package com.fb2.obd

import android.app.Application
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Process-scoped ViewModel store so the ELM poll / value LOG survive the
 * Nakamichi (or phone) killing [MainActivity] to reclaim RAM. Torque keeps
 * logging in a process-level session; activity-scoped ViewModels do not.
 */
class Fb2App : Application(), ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private val appStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appStore

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(this)
}
