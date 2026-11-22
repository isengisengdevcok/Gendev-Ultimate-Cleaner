package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.engine.NonRootEngine
import com.example.engine.SecureDeleteEngine
import com.example.engine.VisualAndContextEngine
import com.example.engine.WhatsAppCleanerEngine
import com.example.model.ContextRiskLevel
import com.example.model.OperationMode
import com.example.root.RootManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ultimate Cleaner", appName)
    }

    @Test
    fun `context risk classifier protects documents and marks old installers safe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootManager = RootManager()
        val nonRootEngine = NonRootEngine(context, rootManager)
        val engine = VisualAndContextEngine(context, rootManager, nonRootEngine)

        val (ktpLevel, _) = engine.classifyContextRisk("scan_ktp_terbaru.jpg", "/sdcard/Download/scan_ktp_terbaru.jpg", 45, 500_000L)
        assertEquals(ContextRiskLevel.PROTECTED, ktpLevel)

        val (pdfLevel, _) = engine.classifyContextRisk("kontrak_kerja.pdf", "/sdcard/Download/kontrak_kerja.pdf", 30, 120_000L)
        assertEquals(ContextRiskLevel.PROTECTED, pdfLevel)

        val (apkLevel, _) = engine.classifyContextRisk("app_installer_v2.apk", "/sdcard/Download/app_installer_v2.apk", 14, 15_000_000L)
        assertEquals(ContextRiskLevel.SAFE_TO_CLEAN, apkLevel)

        val (tmpLevel, _) = engine.classifyContextRisk("cache_temp(1).tmp", "/sdcard/Download/cache_temp(1).tmp", 2, 1024L)
        assertEquals(ContextRiskLevel.SAFE_TO_CLEAN, tmpLevel)

        val (backupTmpLevel, _) = engine.classifyContextRisk("backup(1).tmp", "/sdcard/Download/backup(1).tmp", 2, 1024L)
        assertEquals(ContextRiskLevel.PROTECTED, backupTmpLevel)
    }

    @Test
    fun `dHash similarity detects identical bitmaps with zero hamming distance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootManager = RootManager()
        val nonRootEngine = NonRootEngine(context, rootManager)
        val engine = VisualAndContextEngine(context, rootManager, nonRootEngine)

        val bmpA = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val bmpB = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val c = if (x < 8) Color.WHITE else Color.BLACK
                bmpA.setPixel(x, y, c)
                bmpB.setPixel(x, y, c)
            }
        }
        val hashA = engine.computeDHash(bmpA)
        val hashB = engine.computeDHash(bmpB)
        assertEquals(0, engine.hammingDistance(hashA, hashB))
        assertTrue(OperationMode.entries.size == 3)
    }

    @Test
    fun `whatsapp protection locks account credentials and chat databases strictly`() {
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/files/me"))
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/files/key"))
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/databases/msgstore.db"))
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/sdcard/WhatsApp/Databases/msgstore-2026.db.crypt14"))
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/shared_prefs/keystore.xml"))
        assertTrue(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/sdcard/Android/media/com.whatsapp/WhatsApp/Backups/wa.db"))

        assertFalse(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/sdcard/WhatsApp/Media/WhatsApp Images/IMG_2026.jpg"))
        assertFalse(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/cache/image_cache_01.tmp"))
        assertFalse(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp/files/ViewOnce/vo_media.jpg"))
        assertFalse(WhatsAppCleanerEngine.isWhatsAppAccountProtected("/data/user/0/com.whatsapp.w4b/files/Stickers/st_1.webp"))
    }

    @Test
    fun `secure shred overwrites and permanently removes local temporary file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootManager = RootManager()
        val shredEngine = SecureDeleteEngine(context, rootManager, context.packageName)

        val tempFile = File(context.cacheDir, "sensitive_media_test.bin")
        tempFile.writeBytes(ByteArray(4096) { 0x5A })
        assertTrue(tempFile.exists())

        val result = shredEngine.secureShredLocalFile(tempFile)
        assertTrue(result)
        assertFalse(tempFile.exists())
    }
}
