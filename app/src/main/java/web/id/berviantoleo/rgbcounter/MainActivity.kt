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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.esafirm.imagepicker.features.registerImagePicker
import com.esafirm.imagepicker.model.Image
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import dmax.dialog.SpotsDialog
import web.id.berviantoleo.rgbcounter.databinding.ActivityMainBinding
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
        dialog = SpotsDialog.Builder().setContext(this).build()
        binding.imageHolder.setOnClickListener {
            val launcher = registerImagePicker {
                    result: List<Image> ->
                val image = result.firstOrNull()
                if (image != null) {
                    val path = image.path
                    val uri = Uri.parse("file://$path")
                    binding.imageHolder.setImageURI(uri, this)
                    val bitmap = BitmapFactory.decodeFile(path)
                    val runnableCounter = ColourCounter(this, bitmap)
                    Thread(runnableCounter).start()
                }
            }

            launcher.launch()
        }
        binding.saveToGallery.setOnClickListener {
            val fileLocation = "chart-${System.currentTimeMillis()}.jpg"
            binding.chart.saveToGallery(fileLocation)
            Toast.makeText(this, "Saved to: $fileLocation", Toast.LENGTH_LONG).show()
        }
    }

    class ColourCounter constructor(context: MainActivity, private val bitmap: Bitmap?) : Runnable {
        private val activityWeakReference: WeakReference<MainActivity> = WeakReference(context)
        private var start: Long = 0

        override fun run() {
            val activity = activityWeakReference.get()
            if (activity != null && bitmap != null) {
                runOnUiThread {
                    run()
                    {
                        activity.lineData.clearValues()
                        activity.lineData.notifyDataChanged()
                        activity.dialog.show()
                    }
                }
                start = System.nanoTime()
                val width = bitmap.width
                val height = bitmap.height
                val red = IntArray(256)
                val green = IntArray(256)
                val blue = IntArray(256)
                for (j in 0 until height) {
                    for (i in 0 until width) {
                        val color = bitmap.getPixel(i, j)
                        val redColor = color shr 16 and 0xff
                        val greenColor = color shr 8 and 0xff
                        val blueColor = color and 0xff
                        red[redColor] += 1
                        green[greenColor] += 1
                        blue[blueColor] += 1
                    }
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
                activity.lineData.addDataSet(dataSetRed)
                activity.lineData.addDataSet(dataSetGreen)
                activity.lineData.addDataSet(dataSetBlue)
                if (activity.lineData.dataSetCount > 0) {
                    activity.binding.chart.data = activity.lineData
                    runOnUiThread {
                        run()
                        {
                            activity.binding.chart.invalidate()
                            activity.dialog.dismiss()
                        }
                    }
                    val end = System.nanoTime()
                    val duration = end - start
                    val timeProcess = "Time to process: $duration ns"
                    Log.i("Process Photo", timeProcess)
                    runOnUiThread {
                        run()
                        {
                            Toast.makeText(activity, timeProcess, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}