package com.cryptex.android.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptex.android.crypto.CryptexEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class MainViewModel : ViewModel() {
    var uiState by mutableStateOf(MainUiState())
        private set

    private val engine = CryptexEngine()

    fun onFileSelected(uri: Uri, name: String) {
        uiState = uiState.copy(selectedFileUri = uri, selectedFileName = name, status = "")
    }

    fun onPasswordChanged(password: String) {
        uiState = uiState.copy(password = password)
    }

    fun decryptAndSave(context: Context) {
        val uri = uiState.selectedFileUri ?: return
        val password = uiState.password
        if (password.length < 8) {
            uiState = uiState.copy(status = "Password must be at least 8 characters")
            return
        }

        uiState = uiState.copy(isProcessing = true, status = "Decrypting...")

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val encryptedData = inputStream?.readBytes() ?: throw Exception("Failed to read file")
                    inputStream.close()
                    
                    val decrypted = engine.decrypt(encryptedData, password)
                    
                    // Save to downloads with a unique name if possible
                    val timestamp = System.currentTimeMillis() / 1000
                    val outName = uiState.selectedFileName.removeSuffix(".enc").let { 
                        if (it.contains(".")) {
                            val parts = it.split(".")
                            "${parts[0]}_$timestamp.${parts.last()}"
                        } else {
                            "${it}_$timestamp"
                        }
                    }
                    saveToDownloads(context, outName, decrypted)
                    "Success! Saved as $outName"
                }
                uiState = uiState.copy(status = result, isProcessing = false)
            } catch (e: Exception) {
                uiState = uiState.copy(status = "Error: ${e.message}", isProcessing = false)
            }
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, data: ByteArray) {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create MediaStore entry")

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(data)
        } ?: throw Exception("Failed to open output stream")
    }
}

data class MainUiState(
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "",
    val password: String = "",
    val status: String = "",
    val isProcessing: Boolean = false
)
