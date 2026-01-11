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

// 用户数据类
data class User(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val email: String? = null
)

// 用户信息响应数据类
data class UserInfoResponse(
    val status: Int,
    val result: UserInfoResult?
) {
    // 辅助属性，判断请求是否成功
    val isSuccess: Boolean
        get() = status == 200
}

// 用户信息结果数据类
data class UserInfoResult(
    val user_id: Int,
    val username: String,
    val email: String
)

// 视频数据类
data class Video(
    val id: String,
    val title: String,
    val duration: String,
    val thumbnailUrl: String?,
    val videoUrl: String? = null,
    val details: VideoDetails? = null,
    val favouriteCount: Int = 0,
    val parts: List<VideoPart> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readParcelable(VideoDetails::class.java.classLoader),
        parcel.readInt(),
        parcel.createTypedArrayList(VideoPart.CREATOR) ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(duration)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(videoUrl)
        parcel.writeParcelable(details, flags)
        parcel.writeInt(favouriteCount)
        parcel.writeTypedList(parts)
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
    val hasPrevPage: Boolean,
    val totalResults: Int = 0,
    val categoryTitle: String = "",
    val videoCount: String = "",
    val currentSort: String = "",
    val sortOptions: List<SortOption> = emptyList(),
    val actressDetail: ActressDetail? = null
)

// 排序选项数据类
data class SortOption(
    val title: String,
    val value: String,
    val isSelected: Boolean = false
)

// 视图模式枚举
enum class ViewMode {
    LIST, GRID
}

// 视频部分数据类
data class VideoPart(
    val name: String,
    val url: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VideoPart> {
        override fun createFromParcel(parcel: Parcel): VideoPart {
            return VideoPart(parcel)
        }

        override fun newArray(size: Int): Array<VideoPart?> {
            return arrayOfNulls(size)
        }
    }
}

data class MenuItem(
    val title: String,
    val href: String
)

data class MenuSection(
    val title: String,
    val items: List<MenuItem>
)

data class Actress(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val videoCount: Int,
    val birthday: String = ""
)

data class ActressDetail(
    val name: String,
    val avatarUrl: String = "",
    val birthday: String = "",
    val height: String = "",
    val measurements: String = "",
    val videoCount: Int = 0
)

data class Series(
    val id: String,
    val name: String,
    val videoCount: Int
)



