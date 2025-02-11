package com.onotole79.homeguard

import android.content.Context
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LINE_END = "\r\n"


public class MyLog(context: Context, string: String) {

    private var date = SimpleDateFormat("yyyyMMdd_HH:mm:ss ", Locale.getDefault()).format(Date())
    init {
        val file = java.io.File(context.filesDir.toString() + "/log.txt")
        val outputStream = FileOutputStream(file, true)
        outputStream.write(date.toByteArray())
        outputStream.write(string.toByteArray())
        outputStream.write(LINE_END.toByteArray())
        outputStream.flush()
        outputStream.close()
    }
}