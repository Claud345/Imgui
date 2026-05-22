package com.lc.hello.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A custom LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner designed
 * specifically to host Jetpack Compose components inside a background or foreground Android Service
 * without crashing. It manages the lifecycle states manually and cleans up the view model store
 * when destroyed.
 */
class ServiceViewLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
