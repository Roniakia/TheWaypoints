package de.dimskiy.waypoints.platform.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

data class UpdateUiState(val channel: UpdateChannel = UpdateChannel.STABLE, val checking: Boolean = false,
    val downloading: Boolean = false, val progress: Int = 0, val release: AppRelease? = null,
    val apk: File? = null, val lastChecked: Long? = null, val message: String? = null)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(private val updates: AppUpdates) : ViewModel() {
    private val mutable = MutableStateFlow(UpdateUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private val installEvents = Channel<File>(Channel.BUFFERED)
    val installs = installEvents.receiveAsFlow()
    init { check(force = false) }
    fun check(force: Boolean = true) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            mutable.update { it.copy(checking = true, message = null) }
            try {
                mutable.update { it.copy(channel = updates.channel(), lastChecked = updates.lastChecked()) }
                val (performed, release) = updates.check(force)
                val keepApk = mutable.value.release == release && mutable.value.apk?.isFile == true
                if (performed && !keepApk) mutable.value.apk?.delete()
                mutable.update { it.copy(checking = false, lastChecked = updates.lastChecked(),
                    release = if (performed) release else it.release,
                    apk = if (performed && !keepApk) null else it.apk,
                    message = if (performed && release == null) "No newer version in this channel." else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(checking = false, message = "Update check failed. ${failure.message.orEmpty()}") } }
        }
    }
    fun channel(value: UpdateChannel) {
        if (work?.isActive == true || value == mutable.value.channel) return
        work = viewModelScope.launch {
            try {
                updates.channel(value)
                mutable.value.apk?.delete()
                mutable.update { it.copy(channel = value, release = null, apk = null, message = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(message = failure.message) }; return@launch }
            // Finish this preference write before scheduling a fresh check.
            work = null
            check()
        }
    }
    fun download() {
        if (work?.isActive == true) return
        val release = mutable.value.release ?: return
        work = viewModelScope.launch {
            mutable.update { it.copy(downloading = true, progress = 0, message = null, apk = null) }
            try {
                val file = updates.download(release) { progress -> mutable.update { it.copy(progress = progress) } }
                mutable.update { it.copy(downloading = false, apk = file, message = "Download verified. Ready to install.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(downloading = false, message = "Download failed. ${failure.message.orEmpty()}") } }
        }
    }
    fun cancel() { work?.cancel(); mutable.update { it.copy(checking = false, downloading = false, message = "Download cancelled. You can retry.") } }
    fun install() {
        val current = mutable.value
        val file = current.apk ?: return
        val release = current.release ?: return
        viewModelScope.launch {
            try {
                // Recheck immediately before exposing the private APK to the installer.
                updates.verify(file, release)
                installEvents.send(file)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(apk = null, message = "Cannot install. ${failure.message.orEmpty()}") } }
        }
    }
    fun message(text: String) { mutable.update { it.copy(message = text) } }
}
