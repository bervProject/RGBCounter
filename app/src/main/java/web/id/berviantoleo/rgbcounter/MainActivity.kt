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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.esafirm.imagepicker.features.ImagePicker
import com.esafirm.imagepicker.features.ReturnMode
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dmax.dialog.SpotsDialog
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference
import java.util.*

class MainActivity : AppCompatActivity() {
    private val lineData: LineData = LineData()
    private lateinit var dialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dialog = SpotsDialog.Builder().setContext(this).build()
        image_holder.setOnClickListener {
            ImagePicker.create(this)
                    .returnMode(ReturnMode.ALL)
                    .folderMode(true) // folder mode (false by default)
                    .toolbarFolderTitle("Select Folder") // folder selection title
                    .toolbarImageTitle("Select Image") // image selection title
                    .single() // single mode
                    .showCamera(true) // show camera or not (true by default)
                    .imageDirectory("RGB_Counter") // directory name for captured image  ("Camera" folder by default)
                    .enableLog(false) // disabling log
                    .start() // start image picker activity with request code
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            val image = ImagePicker.getFirstImageOrNull(data)
            if (image != null) {
                val path = image.path
                val uri = Uri.parse("file://$path")
                image_holder.setImageURI(uri, this)
                val bitmap = BitmapFactory.decodeFile(path)
                CountColour(this).execute(bitmap)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private class CountColour internal constructor(context: MainActivity) : AsyncTask<Bitmap?, Void?, Void?>() {
        private val activityWeakReference: WeakReference<MainActivity> = WeakReference(context)
        private var start: Long = 0
        override fun onPreExecute() {
            super.onPreExecute()
            val activity = activityWeakReference.get()
            if (activity != null) {
                activity.lineData.clearValues()
                activity.lineData.notifyDataChanged()
                activity.dialog.show()
                start = System.nanoTime()
            }
        }

        override fun doInBackground(vararg params: Bitmap?): Void? {
            val activity = activityWeakReference.get()
            val bitmap = params[0]
            if (activity == null || bitmap == null) {
                return null
            }
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
            return null
        }

        override fun onPostExecute(aVoid: Void?) {
            val activity = activityWeakReference.get()
            if (activity != null && activity.lineData.dataSetCount > 0) {
                activity.chart.data = activity.lineData
                activity.chart.invalidate()
                activity.dialog.dismiss()
                val end = System.nanoTime()
                val duration = end - start
                Log.i("Process Photo", String.format("Waktu dibutuhkan : %d", duration))
            }
        }

    }
}