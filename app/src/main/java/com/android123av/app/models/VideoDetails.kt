package com.android123av.app.models

import android.os.Parcel
import android.os.Parcelable

data class VideoDetails(
    val code: String,
    val releaseDate: String,
    val duration: String,
    val genres: List<String>,
    val maker: String,
    val tags: List<String>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(code)
        parcel.writeString(releaseDate)
        parcel.writeString(duration)
        parcel.writeStringList(genres)
        parcel.writeString(maker)
        parcel.writeStringList(tags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VideoDetails> {
        override fun createFromParcel(parcel: Parcel): VideoDetails {
            return VideoDetails(parcel)
        }

        override fun newArray(size: Int): Array<VideoDetails?> {
            return arrayOfNulls(size)
        }
    }
}