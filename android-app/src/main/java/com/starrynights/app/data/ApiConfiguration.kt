package com.starrynights.app.data

import com.starrynights.app.BuildConfig

/** The only Android API base-URL boundary. Database credentials never belong in this app. */
object ApiConfiguration {
    val baseUrl: String = BuildConfig.API_BASE_URL
    val isReleasePlaceholder: Boolean get() = baseUrl.contains("replace-with-your-api")
    fun validationMessage(): String? = when {
        !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") -> "The API address is invalid. Configure a secure server URL before continuing."
        isReleasePlaceholder -> "This release has no API URL configured yet. Add the deployed HTTPS URL in the centralized build configuration."
        else -> null
    }
}
