package com.bridginghelp.core.common.result

/**
 * 通用结果封装
 * 用于表示操作的成功或失败状态
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: ErrorEntity) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isLoading: Boolean
        get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun errorOrNull(): ErrorEntity? = when (this) {
        is Error -> error
        else -> null
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (ErrorEntity) -> Unit): Result<T> {
        if (this is Error) action(error)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(error: ErrorEntity): Result<Nothing> = Error(error)
        fun loading(): Result<Nothing> = Loading

        fun <T> catch(block: () -> T): Result<T> = try {
            Success(block())
        } catch (e: Exception) {
            Error(ErrorEntity.fromException(e))
        }

        suspend fun <T> suspendCatch(block: suspend () -> T): Result<T> = try {
            Success(block())
        } catch (e: Exception) {
            Error(ErrorEntity.fromException(e))
        }
    }
}

/**
 * 错误实体
 */
sealed class ErrorEntity {
    data class Network(val message: String, val code: Int? = null) : ErrorEntity()
    data class Permission(val permission: String) : ErrorEntity()
    data class Validation(val message: String) : ErrorEntity()
    data class Unknown(val message: String, val cause: Throwable? = null) : ErrorEntity()

    companion object {
        fun fromException(e: Exception): ErrorEntity = when (e) {
            is SecurityException -> Permission(e.message ?: "Unknown permission error")
            is IllegalArgumentException -> Validation(e.message ?: "Validation error")
            else -> Unknown(e.message ?: "Unknown error", e)
        }
    }
}

/**
 * 扩展函数：将Kotlin Result转换为我们的Result
 */
fun <T> kotlin.Result<T>.toCommonResult(): Result<T> = if (isSuccess) {
    Result.Success(getOrNull()!!)
} else {
    Result.Error(ErrorEntity.Unknown(exceptionOrNull()?.message ?: "Unknown error"))
}

/**
 * 扩展函数：获取错误消息
 */
val ErrorEntity.message: String
    get() = when (this) {
        is ErrorEntity.Network -> message
        is ErrorEntity.Permission -> "Permission denied: $permission"
        is ErrorEntity.Validation -> message
        is ErrorEntity.Unknown -> message
    }
