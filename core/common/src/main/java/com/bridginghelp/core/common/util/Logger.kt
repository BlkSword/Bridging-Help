package com.bridginghelp.core.common.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志级别
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * 日志接口
 */
interface AppLogger {
    fun v(tag: String, message: String, throwable: Throwable? = null)
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 默认日志实现
 */
@Singleton
class DefaultLogger @Inject constructor() : AppLogger {
    private var minLevel = LogLevel.DEBUG

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    override fun v(tag: String, message: String, throwable: Throwable?) {
        if (minLevel <= LogLevel.VERBOSE) {
            if (throwable != null) {
                Log.v(tag, message, throwable)
            } else {
                Log.v(tag, message)
            }
        }
    }

    override fun d(tag: String, message: String, throwable: Throwable?) {
        if (minLevel <= LogLevel.DEBUG) {
            if (throwable != null) {
                Log.d(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        if (minLevel <= LogLevel.INFO) {
            if (throwable != null) {
                Log.i(tag, message, throwable)
            } else {
                Log.i(tag, message)
            }
        }
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (minLevel <= LogLevel.WARN) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (minLevel <= LogLevel.ERROR) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}

/**
 * 日志扩展函数
 */
inline fun <reified T> T.logV(message: String, throwable: Throwable? = null) {
    val tag = T::class.java.simpleName
    LogWrapper.v(tag, message, throwable)
}

inline fun <reified T> T.logD(message: String, throwable: Throwable? = null) {
    val tag = T::class.java.simpleName
    LogWrapper.d(tag, message, throwable)
}

inline fun <reified T> T.logI(message: String, throwable: Throwable? = null) {
    val tag = T::class.java.simpleName
    LogWrapper.i(tag, message, throwable)
}

inline fun <reified T> T.logW(message: String, throwable: Throwable? = null) {
    val tag = T::class.java.simpleName
    LogWrapper.w(tag, message, throwable)
}

inline fun <reified T> T.logE(message: String, throwable: Throwable? = null) {
    val tag = T::class.java.simpleName
    LogWrapper.e(tag, message, throwable)
}

/**
 * 日志包装器（静态访问）
 */
object LogWrapper {
    lateinit var logger: AppLogger

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (::logger.isInitialized) {
            logger.v(tag, message, throwable)
        } else {
            Log.v(tag, message, throwable)
        }
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (::logger.isInitialized) {
            logger.d(tag, message, throwable)
        } else {
            Log.d(tag, message, throwable)
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (::logger.isInitialized) {
            logger.i(tag, message, throwable)
        } else {
            Log.i(tag, message, throwable)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (::logger.isInitialized) {
            logger.w(tag, message, throwable)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (::logger.isInitialized) {
            logger.e(tag, message, throwable)
        } else {
            Log.e(tag, message, throwable)
        }
    }
}
