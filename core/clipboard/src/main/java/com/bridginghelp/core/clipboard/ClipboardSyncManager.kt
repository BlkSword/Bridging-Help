package com.bridginghelp.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import com.bridginghelp.core.model.*
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 剪贴板同步状态
 */
sealed class ClipboardSyncState {
    data object Idle : ClipboardSyncState()
    data class Syncing(val sequence: Long) : ClipboardSyncState()
    data class Error(
        val error: ClipboardSyncError,
        val message: String
    ) : ClipboardSyncState()
}

/**
 * 剪贴板同步错误
 */
sealed class ClipboardSyncError {
    data object AccessDenied : ClipboardSyncError()
    data object InvalidFormat : ClipboardSyncError()
    data object SizeLimit : ClipboardSyncError()
    data object Unknown : ClipboardSyncError()
}

/**
 * 剪贴板同步管理器
 * 负责在设备间同步剪贴板内容
 */
@Singleton
class ClipboardSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ClipboardManager.OnPrimaryClipChangedListener {

    companion object {
        private const val TAG = "ClipboardSyncManager"
        private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
        private const val MAX_TEXT_SIZE = 1024 * 1024 // 1MB
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _syncState = MutableStateFlow<ClipboardSyncState>(ClipboardSyncState.Idle)
    val syncState: StateFlow<ClipboardSyncState> = _syncState.asStateFlow()

    private val _clipboardEvents = MutableSharedFlow<ClipboardSyncEvent>()
    val clipboardEvents: SharedFlow<ClipboardSyncEvent> = _clipboardEvents.asSharedFlow()

    private val systemClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var isEnabled = false
    private var lastSequence = 0L
    private var isProcessing = false

    /**
     * 启用剪贴板同步
     */
    fun enable() {
        LogWrapper.d(TAG, "Enabling clipboard sync")
        isEnabled = true
        systemClipboardManager.addPrimaryClipChangedListener(this)
    }

    /**
     * 禁用剪贴板同步
     */
    fun disable() {
        LogWrapper.d(TAG, "Disabling clipboard sync")
        isEnabled = false
        systemClipboardManager.removePrimaryClipChangedListener(this)
    }

    /**
     * 检查是否启用
     */
    fun isSyncEnabled(): Boolean = isEnabled

    /**
     * 当主剪贴板内容改变时调用
     */
    override fun onPrimaryClipChanged() {
        if (!isEnabled || isProcessing) {
            return
        }

        scope.launch {
            processClipboardChange()
        }
    }

    /**
     * 处理剪贴板变化
     */
    private suspend fun processClipboardChange() {
        if (isProcessing) return

        isProcessing = true
        try {
            val clipData = systemClipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) {
                return
            }

            val item = clipData.getItemAt(0)
            val deviceId = getLocalDeviceId()
            val sequence = System.currentTimeMillis()

            // 避免重复同步
            if (sequence <= lastSequence) {
                return
            }

            val clipboardData = when {
                // 文本内容
                item.text != null -> {
                    val text = item.text.toString()
                    if (text.length > MAX_TEXT_SIZE) {
                        LogWrapper.w(TAG, "Text size exceeds limit")
                        return
                    }
                    ClipboardData.Text(text = text)
                }
                // URI内容
                item.uri != null -> {
                    ClipboardData.Uri(uri = item.uri.toString())
                }
                // HTML内容（需要从coerceText中提取）
                item.coerceTextToHtml(context) != null -> {
                    val html = item.coerceTextToHtml(context) ?: ""
                    val text = item.text?.toString() ?: ""
                    ClipboardData.Html(html = html, text = text)
                }
                // 其他情况
                else -> {
                    LogWrapper.d(TAG, "Unsupported clipboard type")
                    return
                }
            }

            lastSequence = sequence

            val event = ClipboardSyncEvent(
                deviceId = deviceId,
                data = clipboardData,
                sequence = sequence
            )

            _syncState.value = ClipboardSyncState.Syncing(sequence)
            _clipboardEvents.emit(event)

            LogWrapper.d(TAG, "Clipboard sync event sent: ${clipboardData::class.simpleName}")

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to process clipboard change", e)
            _syncState.value = ClipboardSyncState.Error(
                ClipboardSyncError.Unknown,
                e.message ?: "Unknown error"
            )
        } finally {
            isProcessing = false
        }
    }

    /**
     * 处理远程剪贴板事件
     */
    suspend fun handleRemoteEvent(event: ClipboardSyncEvent): Result<Unit> {
        LogWrapper.d(TAG, "Handling remote clipboard event from ${event.deviceId}")

        // 避免循环同步
        if (event.deviceId == getLocalDeviceId()) {
            LogWrapper.d(TAG, "Ignoring own clipboard event")
            return Result.Success(Unit)
        }

        return try {
            when (val data = event.data) {
                is ClipboardData.Text -> {
                    setClipboardText(data.text)
                }
                is ClipboardData.Image -> {
                    setClipboardImage(data.data)
                }
                is ClipboardData.Html -> {
                    setClipboardHtml(data.html, data.text)
                }
                is ClipboardData.Uri -> {
                    setClipboardUri(data.uri)
                }
            }

            lastSequence = event.sequence
            LogWrapper.i(TAG, "Remote clipboard synced successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to handle remote clipboard event", e)
            _syncState.value = ClipboardSyncState.Error(
                ClipboardSyncError.Unknown,
                e.message ?: "Unknown error"
            )
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 设置剪贴板文本
     */
    private fun setClipboardText(text: String) {
        val clip = ClipData.newPlainText("clipboard", text)
        systemClipboardManager.setPrimaryClip(clip)
    }

    /**
     * 设置剪贴板图片
     */
    private fun setClipboardImage(imageData: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        // Android剪贴板不支持直接设置图片，需要转换为URI或其他格式
        // 这里可以保存到临时文件并设置URI
        LogWrapper.d(TAG, "Image clipboard sync not fully supported")
    }

    /**
     * 设置剪贴板HTML
     */
    private fun setClipboardHtml(html: String, text: String) {
        val clip = ClipData.newHtmlText("clipboard", text, html)
        systemClipboardManager.setPrimaryClip(clip)
    }

    /**
     * 设置剪贴板URI
     */
    private fun setClipboardUri(uri: String) {
        val clip = ClipData.newRawUri("clipboard", android.net.Uri.parse(uri))
        systemClipboardManager.setPrimaryClip(clip)
    }

    /**
     * 获取当前剪贴板内容
     */
    fun getCurrentClipboard(): ClipboardData? {
        val clipData = systemClipboardManager.primaryClip ?: return null

        val item = clipData.getItemAt(0)
        return when {
            item.text != null -> ClipboardData.Text(text = item.text.toString())
            item.uri != null -> ClipboardData.Uri(uri = item.uri.toString())
            else -> null
        }
    }

    /**
     * 获取本地设备ID
     */
    private fun getLocalDeviceId(): String {
        // TODO: 从LocalDeviceInfo获取
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    /**
     * 清除剪贴板
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun clearClipboard() {
        systemClipboardManager.clearPrimaryClip()
    }

    /**
     * 释放资源
     */
    fun release() {
        disable()
    }
}

/**
 * ClipItem扩展函数 - 获取HTML文本
 */
private fun ClipData.Item.coerceTextToHtml(context: Context): String? {
    return try {
        // 尝试获取HTML格式的文本
        val htmlText = this.htmlText
        if (htmlText != null) {
            return htmlText
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * 剪贴板历史记录
 */
class ClipboardHistory @Inject constructor() {

    private val history = mutableListOf<ClipboardSyncEvent>()
    private val maxHistorySize = 50

    /**
     * 添加记录
     */
    fun addEvent(event: ClipboardSyncEvent) {
        history.add(0, event)
        if (history.size > maxHistorySize) {
            history.removeAt(history.size - 1)
        }
    }

    /**
     * 获取历史记录
     */
    fun getHistory(limit: Int = 20): List<ClipboardSyncEvent> {
        return history.take(limit)
    }

    /**
     * 清空历史记录
     */
    fun clearHistory() {
        history.clear()
    }

    /**
     * 搜索历史记录
     */
    fun searchHistory(query: String): List<ClipboardSyncEvent> {
        return history.filter { event ->
            when (val data = event.data) {
                is ClipboardData.Text -> data.text.contains(query, ignoreCase = true)
                is ClipboardData.Html -> data.text.contains(query, ignoreCase = true)
                is ClipboardData.Uri -> data.uri.contains(query, ignoreCase = true)
                else -> false
            }
        }
    }
}
