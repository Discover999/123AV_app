package com.android123av.app.utils

import android.util.Log
import kotlinx.coroutines.CancellationException

object ExceptionHandler {
    
    private const val TAG = "ExceptionHandler"
    
    private var globalExceptionHandler: ((Throwable) -> Unit)? = null
    
    fun setGlobalExceptionHandler(handler: (Throwable) -> Unit) {
        globalExceptionHandler = handler
    }
    
    fun handleException(
        throwable: Throwable,
        tag: String = TAG,
        logError: Boolean = true,
        notifyGlobal: Boolean = true
    ) {
        if (logError) {
            Log.e(tag, "Error occurred", throwable)
        }
        
        if (notifyGlobal) {
            globalExceptionHandler?.invoke(throwable)
        }
    }
    
    fun <T> safeCall(
        tag: String = TAG,
        defaultValue: T,
        logError: Boolean = true,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
            defaultValue
        }
    }
    
    suspend fun <T> safeCallSuspend(
        tag: String = TAG,
        defaultValue: T,
        logError: Boolean = true,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
            defaultValue
        }
    }
    
    fun <T> safeCallOrNull(
        tag: String = TAG,
        logError: Boolean = true,
        block: () -> T?
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
            null
        }
    }
    
    suspend fun <T> safeCallOrNullSuspend(
        tag: String = TAG,
        logError: Boolean = true,
        block: suspend () -> T?
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
            null
        }
    }
    
    fun safeRun(
        tag: String = TAG,
        logError: Boolean = true,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
        }
    }
    
    suspend fun safeRunSuspend(
        tag: String = TAG,
        logError: Boolean = true,
        block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, tag, logError)
        }
    }
    
    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is java.net.UnknownHostException -> "网络连接失败，请检查网络设置"
            is java.net.SocketTimeoutException -> "连接超时，请重试"
            is java.net.ConnectException -> "无法连接到服务器"
            is java.io.IOException -> "网络错误: ${throwable.message}"
            is kotlinx.coroutines.TimeoutCancellationException -> "操作超时"
            else -> throwable.message ?: "发生未知错误"
        }
    }
    
    fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is java.net.UnknownHostException ||
                throwable is java.net.SocketTimeoutException ||
                throwable is java.net.ConnectException ||
                throwable is java.io.IOException
    }
    
    fun isTimeoutError(throwable: Throwable): Boolean {
        return throwable is java.net.SocketTimeoutException ||
                throwable is kotlinx.coroutines.TimeoutCancellationException
    }
}

fun <T> tryCatch(
    tag: String = "TryCatch",
    defaultValue: T,
    logError: Boolean = true,
    block: () -> T
): T {
    return ExceptionHandler.safeCall(tag, defaultValue, logError, block)
}

fun <T> tryCatchOrNull(
    tag: String = "TryCatch",
    logError: Boolean = true,
    block: () -> T?
): T? {
    return ExceptionHandler.safeCallOrNull(tag, logError, block)
}

fun tryCatchRun(
    tag: String = "TryCatch",
    logError: Boolean = true,
    block: () -> Unit
) {
    ExceptionHandler.safeRun(tag, logError, block)
}

suspend fun <T> tryCatchSuspend(
    tag: String = "TryCatch",
    defaultValue: T,
    logError: Boolean = true,
    block: suspend () -> T
): T {
    return ExceptionHandler.safeCallSuspend(tag, defaultValue, logError, block)
}

suspend fun <T> tryCatchOrNullSuspend(
    tag: String = "TryCatch",
    logError: Boolean = true,
    block: suspend () -> T?
): T? {
    return ExceptionHandler.safeCallOrNullSuspend(tag, logError, block)
}

suspend fun tryCatchRunSuspend(
    tag: String = "TryCatch",
    logError: Boolean = true,
    block: suspend () -> Unit
) {
    ExceptionHandler.safeRunSuspend(tag, logError, block)
}
