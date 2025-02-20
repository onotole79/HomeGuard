package com.onotole79.homeguard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.onotole79.homeguard.Constants.ALERT
import com.onotole79.homeguard.Constants.ALL_OK
import com.onotole79.homeguard.Constants.CLEAR
import com.onotole79.homeguard.Constants.COMMAND
import com.onotole79.homeguard.Constants.CONNECT
import com.onotole79.homeguard.Constants.CONNECTING_STATUS
import com.onotole79.homeguard.Constants.DASH
import com.onotole79.homeguard.Constants.ERROR
import com.onotole79.homeguard.Constants.LAST_ALERT
import com.onotole79.homeguard.Constants.MESSAGE
import com.onotole79.homeguard.Constants.MQTT_MESSENGER
import com.onotole79.homeguard.Constants.NOT_CONNECTED
import com.onotole79.homeguard.Constants.NO_ALERT
import com.onotole79.homeguard.Constants.PICTURE
import com.onotole79.homeguard.Constants.PING
import com.onotole79.homeguard.Constants.PUBLISH
import com.onotole79.homeguard.Constants.PUBLISH_ARRAY
import com.onotole79.homeguard.Constants.START
import com.onotole79.homeguard.Constants.STARTED
import com.onotole79.homeguard.Constants.STOP
import com.onotole79.homeguard.Constants.STOPPED
import com.onotole79.homeguard.Constants.SUBTOPIC
import com.onotole79.homeguard.Constants.VALUE
import com.onotole79.homeguard.Constants.listGuardClient
import com.onotole79.homeguard.Constants.listOnOff
import com.onotole79.homeguard.Constants.listStartStop
import com.onotole79.homeguard.ui.theme.HomeGuardTheme
import com.onotole79.homeguard.ui.theme.LockScreenOrientation
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors


/*
НА ТЕЛЕФОНЕ ДОЛЖНЫ БЫТЬ ОТКЛЮЧНЫ ЗАСТАВКА И БЛОКИРОВКА ЭКРАНА!!!!
 */



class MainActivity : ComponentActivity() {

    private var connectStatus = mutableStateOf(NOT_CONNECTED)
    private var errorStatus = mutableStateOf("")
    private var pingStatus = mutableStateOf(DASH)
    private var isGuardOrClient = mutableStateOf(false)  // guard/client
    private var isGuardRun = mutableStateOf(false)
    private var isAlert = mutableStateOf(false)

    // client
    private var alertStatus = mutableStateOf(DASH)
    private var bitmapTMP = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    private var photoDescriptionTrigger = 1 // для обновления изображений
    private val photoDescription = mutableStateOf ("1")

    private var isGallery = mutableStateOf(true)    // показываем все фото или одну выбранную
    private var photoToShow = bitmapTMP.asImageBitmap()

    // guard
    private var delayStart = mutableStateOf("10")
    private var counter = mutableStateOf("")
    private lateinit var countdownTimer:CountDownTimer
    private var isCounting = false
    private var chosenCam = mutableIntStateOf(CameraCharacteristics.LENS_FACING_BACK)
    private var isCameraShow = mutableStateOf(false)
    private lateinit var cameraController: LifecycleCameraController
    private var lastAlertTime: Long = 0



    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableMQTT()

        setContent {
            LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Main()
            }
        }
    }



    @Preview(showBackground = true)
    @Composable
    fun Main(){
        HomeGuardTheme{
            MainPreview()
        }
    }

    @Composable
    fun MainPreview() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(color = Color.Cyan)
        ) {
            Column(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MQTT сервер:")
                    Text(
                        text = connectStatus.value,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 15.dp)
                    )
                    Button(
                        onClick = {mqttService(CONNECT,"0")},
                    ) { Text(text = "Reconnect")}
                }

                if (errorStatus.value.isNotEmpty()){
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorStatus.value,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 5.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ping:")
                    Text(
                        text = pingStatus.value,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 15.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically

                ) {
                    Text(text = "Alert: ")
                    Text(
                        text = alertStatus.value,
                        modifier = Modifier.padding(start = 10.dp)
                    )

                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier.fillMaxWidth()
                    ){
                        Button(
                            onClick = {mqttService(PUBLISH, CLEAR)},
                            modifier = Modifier
                                .padding(end = 20.dp)

                        ) { Text(text = "Очистить")}

                    }


                }

                Button(
                    onClick = {
                        isGuardOrClient.value = !isGuardOrClient.value
                    },
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = listGuardClient[if (isGuardOrClient.value) 1 else 0],
                    )
                }

                if (isGuardOrClient.value) Guard()
                else Client()

            }
        }
    }




    @Composable
    fun Guard(){
        val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
        if (!prepareCamera()) return

        if (!threadCycleTakePicture.isAlive){
            threadCycleTakePicture.start()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(color = Color.Cyan)
        ) {
            Column(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier
                            .padding(end = 10.dp),
                        text = "Задержка: "

                    )
                    with(delayStart) {
                        MyBasicTextField(
                            value = value,
                            labelText = "сек",
                            interactionSource = interactionSource,
                            keyboardOptions =  KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = {
                                value = if (it.isEmpty()) "0"    // isEmpty() всегда в начале!!!
                                else if (it.toInt()>2) it
                                else "0"
                            },
                        )
                    }
                    Button(
                        onClick = {
                            if (!isGuardRun.value) delayStartTimer()
                            else mqttService(PUBLISH,STOP)
                        },
                        modifier = Modifier
                            .padding(start = 20.dp)
                    ) {
                        Text(
                            text = listStartStop[if(isCounting.or(isGuardRun.value)) 1 else 0])
                    }

                    Text(
                        modifier = Modifier
                            .padding(start = 10.dp),
                        text = counter.value
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Обнаружение:")
                    Text(
                        text = listOnOff[if(isGuardRun.value) 1 else 0],
                        modifier = Modifier
                            .padding(start = 5.dp, end = 15.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = "Камера",fontSize = 18.sp,
                    )

                    Switch(
                        modifier = Modifier
                            .padding(start = 20.dp),
                        checked = chosenCam.intValue == 1,
                        onCheckedChange = {
                            chosenCam.intValue = chosenCam.intValue.xor(1)
                        }
                    )

                    Button(
                        onClick = {
                            isCameraShow.value = !isCameraShow.value
                        },
                        modifier = Modifier
                            .padding(start = 20.dp)
                    ) {Text(text = "Тест")}



                }

                if (isCameraShow.value) CameraPreview()

            }

        }

    }


    @Composable
    fun Client(){

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(15.dp))
        ){
            Column(
                modifier = Modifier.padding(5.dp)
            ){
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            mqttService(PUBLISH,PICTURE)
                        },
                    ) {Text("Снимок")}

                    Button(
                        modifier = Modifier
                            .padding(start = 10.dp),
                        onClick = {
                            Files().deleteAll()
                            recompose()
                        },
                    ) {Text("Удалить всё")}
                }

                if (isGallery.value){

                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ){
                        // показываем все фото
                        val arrayPhoto = Files().getPhotoArray()
                        var i = 0
                        var ret = false
                        while (!ret && arrayPhoto.isNotEmpty()){
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (k in 0..2) {
                                    val photoPath = arrayPhoto[i].path
                                    val bitmap = BitmapFactory.decodeFile(photoPath).asImageBitmap()

                                    Box(
                                        modifier = Modifier
                                    ){
                                        Image(
                                            bitmap,
                                            photoDescription.value,
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(160.dp)
                                                .clickable {
                                                    photoToShow = bitmap
                                                    recompose(false)
                                                },
                                        )
                                        Image(
                                            painterResource(R.drawable.ic_delete_32),
                                            "",
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .width(32.dp)
                                                .height(32.dp)
                                                .clickable {
                                                    Files().deletePhoto(photoPath)
                                                    recompose()
                                                }
                                        )
                                    }

                                    i++
                                    if (i==arrayPhoto.size){
                                        ret = true
                                        break
                                    }
                                }
                            }
                        }
                    }

                }else {
                    Image(
                        photoToShow,
                        photoDescription.value,
                        modifier = Modifier
                            .clickable {
                                recompose()
                            },
                    )
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyBasicTextField(
        labelText: String,
        value: String,
        onValueChange: (String) -> Unit,
        width: Int = 65,
        interactionSource: MutableInteractionSource,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    ) {
        BasicTextField(
            value = value,
            modifier = Modifier
                .height(36.dp)
                .width(width.dp)
                .padding(start = 2.dp),
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 18.sp),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions

        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField, enabled = true, singleLine = true,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                label = { Text(text = labelText) },
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                    top = 0.dp, bottom = 0.dp, start = 0.dp, end = 0.dp
                )
            )
        }
    }






    // включаем MQTT-сервис
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun enableMQTT(){
        // Регистрируем слушалку MQTT сообщений из сервиса
        registerReceiver(
            mMessageReceiver, IntentFilter(MQTT_MESSENGER), RECEIVER_NOT_EXPORTED
        )
        // запускаем сервис для соединения с MQTT-брокером
        mqttService(CONNECT,"1") // в сервисе определяем новый ли запуск или определение состояние MQTT
    }

    // ресивер из Service
    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val subtopic = intent.getStringExtra(SUBTOPIC)!!
            var message = ""
            var valueArray = byteArrayOf()
            if (subtopic == PICTURE)
                valueArray = intent.getByteArrayExtra(MESSAGE)!!
            else
                message = intent.getStringExtra(MESSAGE)!!


            val date = SimpleDateFormat("dd.MM HH:mm ", Locale.getDefault()).format(Date())

            when(subtopic){
                CONNECTING_STATUS -> connectStatus.value = message

                LAST_ALERT -> {
                    if (message == NO_ALERT)
                        alertStatus.value = ALL_OK

                    else {
                        if (!alertStatus.value.contains(ALERT)){
                            val lastAlert = message.toLong()
                            val hours:Long = lastAlert/3600000
                            val minutes:Long = (lastAlert % 3600000)/60000
                            alertStatus.value = "$hours ч. $minutes мин. назад"
                        }
                        mqttService(LAST_ALERT,"")
                    }
                }

                PICTURE -> {
                    if (!isGuardOrClient.value){
                        photoToShow = BitmapFactory.decodeByteArray(valueArray, 0, valueArray.size).asImageBitmap()
                        recompose(false)
                    }
                }


                ERROR -> {
                    errorStatus.value = message
                }

                "" -> {
                    when (message){
                        ALERT -> {
                            isAlert.value = true
                            lastAlertTime = System.currentTimeMillis()
                            alertStatus.value = date + ALERT
                            isCameraShow.value = true
                        }

                        PICTURE -> {
                            if (isGuardOrClient.value){

                                // если камера уже включена, то сразу делаем снимок
                                if (isCameraShow.value)
                                    takePicture()
                                else{
                                    // Включаем камеру, в другом потоке ожидаем 3 сек для инициализации камеры
                                    // и в основном потоке берём снимок

                                    // Но в начале включаем экран телефона
                                    WakeLock.acquire(applicationContext, 5*1000)

                                    isCameraShow.value = true
                                    Thread {
                                        Thread.sleep(3*1000)
                                        runOnUiThread(kotlinx.coroutines.Runnable { takePicture() })
                                        Thread.sleep(1*1000)
                                        isCameraShow.value = false
                                    }.start()
                                }
                            }
                        }

                        STARTED -> isGuardRun.value = true
                        STOPPED ->  isGuardRun.value = false

                        else -> if (message.startsWith(PING))
                            pingStatus.value = date + message

                    }
                }
            }
        }
    }


    private fun mqttService(command: String, value: String){
        Thread {
            startService(
                Intent(this, MqttClientService::class.java)
                    .putExtra(COMMAND, command)
                    .putExtra(VALUE, value)
            )
        }.start()
    }

    private fun mqttService(value: ByteArray){
        startService(
            Intent(this, MqttClientService::class.java)
                .putExtra(COMMAND, PUBLISH_ARRAY)
                .putExtra(VALUE, value)
        )
    }




    private fun prepareCamera(): Boolean {
        // Проверяем доступ к камере
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
        ) {
            val permission = arrayOf(
                Manifest.permission.CAMERA
            )
            requestPermissions(permission, 1122)
            return false
        }
        return true
    }



    @Composable
    private fun CameraPreview() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // Remember a LifecycleCameraController for this composable
        cameraController = remember {
            LifecycleCameraController(context)
                .apply {
                    bindToLifecycle(lifecycleOwner)
                }
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(chosenCam.intValue)
            .build()
        cameraController.cameraSelector = cameraSelector

        Button(
            onClick = {takePicture()},
        ) {Text("Снимок")}

        AndroidView(
            modifier = Modifier
                .height(320.dp)
                .width(240.dp),
            factory = { ctx ->
                // Initialize the PreviewView and configure it
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_START
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController // Set the controller to manage the camera lifecycle
                }
            },

            onRelease = {
                // Release the camera controller when the composable is removed from the screen
                cameraController.unbind()
                Log.i(Constants.TAG, "Camera released")
            }
        ){}
    }


    private fun takePicture(): Boolean{
        // Камера может быть ещё не инициализирована
        if (!::cameraController.isInitialized) return false
        if (cameraController.cameraInfo == null) return false

        cameraController.takePicture(
            Executors.newSingleThreadExecutor(),
            object: ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    val byteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    var newBitmap = Bitmap.createScaledBitmap(bitmap, 1600, 1200, true)

                    // переворачиваем картинку
                    val matrix = Matrix()
                    matrix.postRotate(90F)
                    newBitmap =  Bitmap.createBitmap(
                        newBitmap,0,0,newBitmap.width,newBitmap.height,matrix,true
                    )

                    // пишет текст даты со временем
                    val canvas = Canvas(newBitmap)
                    val paint = Paint()
                    paint.color = resources.getColor(R.color.purple_200)
                    paint.textSize = 50F
                    paint.isAntiAlias = true
                    paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)) // Text Overlapping Pattern
                    // some more settings...
                    canvas.drawText(SimpleDateFormat("dd.MM HH:mm:ss ", Locale.getDefault()).format(Date()),
                        10F, newBitmap.height - (paint.textSize)/2, paint)

                    // сжимаем в Jpeg
                    val outStream = ByteArrayOutputStream()
                    newBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                    val bytesArray = outStream.toByteArray()

                    // отсылаем картинку
                    mqttService(bytesArray)
                    Log.i(Constants.TAG, "onCaptureSuccess")

                    outStream.flush()
                    outStream.close()
                    image.close()
                    bitmap.recycle()
                }

                override fun onError(exc: ImageCaptureException) {
                    super.onError(exc)
                    // Обработайте ошибку захвата изображения
                    Log.e("CameraCapture", "Ошибка захвата изображения: ${exc.message}", exc)
                }
            }
        )
        return true
    }

    private val threadCycleTakePicture = Thread{
        Log.i(Constants.TAG, "thread taking picture started")
        var timeDelay = 100L    // ожидаем инициализацию камеры
        while(true){
            Thread.sleep(timeDelay)
            if (isGuardOrClient.value and isAlert.value){
                if ((System.currentTimeMillis() - lastAlertTime) < 15*1000){
                    runOnUiThread(kotlinx.coroutines.Runnable {
                        if (takePicture())
                            timeDelay = 2*1000  // камера инициализирована, каждые 2сек отсылаем скрины
                    })
                }else {
                    isAlert.value = false
                    isCameraShow.value = false
                }
                Log.i(Constants.TAG, "thread working")
            }
        }
    }


    private fun delayStartTimer() {
        if (isCounting) {
            countdownTimer.cancel()
            isCounting = false
            counter.value = ""
        }
        else{
            countdownTimer = object : CountDownTimer(delayStart.value.toLong()*1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    isCounting = true
                    counter.value = (millisUntilFinished/1000).toString()
                }
                override fun onFinish() {
                    isCounting = false
                    counter.value = ""
                    mqttService(PUBLISH,START)
                }
            }.start()
        }
    }


private fun recompose(gallery: Boolean = true) {
    // показывать в виде галереи или одиночно
    isGallery.value = gallery
    // Чтобы обновились фото на экране, меняем Description
    photoDescriptionTrigger = photoDescriptionTrigger.xor(1)
    photoDescription.value = photoDescriptionTrigger.toString()
}



    override fun onStop() {
        super.onStop()
        Log.i(Constants.TAG, "onStop")
    }
    override fun onResume() {
        super.onResume()
        Log.i(Constants.TAG, "onResume")

    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mMessageReceiver)
    }
}






