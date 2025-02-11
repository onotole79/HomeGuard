package com.onotole79.homeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.onotole79.homeguard.Constants.ALERT
import com.onotole79.homeguard.Constants.ALL_OK
import com.onotole79.homeguard.Constants.COMMAND
import com.onotole79.homeguard.Constants.CONNECT
import com.onotole79.homeguard.Constants.CONNECTING_STATUS
import com.onotole79.homeguard.Constants.PICTURE
import com.onotole79.homeguard.Constants.LAST_ALERT
import com.onotole79.homeguard.Constants.TOPIC
import com.onotole79.homeguard.Constants.MESSAGE
import com.onotole79.homeguard.Constants.MQTT_MESSENGER
import com.onotole79.homeguard.Constants.NOTIF_ID
import com.onotole79.homeguard.Constants.NOT_CONNECTED
import com.onotole79.homeguard.Constants.PUBLISH
import com.onotole79.homeguard.Constants.PUBLISH_ARRAY
import com.onotole79.homeguard.Constants.SUBTOPIC
import com.onotole79.homeguard.Constants.VALUE
import com.onotole79.homeguard.Constants.WAS_ALERT
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttTopic
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.Charset
import java.util.Timer
import java.util.TimerTask


class MqttClientService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var mqttClient: MqttClient
    private lateinit var options: MqttConnectOptions
    private lateinit var timer: Timer
    private var lastAlert = 0L
    private var isDestroyed = false // только для отладки


    override fun onBind(intent: Intent): IBinder? { return null }



    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate() {
        MyLog(applicationContext, "Service onCreate")

        // создаём FOREGROUND сервис с уведомлением
        notificationService()
        super.onCreate()
    }


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun notificationService() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // для начала создаётся канал
        notificationManager.createNotificationChannel(NotificationChannel(
            MQTT_MESSENGER,
            "Guard",
            NotificationManager.IMPORTANCE_HIGH
        ))
        // запускаем сервис с уведомлением
        ServiceCompat.startForeground(this, NOTIF_ID,
            createNotification (ALL_OK),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
    }

    // создаём уведомление для сервиса
    private fun createNotification(content: String):Notification{
        val resultPendingIntent = PendingIntent.getActivity(
            this@MqttClientService,
            0,
            Intent(this@MqttClientService, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val notification = NotificationCompat.Builder(this@MqttClientService, MQTT_MESSENGER)
            .setContentTitle("Охрана дома")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setContentIntent(resultPendingIntent)
            .setOngoing(true)   // обязательно true, если уведомление уйдёт-сервис закроется системой
            .build()
        return notification
    }




    // каждый раз вызывается при startService
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        MyLog(applicationContext, "Service onStartCommand")

        // The Intent may be null if the service is being restarted after its process has gone away,
        // and it had previously returned anything except START_STICKY_COMPATIBILITY.
        if (intent == null) {
            MyLog(applicationContext, "Service start without Intent!!!")
            Log.w(Constants.TAG, "Service start without Intent!!!")
            return START_NOT_STICKY
        }

        val command = intent.getStringExtra(COMMAND)!!
        var value = ""
        var valueArray = byteArrayOf()
        if (command == PUBLISH_ARRAY)
            valueArray = intent.getByteArrayExtra(VALUE)!!
        else
            value = intent.getStringExtra(VALUE)!!

        MyLog(applicationContext, "$command:$value")

        when (command) {
            CONNECT -> {

                when (value){
                    "1" ->{
                        if (!::mqttClient.isInitialized){
                            // инициализация MQTT, подключение
                            Log.i(Constants.TAG, "Service start CREATING!!!")
                            initMQTT()
                            setTimer()
                        }else{
                            // обновляем статус подключения к MQTT и показываем последнее пришедшее состояние утечки
                            Log.i(Constants.TAG, "Service start CONNECTING_STATUS")
                            var connectStatus = getString(R.string.connected)
                            if (!mqttClient.isConnected) {
                                connectStatus = getString(R.string.disconnected)
                            }
                            messageToActivity(CONNECTING_STATUS, connectStatus)
                        }
                    }
                    "0" -> {
                        disconnect()
                    }

                }

            }

            PUBLISH -> {
                publish(TOPIC, value)
            }


            PUBLISH_ARRAY ->{
                try {
                    val mqttTopic: MqttTopic = mqttClient.getTopic("$TOPIC/$PICTURE")
                    val mqttMessage = MqttMessage(valueArray)
                    mqttTopic.publish(mqttMessage)
                    Log.i(Constants.TAG, "MQTT publish: byteArray")
                } catch (e: MqttException) {
                    Log.e(Constants.TAG, "MQTT publish error: " + e.stackTraceToString())
                    mqttClient.disconnect()
                }
            }

            LAST_ALERT -> {
                notificationManager.notify(NOTIF_ID, createNotification(WAS_ALERT))
            }

        }
        return START_STICKY
    }


    private fun initMQTT() {
        try {
            // ID должен быть у всех разный, иначе сервер будет отключать клиентов
            mqttClient = MqttClient("tcp://broker.hivemq.com:1883", "ClientID_" + System.currentTimeMillis(), MemoryPersistence())
            options = MqttConnectOptions()
            options.isAutomaticReconnect = true
            options.isCleanSession = false
            options.userName = ""
            options.password = "".toCharArray()
            options.isAutomaticReconnect = false    // автоматически плохо работает, будем сами
            options.keepAliveInterval = 1000


            // устанавливаем callback`и
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.i(Constants.TAG, "MQTT Отключено Callback \r\n" + cause?.stackTraceToString())
                    messageToActivity(CONNECTING_STATUS, getString(R.string.disconnected))
                }

                override fun messageArrived(fullTopic: String?, mqttMessage: MqttMessage?) {
                    val receivedMessage = mqttMessage?.toString()
                    if (!receivedMessage.isNullOrEmpty()) {
                        val utf8String = receivedMessage.toByteArray(Charset.forName("UTF-8"))

                        var subTopic = ""
                        if (fullTopic != null) {
                            if (fullTopic.length>TOPIC.length+1){
                                subTopic = fullTopic.substring(TOPIC.length+1)
                            }
                        }

                        val message = String(utf8String, Charset.forName("UTF-8"))

                        when (subTopic){
                            "" ->{
                                Log.i(Constants.TAG, message)
                                when (message){
                                    ALERT ->{
                                        // включаем экран телефона на время
                                        WakeLock.acquire(applicationContext, 20*1000)
                                        if (System.currentTimeMillis() - lastAlert > 5*60*1000){
                                            notificationManager.notify(NOTIF_ID, createNotification(ALERT))
                                            lastAlert = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                            PICTURE -> {
                                Files().savePhoto(mqttMessage.payload)
                                messageToActivity(subTopic, mqttMessage.payload)
                                return
                            }
                        }

                        // передаём сообщение в Activity
                        messageToActivity(subTopic, message)
                    }

                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.i(Constants.TAG, "MQTT deliveryComplete")
                }
            })

            // подключаемся, там же делаем подписку
            connect(options)

        } catch (e: MqttException) {
            Log.i(Constants.TAG, "MQTT error: " + e.stackTraceToString())
        }
    }


    private fun connect(options: MqttConnectOptions) {
        try {
            if (!mqttClient.isConnected) {
                Log.i(Constants.TAG, "MQTT Подключение...")
                messageToActivity(CONNECTING_STATUS, getString(R.string.connecting))
                mqttClient.connect(options)
                Log.i(Constants.TAG, "MQTT Подключено")
                messageToActivity(CONNECTING_STATUS, getString(R.string.connected))
                mqttClient.subscribe("$TOPIC/#", 1)
                Log.i(Constants.TAG, "MQTT Subscribe: $TOPIC/#")
                publish(TOPIC, LAST_ALERT)

            }
        } catch (e: MqttException) {
            Log.e(Constants.TAG, "MQTT connect error: " + e.stackTraceToString())
        }
    }


    private fun publish(topic: String, message: String) {
        try {
            val mqttTopic: MqttTopic = mqttClient.getTopic(topic)
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = 2
            mqttTopic.publish(mqttMessage)
            Log.i(Constants.TAG, "MQTT publish: $message")
        } catch (e: MqttException) {
            Log.e(Constants.TAG, "MQTT publish error: " + e.stackTraceToString())
            disconnect()
        }
    }

    private fun disconnect() {
        if (::mqttClient.isInitialized){
            try {
                mqttClient.disconnect()
                Log.i(Constants.TAG, "MQTT disconnect")
                messageToActivity(CONNECTING_STATUS, NOT_CONNECTED)
            } catch (e: MqttException) {
                Log.e(Constants.TAG, "MQTT error disconnect: " + e.message)
            }
        }else{
            // опытным путём выяснилось: когда вызывается onDestroy при неинициализированных объектах,
            // означает, служба закрывается системой без перезапуска
            // перезапускаем службу сами
            sendBroadcast(Intent(applicationContext, BootBroadcast::class.java))
        }
    }


    private fun messageToActivity(subTopic: String, message: String) {
        val intent = Intent(MQTT_MESSENGER)
        intent.putExtra(SUBTOPIC, subTopic)
        intent.putExtra(MESSAGE, message)
        sendBroadcast(intent)
    }
    private fun messageToActivity(subTopic: String, value: ByteArray) {
        val intent = Intent(MQTT_MESSENGER)
        intent.putExtra(SUBTOPIC, subTopic)
        intent.putExtra(MESSAGE, value)
        sendBroadcast(intent)
    }



    // в таймере проверяем соединение с брокером, если долго не подключались, пытаемся снова
    private fun setTimer() {
        Log.i(Constants.TAG, "Start timer")
        timer = Timer()
        var timerCount = 0  // необходим только для отладки
        var notConnectCount = 0
        timer.schedule(object : TimerTask() {
            override fun run() {

                val icC1 = mqttClient.isConnected //TODO проверка
                val icC2 = mqttClient.isConnected() //TODO проверка
                if (mqttClient.isConnected.xor(mqttClient.isConnected()))
                    messageToActivity(CONNECTING_STATUS, "Разница Значений!!!") //TODO проверка

                if (!mqttClient.isConnected) {
                    Log.i(Constants.TAG, "не подключено: $notConnectCount")
                    notConnectCount++
                    Log.i(Constants.TAG, "Timer: Reconnecting")
                    messageToActivity(CONNECTING_STATUS, getString(R.string.connecting))
                    try {
                        mqttClient.reconnect()
                    } catch (e: MqttException) {
                        Log.e(Constants.TAG, "MQTT error reconnect: " + e.message)
                    }
//                    connect(options)
                } else{
                    // если было отключение, заново подписываемся и запрашиваем состояние
                    if (notConnectCount>0){
                        messageToActivity(CONNECTING_STATUS, getString(R.string.connected))
                        try {
                            mqttClient.subscribe("$TOPIC/#", 1)
                            Log.i(Constants.TAG, "MQTT Subscribe: $TOPIC/#")
                        } catch (e: MqttException) {
                            Log.e(Constants.TAG, "MQTT error subscribe: " + e.message)
                            messageToActivity(CONNECTING_STATUS, getString(R.string.subscribe_error))
                        }
                        notConnectCount = 0
                    }
                    Log.i(Constants.TAG, "Timer: $timerCount")
                    // каждую минуту посылаем запрос последней тревоги (также используется в качестве PING`a)
                    if (timerCount % 6 == 0) publish(TOPIC, LAST_ALERT)
                }
                timerCount++
            }
        }, 0, 10 * 1000)
    }


    override fun onDestroy() {
        super.onDestroy()

        MyLog(applicationContext, "Service onDestroy")
        Log.i(Constants.TAG, "Service onDestroy")

        isDestroyed = true

        // останавливаем таймер
        if (::timer.isInitialized)
            timer.cancel()
        Log.i(Constants.TAG, "Timer: остановлен" )
        // отключаемся от MQTT-брокера
        disconnect()
    }
}