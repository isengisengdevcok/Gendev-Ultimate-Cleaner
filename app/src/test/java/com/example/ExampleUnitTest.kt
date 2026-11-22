package com.example

import com.example.engine.PathValidator
import com.example.model.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun protectedPaths_areAlwaysBlocked() {
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard/Download/KitsuneBackup", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/storage/emulated/0/Download/KitsuneBackup", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard/Download/LineageOS-18.1-arm64-a-Vanilla.img.xz", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard/UltimateCleaner.log", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/data/adb/modules", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/data/data", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard/Download/*", emptyList()))

        assertFalse(PathValidator.isSafeFileDeletionTarget("/sdcard/Download/KitsuneBackup/boot.img", "com.example", emptyList()))
        assertFalse(PathValidator.isSafeFileDeletionTarget("/sdcard/Download/LineageOS-18.1-arm64-a-Vanilla.img.xz", "com.example", emptyList()))
        assertFalse(PathValidator.isSafeFileDeletionTarget("/sdcard/WhatsApp/Databases/msgstore.db.crypt14", "com.example", emptyList()))
        assertFalse(PathValidator.isSafeFileDeletionTarget("/sdcard/UltimateCleaner.log", "com.example", emptyList()))
        assertTrue(PathValidator.isSafeFileDeletionTarget("/sdcard/Download/old_installer.apk", "com.example", emptyList()))
    }

    @Test
    fun validTargetDirectories_andCustomExclusions_workCorrectly() {
        assertTrue(PathValidator.isSafeDirectoryTarget("/sdcard/Download", emptyList()))
        assertTrue(PathValidator.isSafeDirectoryTarget("/sdcard/DCIM", emptyList()))
        assertTrue(PathValidator.isSafeDirectoryTarget("/data/local/tmp", emptyList()))
        assertFalse(PathValidator.isSafeDirectoryTarget("/sdcard/MySecrets", listOf("MySecrets")))

        val flags = PathValidator.buildFindExclusionFlags(listOf("ImportantFolder"))
        assertTrue(flags.contains("KitsuneBackup"))
        assertTrue(flags.contains("LineageOS-18.1-arm64-a-Vanilla.img.xz"))
        assertTrue(flags.contains("*.db"))
        assertTrue(flags.contains("ImportantFolder"))
    }

    @Test
    fun formatBytes_formatsCorrectly() {
        assertEquals("0 B", FormatUtils.formatBytes(0L))
        assertEquals("512 B", FormatUtils.formatBytes(512L))
        assertEquals("1.0 KB", FormatUtils.formatBytes(1024L))
        assertEquals("2.50 MB", FormatUtils.formatBytes(2621440L))
    }
}
