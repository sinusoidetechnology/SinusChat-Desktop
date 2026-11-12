import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = "1.0"

val hostOs = System.getProperty("os.name").lowercase()
val hostArm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
val webrtcNative = when {
    hostOs.startsWith("windows") -> "windows-x86_64"
    hostOs.startsWith("mac") -> if (hostArm) "macos-aarch64" else "macos-x86_64"
    else -> if (hostArm) "linux-aarch64" else "linux-x86_64"
}
val bytedecoNative = when {
    hostOs.startsWith("windows") -> "windows-x86_64"
    hostOs.startsWith("mac") -> if (hostArm) "macosx-arm64" else "macosx-x86_64"
    else -> if (hostArm) "linux-arm64" else "linux-x86_64"
}

val installerVersion = appVersion.split('.').map { it.trim() }.let { parts -> (parts + listOf("0", "0", "0")).take(3) }.joinToString(".")

val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("sinuschat-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

val compileBleHelper by tasks.registering {
    val out = layout.buildDirectory.dir("generated/ble")
    val source = project.file("ble/SinusBle.cs")
    val windows = System.getProperty("os.name").startsWith("Windows")
    val windir = System.getenv("WINDIR") ?: "C:/Windows"
    inputs.file(source)
    outputs.dir(out)
    doLast {
        val dir = out.get().dir("ble").asFile
        dir.mkdirs()
        if (!windows) return@doLast
        val fw = File(windir, "Microsoft.NET/Framework64/v4.0.30319")
        val meta = File(windir, "System32/WinMetadata")
        val csc = File(fw, "csc.exe")
        if (!csc.isFile) throw GradleException("Brak csc.exe (.NET Framework 4) w ${fw.absolutePath}")
        val refs = listOf(
            File(fw, "System.Runtime.WindowsRuntime.dll"), File(fw, "System.Runtime.dll"),
            File(fw, "System.Runtime.InteropServices.WindowsRuntime.dll"),
            File(meta, "Windows.Devices.winmd"), File(meta, "Windows.Foundation.winmd"), File(meta, "Windows.Storage.winmd")
        ).map { "/r:" + it.absolutePath }
        val exe = File(dir, "sinuschat-ble.exe")
        val process = ProcessBuilder(
            listOf(csc.absolutePath, "/nologo", "/optimize+", "/target:exe", "/platform:anycpu", "/out:" + exe.absolutePath) + refs + source.absolutePath
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0 || !exe.isFile) throw GradleException("csc: $log")
    }
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        jvmMain {
            resources.srcDir(generateVersion)
            resources.srcDir(compileBleHelper)
        }
        jvmMain.dependencies {
            implementation("pl.sinusoide.sinuschat:link:1.0")
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.zxing.core)
            implementation(libs.jmdns)
            implementation(libs.jna.platform)
            implementation(libs.webrtc.java)
            implementation(libs.vorbisspi)
            runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtc.get()}:$webrtcNative")
            implementation("org.bytedeco:javacv:${libs.versions.javacv.get()}") { isTransitive = false }
            implementation(libs.javacpp)
            implementation(libs.ffmpeg)
            runtimeOnly("org.bytedeco:javacpp:${libs.versions.javacv.get()}:$bytedecoNative")
            runtimeOnly("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$bytedecoNative")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }
    }
}

compose.desktop {
    application {
        mainClass = "pl.sinusoide.sinuschat.desktop.MainKt"
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "SinusChat"
            packageVersion = installerVersion
            vendor = "Sinusoide"
            modules("java.instrument", "java.management", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/sinuschat.ico"))
                menuGroup = "SinusChat"
                perUserInstall = true
                upgradeUuid = "6f1d8c52-3b4e-4a7f-9c21-5e0b7a9d4c13"
            }
            macOS {
                iconFile.set(project.file("icons/sinuschat.icns"))
                bundleID = "pl.sinusoide.sinuschat.desktop"
                packageName = "SinusChat"
                dockName = "SinusChat"
                appCategory = "public.app-category.social-networking"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>SinusChat needs the microphone for voice messages and calls.</string>
                        <key>NSCameraUsageDescription</key>
                        <string>SinusChat needs the camera for video calls.</string>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>SinusChat connects to SinusChat on your phone over the local network.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_sinuschat._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("icons/sinuschat.png"))
                packageName = "sinuschat"
                debMaintainer = "support@sinusoide.pl"
                menuGroup = "Network;InstantMessaging"
                appCategory = "net"
                shortcut = true
            }
        }
    }
}

val releaseDir = File(System.getProperty("user.home"), ".sinuschat-release")

val portableArch = if (hostArm) "arm64" else "x64"
val portableOs = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").lowercase().contains("mac") -> "mac"
    else -> "linux"
}

fun registerPortable(taskName: String, suffix: String, folder: String, marker: String) = tasks.register<Zip>(taskName) {
    group = "release"
    description = "Buduje wersje przenosna SinusChat-Desktop ($suffix)"
    dependsOn("createDistributable")
    val image = layout.buildDirectory.dir("compose/binaries/main/app/SinusChat")
    val markerDir = layout.buildDirectory.dir("portable-marker/$suffix")
    doFirst {
        val dir = markerDir.get().asFile
        dir.mkdirs()
        File(dir, "SinusChat.portable").writeText(marker)
    }
    from(image) { into(folder) }
    from(markerDir) { into(folder) }
    archiveFileName.set("SinusChat-Desktop-$appVersion-$portableOs-$portableArch-$suffix.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
}

registerPortable("packagePortable", "portable", "SinusChat Portable", "keep")
registerPortable("packagePortableSession", "portable-session", "SinusChat Jednorazowo", "session")

tasks.register("packageSetup") {
    group = "release"
    description = "Buduje instalator SinusChat-Setup (Inno Setup)"
    dependsOn("createDistributable")
    val version = appVersion
    val numeric = installerVersion
    val script = project.file("installer/sinuschat.iss")
    val source = layout.buildDirectory.dir("compose/binaries/main/app/SinusChat").get().asFile
    val out = layout.buildDirectory.dir("compose/binaries/main/setup").get().asFile
    val custom = findProperty("iscc") as String?
    val local = System.getenv("LOCALAPPDATA")
    doLast {
        val candidates = listOfNotNull(
            custom,
            System.getenv("ISCC"),
            "C:/Program Files (x86)/Inno Setup 6/ISCC.exe",
            "C:/Program Files/Inno Setup 6/ISCC.exe",
            local?.let { "$it/Programs/Inno Setup 6/ISCC.exe" }
        )
        val iscc = candidates.map { File(it) }.firstOrNull { it.isFile }
            ?: throw GradleException("Nie znaleziono Inno Setup 6 (ISCC.exe). Zainstaluj go albo podaj -Piscc=sciezka")
        if (!File(source, "SinusChat.exe").isFile) throw GradleException("Brak aplikacji w $source")
        out.mkdirs()
        val process = ProcessBuilder(
            iscc.absolutePath, "/Q",
            "/DAppVersion=$version", "/DInstallerVersion=$numeric",
            "/DSourceDir=${source.absolutePath}", "/DOutputDir=${out.absolutePath}",
            script.absolutePath
        ).directory(script.parentFile).inheritIO().start()
        if (process.waitFor() != 0) throw GradleException("ISCC zakończył się błędem ${process.exitValue()}")
        println("Gotowe: " + File(out, "SinusChat-Setup-$version.exe"))
    }
}

tasks.register("updateKeygen") {
    group = "release"
    description = "Tworzy klucz do podpisywania aktualizacji SinusChat-Desktop"
    val dir = releaseDir
    doLast {
        val private = File(dir, "update-ed25519.key")
        val public = File(dir, "update-ed25519.pub")
        if (private.exists()) throw GradleException("Klucz już istnieje: $private")
        dir.mkdirs()
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        private.writeText(Base64.getEncoder().encodeToString(pair.private.encoded))
        val pub = Base64.getEncoder().encodeToString(pair.public.encoded)
        public.writeText(pub)
        println("Klucz prywatny zapisany w $private")
        println("Klucz publiczny: $pub")
    }
}

tasks.register("updateManifest") {
    group = "release"
    description = "Zbiera instalatory, liczy SHA-256 i podpisuje desktop-latest.json"
    val version = appVersion
    val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
    val out = layout.buildDirectory.dir("update").get().asFile
    val base = (findProperty("updateBase") as String?) ?: "https://github.com/sinusoidetechnology/SinusChat-Desktop/releases/download/v$version/"
    val changelog = findProperty("changelog") as String?
    val extraDir = (findProperty("packagesDir") as String?)?.let { File(it) }
    val keyFile = File(releaseDir, "update-ed25519.key")
    doLast {
        val keyText = System.getenv("SINUSCHAT_UPDATE_KEY")?.trim()?.ifEmpty { null }
            ?: keyFile.takeIf { it.exists() }?.readText()?.trim()
            ?: throw GradleException("Brak klucza: uruchom updateKeygen albo ustaw SINUSCHAT_UPDATE_KEY")
        val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyText)))
        fun platform(f: File): String? {
            val n = f.name.lowercase()
            val arm = "arm64" in n || "aarch64" in n || (hostArm && "x64" !in n && "x86_64" !in n && "amd64" !in n)
            return when (f.extension.lowercase()) {
                "msi" -> "windows-x64"
                "exe" -> if (n.startsWith("sinuschat-setup")) "windows-x64-setup" else null
                "deb" -> if (arm) "linux-arm64-deb" else "linux-x64-deb"
                "rpm" -> if (arm) "linux-arm64-rpm" else "linux-x64-rpm"
                "pkg" -> if (arm) "mac-arm64" else "mac-x64"
                "dmg" -> if (arm) "mac-arm64-dmg" else "mac-x64-dmg"
                else -> null
            }
        }
        val roots = listOfNotNull(binaries.takeIf { it.isDirectory }, extraDir?.takeIf { it.isDirectory })
        val found = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && platform(it) != null }.toList() }
            .groupBy { platform(it)!! }
            .mapValues { (_, files) -> files.maxByOrNull { it.lastModified() }!! }
            .toSortedMap()
        out.deleteRecursively()
        out.mkdirs()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val entries = found.map { (platform, file) ->
            val name = "SinusChat-Desktop-${platform.removeSuffix("-deb").removeSuffix("-rpm")}.${file.extension.lowercase()}"
            val target = File(out, name)
            file.copyTo(target, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            println("$platform: $name (${target.length()} B)")
            "    \"$platform\": { \"url\": \"${esc(base.trimEnd('/') + "/" + name)}\", \"sha256\": \"$sha\", \"size\": ${target.length()}, \"name\": \"$name\" }"
        }
        val json = buildString {
            append("{\n")
            append("  \"app\": \"SinusChat-Desktop\",\n")
            append("  \"version\": \"${esc(version)}\",\n")
            append("  \"published\": ${System.currentTimeMillis()},\n")
            if (changelog != null) append("  \"changelog\": \"${esc(changelog)}\",\n")
            append("  \"packages\": {\n")
            append(entries.joinToString(",\n"))
            if (entries.isNotEmpty()) append("\n")
            append("  }\n")
            append("}\n")
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        File(out, "desktop-latest.json").writeBytes(bytes)
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(key)
        signer.update(bytes)
        File(out, "desktop-latest.json.sig").writeText(Base64.getEncoder().encodeToString(signer.sign()))
        if (found.isEmpty()) println("Nie znaleziono instalatorów, manifest nie zawiera paczek")
        println("Gotowe: $out")
    }
}
