package app.aemu

import app.aemu.core.Engine
import app.aemu.core.TreeFixer
import app.aemu.core.VmSettings
import app.aemu.importer.Analyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RomMatrixTest {

    private fun createTempRoot(props: Map<String, String>, createLib64: Boolean = true): File {
        val root = Files.createTempDirectory("aemu_test_").toFile()
        val sys = File(root, "system").apply { mkdirs() }
        if (createLib64) {
            File(sys, "lib64").mkdirs()
        }
        val propText = props.entries.joinToString("\n") { "${it.key}=${it.value}" }
        File(sys, "build.prop").writeText(propText)
        return root
    }

    @Test
    fun testAnalyzeValidArm64GsiRange() {
        for ((rel, expectedApi) in listOf(
            "9" to 28,
            "10" to 29,
            "11" to 30,
            "12" to 31,
            "12L" to 32,
            "13" to 33,
            "14" to 34,
            "15" to 35,
            "16" to 36,
            "17" to 37
        )) {
            val root = createTempRoot(
                mapOf(
                    "ro.build.version.release" to rel,
                    "ro.build.version.sdk" to expectedApi.toString(),
                    "ro.product.cpu.abi" to "arm64-v8a",
                    "ro.product.brand" to "google",
                    "ro.product.model" to "GSI ARM64"
                )
            )
            try {
                val info = Analyzer.analyze(root)
                assertEquals(expectedApi, info.api)
                assertEquals("arm64-v8a", info.abi)
                assertEquals("art", info.runtime)
                assertEquals(Engine.N64, info.engine)
            } finally {
                root.deleteRecursively()
                File(root.parentFile ?: root, "test_props.base").delete()
            }
        }
    }

    @Test
    fun testAnalyzeRejectsLegacyAndroidBelowApi28() {
        val root = createTempRoot(
            mapOf(
                "ro.build.version.release" to "8.1.0",
                "ro.build.version.sdk" to "27",
                "ro.product.cpu.abi" to "arm64-v8a"
            )
        )
        try {
            assertThrows(IOException::class.java) {
                Analyzer.analyze(root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testAnalyzeRejects32BitOnlyImage() {
        val root = createTempRoot(
            mapOf(
                "ro.build.version.release" to "11",
                "ro.build.version.sdk" to "30",
                "ro.product.cpu.abi" to "armeabi-v7a"
            ),
            createLib64 = false
        )
        try {
            assertThrows(IOException::class.java) {
                Analyzer.analyze(root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testVmSettingsAndRandomImeiLuhn() {
        val settings = VmSettings(
            width = 1080,
            height = 2400,
            density = 420,
            fbHz = 90,
            ramMb = 4096,
            gpu = true,
            netProxy = true,
            radio = false
        )
        assertEquals(1080, settings.width)
        assertEquals(2400, settings.height)
        assertEquals(420, settings.density)
        assertEquals(90, settings.fbHz)

        val imei = VmSettings.randomImei()
        assertEquals(15, imei.length)
        var sum = 0
        for (i in 0 until 14) {
            var d = imei[i].digitToInt()
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        val expectedCheck = (10 - sum % 10) % 10
        assertEquals(expectedCheck, imei[14].digitToInt())
    }

    @Test
    fun testQcomParkedComponents() {
        val qcomList = TreeFixer.QCOM_PARK
        assertTrue(qcomList.contains("system/etc/permissions/qti_permissions.xml"))
        assertTrue(qcomList.contains("system/etc/permissions/com.qti.dpmframework.xml"))
        assertTrue(qcomList.contains("system/priv-app/com.qualcomm.location"))
        assertTrue(qcomList.contains("system/priv-app/qcrilmsgtunnel"))
        assertTrue(qcomList.contains("system/priv-app/dpmserviceapp"))
        assertTrue(qcomList.contains("system/priv-app/DiracAudioControlService"))
        assertTrue(qcomList.contains("system/app/DiracManager"))
    }
}
