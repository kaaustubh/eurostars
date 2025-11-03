package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class BluetoothPairingViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothPairingViewModel::class.java)) {
            return BluetoothPairingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/** Convenience getter for Composables */
@Composable
fun bluetoothPairingViewModel(): BluetoothPairingViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(factory = BluetoothPairingViewModelFactory(app))
}

