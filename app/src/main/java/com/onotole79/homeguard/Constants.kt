package com.onotole79.homeguard

object Constants {
    const val TAG = "MyTag"
    const val PREFERENCES = "main_preferences"
    const val NAME = "name"
    const val COMMAND = "command"
    const val VALUE = "value"
    const val MESSAGE = "message"
    const val MQTT_MESSENGER = "MQTT_Messenger"
    const val TOPIC = "8384RU834RUFIOWEJF94UFJ"
    const val SUBTOPIC = "subtopic"
    const val CONNECT = "connect"
    const val PUBLISH = "publish"
    const val PUBLISH_ARRAY = "PUBLISH_ARRAY"
    const val PICTURE = "PICTURE"
    const val ERROR = "ERROR"
    const val NOT_CONNECTED = "Не подключено"
    const val CONNECTING_STATUS = "connecting_status"
    const val PING = "PING"
    const val ALERT = "ALERT"
    const val LAST_ALERT = "LAST_ALERT"
    const val STOP = "STOP"
    const val STOPPED = "STOPPED"
    const val STARTED = "STARTED"

    const val START = "START"
    const val NO_ALERT = "NO_ALERT"
    const val ALL_OK = "Всё OK"
    const val WAS_ALERT = "Была тревога"


    const val CLEAR = "CLEAR"


    const val DASH = "--"
    const val NOTIF_ID = 1 // нельзя ставить в 0

    val listStartStop = listOf("Старт", "Стоп")
    val listOnOff = listOf("Выключено", "Включено")
    val listGuardClient = listOf("To guard", "To client")

}