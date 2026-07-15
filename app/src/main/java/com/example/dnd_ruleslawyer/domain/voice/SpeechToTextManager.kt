package com.example.dnd_ruleslawyer.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.dnd_ruleslawyer.R

class SpeechToTextManager(
    private val context: Context,
    private val onTextResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private companion object {
        const val RECOGNITION_LANGUAGE = "en-US"
    }

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChanged(true)
            }

            override fun onEndOfSpeech() {
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                onListeningChanged(false)
                onError(context.getString(R.string.speech_error_failed))
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()

                if (text.isNullOrBlank()) {
                    onError(context.getString(R.string.speech_error_none))
                } else {
                    onTextResult(text)
                }
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(context.getString(R.string.speech_error_unavailable))
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, RECOGNITION_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, RECOGNITION_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }
}
