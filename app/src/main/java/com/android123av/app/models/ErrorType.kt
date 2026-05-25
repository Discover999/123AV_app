package com.android123av.app.models

/**
 * 错误类型密封类
 * 用于统一管理和分类应用程序中的各种错误
 */
sealed class ErrorType {
    
    /**
     * 网络相关错误
     */
    sealed class NetworkError : ErrorType() {
        object ConnectionFailed : NetworkError() {
            const val MESSAGE = "网络连接失败，请检查网络设置"
        }
        
        object Timeout : NetworkError() {
            const val MESSAGE = "连接超时，请重试"
        }
        
        object ServerUnreachable : NetworkError() {
            const val MESSAGE = "无法连接到服务器"
        }
        
        object UnknownHost : NetworkError() {
            const val MESSAGE = "网络连接失败，请检查网络设置"
        }
        
        object IOError : NetworkError() {
            const val MESSAGE = "网络错误"
        }
    }
    
    /**
     * 超时相关错误
     */
    sealed class TimeoutError : ErrorType() {
        object OperationTimeout : TimeoutError() {
            const val MESSAGE = "操作超时"
        }
        
        object SocketTimeout : TimeoutError() {
            const val MESSAGE = "网络连接超时，请检查网络"
        }
    }
    
    /**
     * 播放器相关错误
     */
    sealed class PlayerError : ErrorType() {
        object FormatNotSupported : PlayerError() {
            const val MESSAGE = "视频格式不支持"
        }
        
        object DecoderInitFailed : PlayerError() {
            const val MESSAGE = "解码器初始化失败"
        }
        
        object DecodingFailed : PlayerError() {
            const val MESSAGE = "视频解码失败"
        }
        
        object LiveEnded : PlayerError() {
            const val MESSAGE = "直播已结束，请刷新重试"
        }
    }
    
    /**
     * 数据解析错误
     */
    sealed class ParseError : ErrorType() {
        object HtmlParseFailed : ParseError() {
            const val MESSAGE = "HTML 解析失败"
        }
        
        object JsonParseFailed : ParseError() {
            const val MESSAGE = "JSON 解析失败"
        }
        
        object InvalidData : ParseError() {
            const val MESSAGE = "数据格式无效"
        }
    }
    
    /**
     * 用户相关错误
     */
    sealed class UserError : ErrorType() {
        object NotLoggedIn : UserError() {
            const val MESSAGE = "请先登录"
        }
        
        object LoginFailed : UserError() {
            const val MESSAGE = "登录失败"
        }
        
        object PermissionDenied : UserError() {
            const val MESSAGE = "权限不足"
        }
    }
    
    /**
     * 资源相关错误
     */
    sealed class ResourceError : ErrorType() {
        object NotFound : ResourceError() {
            const val MESSAGE = "资源未找到"
        }
        
        object LoadFailed : ResourceError() {
            const val MESSAGE = "资源加载失败"
        }
    }
    
    /**
     * 未知错误
     */
    object Unknown : ErrorType() {
        const val MESSAGE = "发生未知错误"
    }
    
    /**
     * 获取错误消息
     * @return 错误消息字符串
     */
    fun getMessage(): String {
        return when (this) {
            is NetworkError.ConnectionFailed -> NetworkError.ConnectionFailed.MESSAGE
            is NetworkError.Timeout -> NetworkError.Timeout.MESSAGE
            is NetworkError.ServerUnreachable -> NetworkError.ServerUnreachable.MESSAGE
            is NetworkError.UnknownHost -> NetworkError.UnknownHost.MESSAGE
            is NetworkError.IOError -> NetworkError.IOError.MESSAGE
            is TimeoutError.OperationTimeout -> TimeoutError.OperationTimeout.MESSAGE
            is TimeoutError.SocketTimeout -> TimeoutError.SocketTimeout.MESSAGE
            is PlayerError.FormatNotSupported -> PlayerError.FormatNotSupported.MESSAGE
            is PlayerError.DecoderInitFailed -> PlayerError.DecoderInitFailed.MESSAGE
            is PlayerError.DecodingFailed -> PlayerError.DecodingFailed.MESSAGE
            is PlayerError.LiveEnded -> PlayerError.LiveEnded.MESSAGE
            is ParseError.HtmlParseFailed -> ParseError.HtmlParseFailed.MESSAGE
            is ParseError.JsonParseFailed -> ParseError.JsonParseFailed.MESSAGE
            is ParseError.InvalidData -> ParseError.InvalidData.MESSAGE
            is UserError.NotLoggedIn -> UserError.NotLoggedIn.MESSAGE
            is UserError.LoginFailed -> UserError.LoginFailed.MESSAGE
            is UserError.PermissionDenied -> UserError.PermissionDenied.MESSAGE
            is ResourceError.NotFound -> ResourceError.NotFound.MESSAGE
            is ResourceError.LoadFailed -> ResourceError.LoadFailed.MESSAGE
            is Unknown -> Unknown.MESSAGE
        }
    }
    
    companion object {
        /**
         * 从异常创建 ErrorType
         * @param throwable 异常对象
         * @return 对应的 ErrorType
         */
        fun fromThrowable(throwable: Throwable): ErrorType {
            return when (throwable) {
                is java.net.UnknownHostException -> NetworkError.UnknownHost
                is java.net.SocketTimeoutException -> TimeoutError.SocketTimeout
                is java.net.ConnectException -> NetworkError.ServerUnreachable
                is java.io.IOException -> NetworkError.IOError
                is kotlinx.coroutines.TimeoutCancellationException -> TimeoutError.OperationTimeout
                is org.json.JSONException -> ParseError.JsonParseFailed
                is IllegalArgumentException -> ParseError.InvalidData
                else -> Unknown
            }
        }
    }
}
