package com.android123av.app.models

import android.os.Parcel
import android.os.Parcelable

data class VideoDetails(
    val code: String,
    val releaseDate: String,
    val duration: String,
    val performer: String,
    val performerHref: String = "",
    val maker: String,
    val makerHref: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val genreHrefs: List<String> = emptyList(),
    val tagHrefs: List<String> = emptyList(),
    val favouriteCount: Int = 0,
    val realId: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.createStringArrayList() ?: emptyList(),
        parcel.createStringArrayList() ?: emptyList(),
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(code)
        parcel.writeString(releaseDate)
        parcel.writeString(duration)
        parcel.writeString(performer)
        parcel.writeString(performerHref)
        parcel.writeString(maker)
        parcel.writeString(makerHref)
        parcel.writeStringList(genres)
        parcel.writeStringList(tags)
        parcel.writeStringList(genreHrefs)
        parcel.writeStringList(tagHrefs)
        parcel.writeInt(favouriteCount)
        parcel.writeString(realId)
    }

    fun getGenresWithHrefs(): List<Pair<String, String>> {
        return genres.zip(genreHrefs.padEnd(genres.size))
    }

    fun getTagsWithHrefs(): List<Pair<String, String>> {
        return tags.zip(tagHrefs.padEnd(tags.size))
    }

    private fun List<String>.padEnd(size: Int): List<String> {
        return if (this.size >= size) this
        else this + List(size - this.size) { "" }
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