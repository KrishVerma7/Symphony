package com.example.symphony

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.symphony.api.ApiInterface
import com.example.symphony.api.mydata
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MusicViewModel : ViewModel() {
    private val apiInterface = Retrofit.Builder()
        .baseUrl("https://deezerdevs-deezer.p.rapidapi.com")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiInterface::class.java)

    private val _music = MutableLiveData<mydata>()
    val music: LiveData<mydata> = _music

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<Exception?>()
    val error: LiveData<Exception?> = _error

    private var mediaPlayer: MediaPlayer? = null

    fun fetchMusic() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = apiInterface.getData("ed sheeran")
                if (response.isSuccessful) {
                    _music.value = response.body()
                    _error.value = null
                } else {
                    _error.value = Exception("Failed to fetch data")
                }
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun initAndPlayMusic(url: String) {
        mediaPlayer?.release()  // Release any previously playing player
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener {
                start()  // Play automatically upon preparation
            }
            setOnErrorListener { _, what, _ ->
                // Log or handle error
                false
            }
        }
    }

//    fun playMusic() {
//        mediaPlayer?.start()
//    }

    fun pauseMusic() {
        mediaPlayer?.pause()
    }

    override fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onCleared()
    }
}

