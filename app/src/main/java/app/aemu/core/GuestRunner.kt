package app.aemu.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Собирает командную строку и окружение гостевого процесса:
 *   dhdrun <область свойств> 30 qemu-arm -L <root> -0 <argv0> <root/argv0> args…
 * dhdrun отображает область свойств на fd 30 (ANDROID_PROPERTY_WORKSPACE), создаёт сокеты init
 * (DHD_SOCK_<имя>=путь,права → ANDROID_SOCKET_<имя>) и переносит DHD_ENV_X в X перед exec qemu.
 */
class GuestRunner(
    val paths: VmPaths,
    val img: GuestImage,
    val net: NetConfig = NetConfig(),
) {
    data class NetConfig(val http80: Int = 0, val https443: Int = 0, val dns53: Int = 0, val glPath: String? = null)

    var singleTouch = false
    var binderVerbose = false
    var sdcardHost: File? = null

    val engine: Engine get() = img.engine
    val kernel: Kernel64 get() = Kernel64.forApi(img.api)
    val runner: File get() = paths.nativeBin(kernel.runner)

    fun cmdline(argv: List<String>, propsFile: File? = null): List<String> {
        val prog = argv.first()
        return listOf(runner.absolutePath, prog) + argv
    }


    /**
     * 12+: the class paths (BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, SYSTEMSERVERCLASSPATH) are put together at boot from
     * the modules' own lists by the firmware's derive_classpath, and init exports them to every service after it.
     */
    @Volatile var derivedExports: Map<String, String> = emptyMap()

    /** Native arm64 guest: libaemu64 redirects paths itself, the firmware's own linker and libraries run as they are. */
    private fun env64(extra: Map<String, String>): Map<String, String> {
        val e = LinkedHashMap<String, String>()
        for ((k, v) in img.exports) if (k !in EXPORTS_SKIP) e[k] = v
        e["PATH"] = img.exports["PATH"] ?: "/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin"
        e["LD_LIBRARY_PATH"] = "/vendor/lib64:/system/lib64"
        e["ANDROID_ROOT"] = img.exports["ANDROID_ROOT"] ?: "/system"
        e["ANDROID_DATA"] = img.exports["ANDROID_DATA"] ?: "/data"
        e["ANDROID_ASSETS"] = img.exports["ANDROID_ASSETS"] ?: "/system/app"
        e["EXTERNAL_STORAGE"] = img.exports["EXTERNAL_STORAGE"] ?: img.sdcardPath
        e["BOOTCLASSPATH"] = img.bootclasspath
        e.putIfAbsent("EMULATED_STORAGE_SOURCE", "/mnt/shell/emulated")
        e.putIfAbsent("EMULATED_STORAGE_TARGET", "/storage/emulated")
        // a system-only image (GSI) has no init.environ.rc: that lives in the boot ramdisk
        e.putIfAbsent("ASEC_MOUNTPOINT", "/mnt/asec")
        e.putIfAbsent("ANDROID_BOOTLOGO", "1")
        e.putIfAbsent("ANDROID_STORAGE", "/storage")
        if ("DOWNLOAD_CACHE" !in e && img.api >= 26) {
            File(paths.root, "data/cache").mkdirs()
            e["DOWNLOAD_CACHE"] = "/data/cache"
        }
        if ("SYSTEMSERVERCLASSPATH" !in e) {
            val jars = listOf("services.jar", "ethernet-service.jar", "wifi-service.jar")
                .filter { File(paths.root, "system/framework/$it").isFile }
            if (jars.isNotEmpty()) e["SYSTEMSERVERCLASSPATH"] = jars.joinToString(":") { "/system/framework/$it" }
        }
        e["HOME"] = "/data"; e["TMPDIR"] = "/data/local/tmp"; e["TZ"] = "UTC"
        e["AEMU64_ROOT"] = paths.root.absolutePath
        e["AEMU64_PROPS"] = paths.props.absolutePath
        e["AEMU64_BINDER"] = paths.binderSock.absolutePath
        if (kernel.treble) {
            e["AEMU64_HWBINDER"] = paths.hwbinderSock.absolutePath
            e["AEMU64_VNDBINDER"] = paths.vndbinderSock.absolutePath
        }
        e["AEMU64_INPUT"] = paths.inputSock.absolutePath
        sdcardHost?.let { e["AEMU64_SDCARD"] = it.absolutePath }
        e["AEMU64_FB"] = "${img.settings.width}x${img.settings.height}"
        // debugging: run/crash.report makes the runner print pc/lr and their mappings on a fatal signal
        if (File(paths.bin, "crash.report").exists()) e["AEMU64_CRASH"] = "1"
        for ((k, v) in extra) when {
            k.startsWith("DHD_SOCK_") -> e["AEMU64_SOCK_" + k.removePrefix("DHD_SOCK_")] = v
            k == "DHD_UID" -> e["AEMU64_ID"] = v + "," + (extra["DHD_GID"] ?: v)
            k == "DHD_GID" -> {}
            k == "AEMU64_PROPS_FILE" -> e["AEMU64_PROPS"] = v
            else -> e[k] = v
        }
        e.putAll(derivedExports)
        return e
    }

    fun env(extra: Map<String, String> = emptyMap()): Map<String, String> = env64(extra)

    /** Одноразовый запуск гостевой команды (am, pm, settings, sh -c …) с ожиданием вывода. */
    fun run(argv: List<String>, timeoutMs: Long = 120_000, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        if (!runner.canExecute()) return -1 to "no runner"
        val pb = ProcessBuilder(cmdline(argv)).directory(paths.bin).redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(env64(extra))
        val p = pb.start()
        val sb = StringBuilder()
        val t = Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { if (sb.length < 256_000) sb.append(it).append('\n') } }
        }.apply { isDaemon = true; start() }
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        t.join(2000)
        val code = runCatching { p.exitValue() }.getOrDefault(-9)
        return code to sb.toString()
    }

    companion object {
        private val EXPORTS_SKIP = setOf("PATH", "LD_LIBRARY_PATH", "BOOTCLASSPATH", "LD_PRELOAD")

        /** RAM the guest is told it has: the user's choice, or what phones of that Android version shipped with. */
        fun guestRamMb(api: Int, chosen: Int): Int {
            if (chosen > 0) return chosen
            val typical = when { api <= 10 -> 512; api <= 15 -> 1024; api <= 19 -> 2048; api <= 23 -> 3072; else -> 4096 }
            val hostMb = runCatching {
                java.io.File("/proc/meminfo").useLines { l -> l.first { it.startsWith("MemTotal:") } }.split(Regex("\\s+"))[1].toLong() / 1024
            }.getOrDefault(8192L)
            return typical.coerceAtMost((hostMb / 2).toInt())
        }
    }
}
