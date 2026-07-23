/**
 * Copyright 2018 Bervianto Leo Pratama
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package web.id.berviantoleo.rgbcounter

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dmax.dialog.SpotsDialog
import web.id.berviantoleo.rgbcounter.databinding.ActivityMainBinding
import java.io.OutputStream
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity() {
    private val lineData: LineData = LineData()
    private lateinit var dialog: AlertDialog
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val launcher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                binding.imageHolder.setImageURI(uri, this)
                val runnableCounter = ColourCounter(this, uri)
                Thread(runnableCounter).start()
            } else {
                Log.d("PhotoPicker", "No media selected")
                Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show()
            }
        }
        dialog = SpotsDialog.Builder().setContext(this).build()
        binding.imageHolder.setOnClickListener {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.saveToGallery.setOnClickListener {
            val fileName = "chart-${System.currentTimeMillis()}.jpg"
            saveChartToGallery(fileName)
        }
    }

    class ColourCounter(context: MainActivity, private val uri: Uri) : Runnable {
        private val activityWeakReference: WeakReference<MainActivity> = WeakReference(context)
        private var start: Long = 0

        override fun run() {
            val activity = activityWeakReference.get() ?: return
            activity.runOnUiThread {
                activity.lineData.clearValues()
                activity.lineData.notifyDataChanged()
                activity.dialog.show()
            }

            val bitmap = activity.decodeBitmap(uri)
            if (bitmap == null) {
                activity.runOnUiThread {
                    activity.dialog.dismiss()
                    Toast.makeText(activity, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
                return
            }

            start = System.nanoTime()
            val width = bitmap.width
            val height = bitmap.height
            val red = IntArray(256)
            val green = IntArray(256)
            val blue = IntArray(256)

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                red[r]++
                green[g]++
                blue[b]++
            }

            val entriesRed: MutableList<Entry> = ArrayList()
            val entriesGreen: MutableList<Entry> = ArrayList()
            val entriesBlue: MutableList<Entry> = ArrayList()
            for (i in 0..255) {
                entriesRed.add(Entry(i.toFloat(), red[i].toFloat()))
                entriesGreen.add(Entry(i.toFloat(), green[i].toFloat()))
                entriesBlue.add(Entry(i.toFloat(), blue[i].toFloat()))
            }
            val dataSetRed = LineDataSet(entriesRed, "red")
            val dataSetGreen = LineDataSet(entriesGreen, "green")
            val dataSetBlue = LineDataSet(entriesBlue, "blue")
            dataSetRed.color = Color.RED
            dataSetRed.setDrawCircles(false)
            dataSetGreen.color = Color.GREEN
            dataSetGreen.setDrawCircles(false)
            dataSetBlue.color = Color.BLUE
            dataSetBlue.setDrawCircles(false)

            activity.runOnUiThread {
                activity.lineData.addDataSet(dataSetRed)
                activity.lineData.addDataSet(dataSetGreen)
                activity.lineData.addDataSet(dataSetBlue)
                
                activity.binding.chart.data = activity.lineData
                activity.binding.chart.invalidate()
                activity.dialog.dismiss()
                
                if (activity.lineData.dataSetCount > 0) {
                    val end = System.nanoTime()
                    val duration = end - start
                    val timeProcess = "Time to process: ${duration / 1_000_000} ms"
                    Log.i("Process Photo", timeProcess)
                    Toast.makeText(activity, timeProcess, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "No data to display", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveChartToGallery(fileName: String) {
        val bitmap = binding.chart.chartBitmap
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/RGBCounter")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Toast.makeText(this, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving chart", e)
            contentResolver.delete(uri, null, null)
            Toast.makeText(this, "Failed to save chart: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= 1024 && options.outHeight / (sampleSize * 2) >= 1024) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e("ColourCounter", "Error decoding bitmap", e)
            null
        }
    }
}