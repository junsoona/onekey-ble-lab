package com.onekey.blelab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class EditorState(
    val configuration: AdvertisingConfiguration = AdvertisingConfiguration(),
    val saved: AdvertisingConfiguration? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

internal class AdvertiserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutableEditor = MutableStateFlow(EditorState())
    val editor = mutableEditor.asStateFlow()
    var pendingExport: String? = null

    init {
        load(initial = true)
    }

    fun edit(transform: (AdvertisingConfiguration) -> AdvertisingConfiguration) {
        mutableEditor.update {
            if (it.busy) it else it.copy(configuration = transform(it.configuration), message = null)
        }
    }

    fun load(initial: Boolean = false) {
        updateSettings("저장한 설정을 읽지 못했습니다.") {
            val saved = repository.load()
            EditorState(saved, saved, message = if (initial) null else "저장한 설정을 불러왔습니다.")
        }
    }

    fun save() {
        val configuration = editor.value.configuration
        if (configuration.validate().packet == null) return
        updateSettings("설정을 저장하지 못했습니다.") {
            repository.save(configuration)
            EditorState(configuration, configuration, message = "설정을 저장했습니다.")
        }
    }

    private fun updateSettings(failure: String, operation: suspend () -> EditorState) {
        if (editor.value.busy) return
        mutableEditor.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                mutableEditor.value = operation()
            } catch (error: IOException) {
                mutableEditor.update { it.copy(busy = false, message = failure) }
                DiagnosticLogger.record(failure, error)
            }
        }
    }
}
