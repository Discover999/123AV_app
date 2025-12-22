package com.android123av.app.models

import android.os.Parcel
import android.os.Parcelable

// 登录响应数据类
data class LoginResponse(
    val status: Int,
    val result: Any?,
    val messages: Messages
) {
    // 辅助属性，判断登录是否成功
    val isSuccess: Boolean
        get() = status == 200
    
    // 辅助属性，获取成功或错误消息
    val message: String
        get() = messages.all.firstOrNull() ?: ""
}

// 消息数据类
data class Messages(
    val all: List<String>,
    val keyed: List<String>
)

// 请求信息数据类
data class RequestInfo(
    val url: String = "",
    val status: String = "未请求",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val duration: Long = 0,
    val responseSize: Int = 0,
    val success: Boolean? = null
)

// 用户数据类
data class User(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)

// 视频数据类
data class Video(
    val id: String,
    val title: String,
    val duration: String,
    val thumbnailUrl: String?,
    val videoUrl: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(duration)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(videoUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Video> {
        override fun createFromParcel(parcel: Parcel): Video {
            return Video(parcel)
        }

        override fun newArray(size: Int): Array<Video?> {
            return arrayOfNulls(size)
        }
    }
}

// 分页信息数据类
data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPrevPage: Boolean
)



