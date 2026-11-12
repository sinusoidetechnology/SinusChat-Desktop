package pl.sinusoide.sinuschat.desktop

import java.util.Properties

object AppVersion {
    val name: String by lazy {
        runCatching {
            AppVersion::class.java.getResourceAsStream("/sinuschat-version.properties")?.use { input ->
                Properties().apply { load(input) }.getProperty("version")?.trim()
            }
        }.getOrNull()?.ifEmpty { null } ?: "0"
    }
}
