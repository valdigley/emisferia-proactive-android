package com.emisferia.proactive.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.emisferia.proactive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Update checker for GitHub releases
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/valdigley/emisferia-proactive-android/releases/latest"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    /**
     * Check for updates from GitHub releases
     */
    suspend fun checkForUpdate(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(GITHUB_API_URL).openConnection()
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val response = connection.getInputStream().bufferedReader().readText()
                val json = org.json.JSONObject(response)

                val tagName = json.getString("tag_name") // e.g., "v1.4.0"
                val latestVersion = tagName.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME
                val releaseNotes = json.optString("body", "")

                // Find APK download URL from assets
                var downloadUrl = ""
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                val hasUpdate = isNewerVersion(latestVersion, currentVersion)

                Log.d(TAG, "Current: $currentVersion, Latest: $latestVersion, HasUpdate: $hasUpdate")

                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates: ${e.message}")
                null
            }
        }
    }

    /**
     * Compare version strings (e.g., "1.5.0" > "1.4.0")
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }

                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison failed: ${e.message}")
        }
        return false
    }

    /**
     * Open browser to download update
     */
    fun openDownloadPage(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download page: ${e.message}")
        }
    }
}
