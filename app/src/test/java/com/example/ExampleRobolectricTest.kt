package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.utils.FileManager
import com.example.utils.ImageCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Exam Assessor", appName)
    }

    @Test
    fun `image compression produces file under 50KB`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create large simulated image 2000x1500
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val testFile = File(context.cacheDir, "test_output.jpg")

        val result = ImageCompressor.compressToTargetSize(
            source = bitmap,
            targetFile = testFile,
            targetMaxKb = 50,
            format = "JPG"
        )

        assertTrue("File must exist", testFile.exists())
        assertTrue("Compressed size must be under 50KB: ${result.fileSizeKb} KB", result.fileSizeKb < 50.0f)
        assertTrue("isUnder50Kb must be true", result.isUnder50Kb)
    }

    @Test
    fun `folder name sanitization cleans special characters`() {
        val raw = "1199917 / Manahal Naseer * (Sports Trainer)"
        val sanitized = FileManager.sanitizeFolderName(raw)
        assertTrue(!sanitized.contains("/"))
        assertTrue(!sanitized.contains("*"))
        assertEquals("1199917___Manahal_Naseer____Sports_Trainer_", sanitized)
    }
}
