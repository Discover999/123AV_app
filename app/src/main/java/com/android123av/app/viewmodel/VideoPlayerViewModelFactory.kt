package com.android123av.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.android123av.app.models.Video

class VideoPlayerViewModelFactory(
    private val video: Video
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(VideoPlayerViewModel::class.java)) {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            return VideoPlayerViewModel(
                application = checkNotNull(application),
                video = video
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
