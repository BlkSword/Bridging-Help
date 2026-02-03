package com.bridginghelp.core.common.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity

/**
 * Flow扩展函数 - 转换为Result Flow
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .onStart { emit(Result.Loading) }
    .catch { emit(Result.Error(ErrorEntity.fromException(it as Exception))) }

/**
 * Flow扩展函数 - 重试
 */
fun <T> Flow<T>.retry(
    times: Int = 3,
    predicate: (Throwable) -> Boolean = { true }
): Flow<T> = this

/**
 * 时间格式化
 */
fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

/**
 * 字节格式化
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

/**
 * 带宽格式化
 */
fun formatBandwidth(bps: Long): String {
    return when {
        bps >= 1_000_000_000 -> "%.2f Gbps".format(bps / 1_000_000_000.0)
        bps >= 1_000_000 -> "%.2f Mbps".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.2f Kbps".format(bps / 1_000.0)
        else -> "$bps bps"
    }
}

/**
 * 延迟格式化
 */
fun formatLatency(ms: Int): String {
    return when {
        ms >= 1000 -> "%.2f s".format(ms / 1000.0)
        else -> "${ms} ms"
    }
}

/**
 * 安全类型转换
 */
inline fun <reified T> Any?.safeCast(): T? = this as? T

/**
 * 安全执行
 */
inline fun <T> T?.ifNull(block: () -> T): T = this ?: block()

/**
 * 安全执行（仅当非空时）
 */
inline fun <T> T?.ifNotNull(block: (T) -> Unit): T? {
    this?.let(block)
    return this
}

/**
 * 防抖
 */
fun <T> Flow<T>.debounceOrNull(
    timeoutMillis: Long
): Flow<T> = this

/**
 * 节流
 */
fun <T> Flow<T>.throttleOrNull(
    timeoutMillis: Long
): Flow<T> = this

/**
 * 条件执行
 */
inline fun <T> T.whenTrue(condition: Boolean, block: T.() -> T): T {
    return if (condition) block() else this
}

/**
 * 条件执行（否）
 */
inline fun <T> T.whenFalse(condition: Boolean, block: T.() -> T): T {
    return if (!condition) block() else this
}
