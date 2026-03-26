package com.mhm.frugails

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _currentLanguage = MutableStateFlow("English")
    val currentLanguage = _currentLanguage.asStateFlow()

    private val _theme = MutableStateFlow("Black")
    val theme = _theme.asStateFlow()

    private val _timerMinutes = MutableStateFlow(55)
    val timerMinutes = _timerMinutes.asStateFlow()

    private val _playDing = MutableStateFlow(true)
    val playDing = _playDing.asStateFlow()

    private val _stimulationMode = MutableStateFlow("Both")
    val stimulationMode = _stimulationMode.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn = _keepScreenOn.asStateFlow()

    private val _slowerFlicker = MutableStateFlow(false)
    val slowerFlicker = _slowerFlicker.asStateFlow()

    fun setLanguage(lang: String) { _currentLanguage.value = lang }
    fun setTheme(theme: String) { _theme.value = theme }
    fun setTimer(minutes: Int) { _timerMinutes.value = minutes }
    fun setPlayDing(play: Boolean) { _playDing.value = play }
    fun setStimulationMode(mode: String) { _stimulationMode.value = mode }
    fun setKeepScreenOn(keepOn: Boolean) { _keepScreenOn.value = keepOn }
    fun setSlowerFlicker(slow: Boolean) { _slowerFlicker.value = slow }
}