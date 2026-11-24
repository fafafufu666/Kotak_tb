package com.fallbracelet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fallbracelet.data.AppMode
import com.fallbracelet.ui.pasien.PasienApp
import com.fallbracelet.ui.pasien.PasienViewModel
import com.fallbracelet.ui.pasien.PilihModeScreen
import com.fallbracelet.ui.pmo.PmoApp

@Composable
fun App(vm: AppViewModel, pasien: PasienViewModel, enableBluetooth: () -> Unit) {
    val settings by vm.settings.collectAsState()
    when (settings.mode) {
        null -> PilihModeScreen(vm::setMode)
        AppMode.CAREGIVER -> PmoApp(vm) // PRD 6.2, the same screen as the relay's web page
        AppMode.WEARER -> PasienApp(pasien, vm, enableBluetooth) // PRD 6.1
    }
}
