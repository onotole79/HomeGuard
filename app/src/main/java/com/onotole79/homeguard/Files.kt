package com.onotole79.homeguard

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class Files{

    private val photoDirection = File(Environment.getExternalStorageDirectory(), "/DCIM/HomeGuard/")

    fun savePhoto(jpeg: ByteArray){
        val photo = File(photoDirection, SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())+".jpeg")
        photoDirection.mkdirs()
        if (photo.exists()) {
            photo.delete();
        }
        try {
            val fos = FileOutputStream(photo.path);
            fos.write(jpeg);
            fos.close();
        }
        catch (e: IOException) {
            Log.e(Constants.TAG, "Photo save error: ", e)
        }
    }

    fun getPhotoArray(): Array<File>{
        return photoDirection.listFiles()!!
    }

    fun deletePhoto(path: String){
        File(path).delete()
    }

}