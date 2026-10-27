package app.aemu.importer

import android.content.Context
import app.aemu.core.Engine
import app.aemu.core.GuestImage
import app.aemu.core.InitPlan
import app.aemu.core.PropArea
import app.aemu.core.VmPaths
import app.aemu.core.VmSettings
import java.io.File
import java.io.IOException

/**
 * Analyzes an extracted GSI filesystem tree and derives everything needed to boot:
 * Android version (API 28–37), brand/model/skin, engine (N64), BOOTCLASSPATH,
 * init exports/directories, service plan, SD card path, and initial property area template.
 */
class Analyzer(
    private val ctx: Context?,
    private val root: File,
    private val propsTemplate: File,
    private val ramdisk: List<BootImage.CpioEntry>? = null
) {
    constructor(ctx: Context, paths: VmPaths, ramdisk: List<BootImage.CpioEntry>?) :
        this(ctx, paths.root, paths.propsTemplate, ramdisk)

    constructor(root: File, propsTemplate: File = File(root.parentFile ?: root, "props.base")) :
        this(null, root, propsTemplate, null)

    fun analyze(id: String, sourceName: String): GuestImage {
        val buildFile = File(root, "system/build.prop")
        val noBuildProp = !buildFile.isFile
        if (noBuildProp) buildFile.writeText(synthBuildProp())
        val build = PropArea.parseProps(buildFile.readText(Charsets.UTF_8))
        val deflt = listOf("default.prop", "system/etc/prop.default").map { File(root, it) }.firstOrNull { it.isFile }
            ?.let { PropArea.parseProps(it.readText()) } ?: LinkedHashMap()
        val all = LinkedHashMap<String, String>().apply {
            putAll(deflt)
            build.forEach { (k, v) -> if (!(k.startsWith("ro.") && containsKey(k))) put(k, v) }
        }

        val api = all["ro.build.version.sdk"]?.toIntOrNull() ?: guessApi(all["ro.build.version.release"])
        val release = all["ro.build.version.release"] ?: "?"
        val ident = LinkedHashMap(all)
        val skinParts = root.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("mi_ext|my_[a-z]+|vgc|prism|optics")) }
            ?.flatMap { listOf("${it.name}/etc/build.prop", "${it.name}/build.prop") } ?: emptyList()
        for (n in skinParts + listOf(
            "product/etc/build.prop", "product/build.prop", "system/product/etc/build.prop", "system/product/build.prop",
            "odm/etc/build.prop", "odm/build.prop", "vendor/build.prop",
            "system_ext/etc/build.prop", "system_ext/build.prop", "system/system_ext/etc/build.prop", "system/system_ext/build.prop"
        )) runCatching {
            File(root, n).takeIf { it.isFile }?.let { f -> PropArea.parseProps(f.readText()).forEach { (k, v) -> ident.putIfAbsent(k, v) } }
        }
        fun product(what: String) = ident["ro.product.$what"]
            ?: listOf("product", "odm", "vendor", "system").firstNotNullOfOrNull { ident["ro.product.$it.$what"]?.takeIf { v -> v.isNotEmpty() } }
        val brand = (product("brand") ?: product("manufacturer") ?: "").replaceFirstChar { it.uppercase() }
        val model = product("model") ?: product("device") ?: "Android"
        for (what in listOf("brand", "manufacturer")) product(what)?.let { ident.putIfAbsent("ro.product.$what", it) }
        val hasLib64 = File(root, "system/lib64").isDirectory || File(root, "system/bin/app_process64").isFile
        val rawAbi = all["ro.product.cpu.abi"] ?: all["ro.product.cpu.abilist"]?.substringBefore(',')
        val abi = rawAbi?.trim()?.takeIf { it.isNotEmpty() } ?: if (hasLib64) "arm64-v8a" else "armeabi-v7a"
        val skin = skin(ident)

        if (api !in 28..37) {
            throw IOException("Unsupported Android version: Android $release (API $api). Only Android 9–17 (API 28–37) GSI images are supported.")
        }
        if (!abi.startsWith("arm64") && !hasLib64) {
            throw IOException("Unsupported CPU architecture ($abi). Only 64-bit ARM64 (arm64-v8a) GSI images are supported.")
        }

        val rcFiles = (root.listFiles()?.filter { it.isFile && it.name.endsWith(".rc") }?.sortedBy { if (it.name == "init.rc") 0 else 1 } ?: emptyList()) +
            InitPlan.hwRcFiles(root, all) +
            (File(root, "system/etc/init").listFiles()?.filter { it.name.endsWith(".rc") } ?: emptyList())
        val rc = InitPlan.parse(rcFiles, all)
        val dhdExports = File(root, "dhd.exports").takeIf { it.isFile }?.readLines()?.mapNotNull { l ->
            val i = l.indexOf('='); if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
        }?.toMap() ?: emptyMap()
        val dhdBcp = File(root, "dhd.bootclasspath").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val dhdDirs = File(root, "dhd.dirs").takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.startsWith("/") } ?: emptyList()
        val bcp = rc.exports["BOOTCLASSPATH"]?.takeIf { it.isNotBlank() } ?: dhdBcp ?: dhdExports["BOOTCLASSPATH"]
            ?: bootclasspathGuess()
        val exports = LinkedHashMap<String, String>(dhdExports).apply { putAll(rc.exports); remove("BOOTCLASSPATH") }
        val dirs = (rc.dirs + dhdDirs).filter { it.startsWith("/data") || it.startsWith("/cache") || it.startsWith("/mnt") || it.startsWith("/storage") }.distinct()
        if (!exports.containsKey("EMULATED_STORAGE_TARGET") && exports["EXTERNAL_STORAGE"]?.startsWith("/storage/emulated") != false) {
            exports.putIfAbsent("EXTERNAL_STORAGE", "/storage/emulated/legacy")
            exports["EMULATED_STORAGE_SOURCE"] = "/mnt/shell/emulated"
            exports["EMULATED_STORAGE_TARGET"] = "/storage/emulated"
        }
        val plan = InitPlan.plan(rc, api, root)
        val vols = StorageList.read(root)
        val voldVols = vols.filter { !it.emulated }.map { it.mountPoint }
        val sdcard = vols.firstOrNull { it.primary && !it.emulated }?.mountPoint ?: exports["EXTERNAL_STORAGE"]?.takeIf { it.startsWith("/") }
            ?: "/storage/emulated/legacy"

        val warnings = ArrayList<String>()
        if (noBuildProp) warnings += "No system/build.prop: Android version was inferred from the system tree."
        if (rcFiles.isEmpty() && dhdBcp == null) warnings += "No boot.img in firmware: boot plan and BOOTCLASSPATH were guessed."

        val density = all["ro.sf.lcd_density"]?.toIntOrNull() ?: 420
        val engine = Engine.N64
        val settings = VmSettings(width = 1080, height = 1920, density = density)

        writePropsTemplate(deflt, build, api, rc.setprops)

        return GuestImage(
            id = id,
            name = (if (model.startsWith(brand, true)) model else "$brand $model").trim().ifBlank { "Android $release" } +
                if (skin != "AOSP" && !model.contains(skin, true)) " - $skin" else "",
            release = release,
            api = api,
            brand = brand,
            model = model,
            skin = skin,
            engine = engine,
            abi = "arm64-v8a",
            bootclasspath = bcp,
            exports = exports,
            dirs = dirs,
            services = plan,
            sdcardPath = sdcard,
            volumes = voldVols,
            settings = settings,
            createdAt = System.currentTimeMillis(),
            sourceName = sourceName,
            runtime = "art",
            warnings = warnings,
            profileVersion = VERSION,
        )
    }

    companion object {
        const val VERSION = 14

        private val NET_DEFAULTS = listOf(
            "net.tcp.buffersize.default" to "4096,87380,110208,4096,16384,110208",
            "net.tcp.buffersize.wifi" to "524288,1048576,2097152,262144,524288,1048576",
            "net.tcp.buffersize.umts" to "4094,87380,110208,4096,16384,110208",
            "net.tcp.buffersize.hspa" to "4094,87380,262144,4096,16384,262144",
            "net.tcp.buffersize.lte" to "524288,1048576,2097152,262144,524288,1048576",
            "net.tcp.buffersize.edge" to "4093,26280,35040,4096,16384,35040",
            "net.tcp.buffersize.gprs" to "4092,8760,11680,4096,8760,11680",
            "net.bt.name" to "Android",
            "net.change" to "net.dns1",
        )

        fun analyze(root: File, id: String = "test", sourceName: String = "system.img"): GuestImage {
            val propsOut = File(root.parentFile ?: root, "${id}_props.base")
            return Analyzer(root, propsOut).analyze(id, sourceName)
        }

        fun refresh(ctx: Context, img: GuestImage): GuestImage {
            if (img.profileVersion >= VERSION) return img
            val paths = VmPaths(ctx, img.id)
            val fresh = runCatching { Analyzer(ctx, paths, null).analyze(img.id, img.sourceName) }.getOrNull() ?: return img
            val out = fresh.copy(
                name = img.name, settings = img.settings, createdAt = img.createdAt, sizeBytes = img.sizeBytes,
                lastBootMs = img.lastBootMs, bootCount = img.bootCount,
            )
            app.aemu.core.ImageStore.save(ctx, out)
            return out
        }

        fun guessApi(rel: String?): Int = when {
            rel == null -> 28
            rel.startsWith("1.0") -> 1; rel.startsWith("1.1") -> 2; rel.startsWith("1.5") -> 3; rel.startsWith("1.6") -> 4
            rel.startsWith("2.0") -> 5; rel.startsWith("2.1") -> 7; rel.startsWith("2.2") -> 8; rel.startsWith("2.3") -> 10
            rel.startsWith("3.0") -> 11; rel.startsWith("3.1") -> 12; rel.startsWith("3.2") -> 13
            rel.startsWith("4.0") -> 15; rel.startsWith("4.1") -> 16; rel.startsWith("4.2") -> 17; rel.startsWith("4.3") -> 18
            rel.startsWith("4.4") -> 19; rel.startsWith("5.0") -> 21; rel.startsWith("5.1") -> 22
            rel.startsWith("6") -> 23; rel.startsWith("7.0") -> 24; rel.startsWith("7") -> 25
            rel.startsWith("8.0") -> 26; rel.startsWith("8.1") -> 27; rel.startsWith("8") -> 26
            rel.startsWith("9") -> 28; rel.startsWith("10") -> 29; rel.startsWith("11") -> 30
            rel.startsWith("12L") || rel.startsWith("12.1") -> 32
            rel.startsWith("12") -> 31; rel.startsWith("13") -> 33; rel.startsWith("14") -> 34
            rel.startsWith("15") -> 35; rel.startsWith("16") -> 36; rel.startsWith("17") -> 37
            else -> 28
        }
    }

    private fun synthBuildProp(): String {
        fun has(path: String) = File(root, path).exists()
        val (api, rel) = when {
            has("system/apex") || has("apex") -> 29 to "10"
            has("system/lib64") -> 28 to "9"
            else -> 27 to "8.1"
        }
        return "# generated by AEmulator: firmware had no build.prop\nro.build.version.sdk=$api\nro.build.version.release=$rel\n" +
            "ro.product.model=Android\nro.product.brand=generic\nro.product.cpu.abi=arm64-v8a\n"
    }

    private fun skin(p: Map<String, String>): String {
        val man = (p["ro.product.manufacturer"] ?: p["ro.product.brand"] ?: "").lowercase()
        fun has(path: String) = File(root, path).exists()
        val brand = (p["ro.product.brand"] ?: "").lowercase()
        return when {
            p.containsKey("ro.mi.os.version.name") || p.containsKey("ro.mi.os.version.code") -> "HyperOS " + (p["ro.mi.os.version.name"] ?: "")
            man.contains("infinix") || brand.contains("infinix") -> "XOS"
            man.contains("tecno") || brand.contains("tecno") -> "HiOS"
            p.containsKey("ro.build.version.oneui") || p.containsKey("ro.build.version.sep") ->
                "One UI " + (p["ro.build.version.oneui"]?.toIntOrNull()?.let { "${it / 10000}.${it / 100 % 100}" } ?: "")
            p.containsKey("ro.build.version.realmeui") -> "realme UI " + (p["ro.build.version.realmeui"] ?: "").removePrefix("V")
            p.containsKey("ro.build.version.oplusrom") -> "ColorOS " + (p["ro.build.version.oplusrom"] ?: "").removePrefix("V")
            p.containsKey("ro.vivo.os.version") || p.containsKey("ro.vivo.os.name") ->
                p["ro.vivo.os.build.display.id"]?.takeIf { it.isNotBlank() }
                    ?: ((if (p["ro.vivo.os.name"]?.contains("Funtouch", true) == true) "Funtouch OS " else "OriginOS ") + (p["ro.vivo.os.version"] ?: ""))
            p.containsKey("ro.miui.ui.version.name") || p.containsKey("ro.miui.ui.version.code") || has("system/app/MiuiHome.apk") || has("system/app/MiuiHome") -> "MIUI " + (p["ro.miui.ui.version.name"] ?: "")
            p.containsKey("ro.lineage.version") -> "LineageOS"
            p["ro.build.display.id"]?.contains("Flyme", true) == true -> "Flyme"
            p.containsKey("ro.build.version.emui") -> "EMUI"
            p.containsKey("ro.build.version.opporom") -> "ColorOS"
            (p["ro.product.brand"] ?: "").lowercase() == "google" -> "AOSP"
            man.contains("asus") -> "ZenUI"
            man.contains("huawei") -> "EMUI"
            else -> "AOSP"
        }.trim()
    }

    private fun bootclasspathGuess(): String {
        val fw = File(root, "system/framework")
        val names = fw.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val aosp = listOf(
            "core-oj.jar", "core-libart.jar", "conscrypt.jar", "okhttp.jar",
            "bouncycastle.jar", "apache-xml.jar", "ext.jar", "framework.jar",
            "telephony-common.jar", "voip-common.jar", "ims-common.jar",
            "android.hidl.base-V1.0-java.jar", "android.hidl.manager-V1.0-java.jar"
        ).filter { it in names }
        val vendorHints = listOf("secframework", "sec_", "miui", "com.qualcomm.qcrilhook", "qcom.fmradio", "framework-miui", "framework-ext")
        val vendor = names.filter { n -> n.endsWith(".jar") && n !in aosp && vendorHints.any { n.startsWith(it) || n.contains(it) } && n != "services.jar" }
        return (aosp + vendor).joinToString(":") { "/system/framework/$it" }
    }

    private fun writePropsTemplate(deflt: Map<String, String>, build: Map<String, String>, api: Int, rcProps: Map<String, String>) {
        val base = LinkedHashMap<String, String>()
        fun add(k: String, v: String) { if (!(k.startsWith("ro.") && base.containsKey(k))) base[k] = v }
        add("ro.secure", "0")
        add("ro.adb.secure", "0")
        add("ro.debuggable", "0")
        add("ro.serialno", "0123456789abcdef")
        add("ro.bootmode", "unknown")
        add("ro.baseband", "unknown")
        add("ro.bootloader", "unknown")
        add("ro.carrier", "unknown")
        add("ro.revision", "0")
        add("ro.factorytest", "0")
        add("ro.setupwizard.mode", "DISABLED")
        add("ro.opengles.version", "196608")
        add("keyguard.no_require_sim", "1")
        add("ro.radio.noril", "false")
        add("dalvik.vm.stack-trace-file", "/data/anr/traces.txt")
        for ((k, v) in deflt) add(k, v)
        for ((k, v) in build) add(k, v)
        for ((k, v) in rcProps) add(k, v)
        for ((k, v) in NET_DEFAULTS) add(k, v)
        val sysDefault = File(root, "system/default.prop")
        if (sysDefault.isFile) PropArea.parseProps(sysDefault.readText()).forEach { (k, v) -> add(k, v) }
        base.remove("ro.sf.lcd_density")
        base.remove("ro.kernel.qemu")
        base.remove("ro.kernel.qemu.gles")
        base["persist.sys.usb.config"] = "none"
        base["DEVICE_PROVISIONED"] = "1"
        if (!base.containsKey("dalvik.vm.heapsize")) base["dalvik.vm.heapsize"] = "256m"
        PropArea.create(propsTemplate, api).use { a ->
            var dropped = 0
            for ((k, v) in base) if (!a.put(k, v)) dropped++
            if (dropped > 0) runCatching { android.util.Log.w("aemu", "props dropped: $dropped") }
        }
    }
}
