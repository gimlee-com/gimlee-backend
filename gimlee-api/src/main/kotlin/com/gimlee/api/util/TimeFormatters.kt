package com.gimlee.api.util

import kotlin.math.floor
import kotlin.math.roundToInt

fun convertStringHourMinuteToDecimal(stringHourMinute: String): Double {
    val splitValue = stringHourMinute.split(":")
    val hourDouble = splitValue[0].toDouble()
    val minuteDouble = splitValue[1].toDouble() * (1.0/60)
    return hourDouble + minuteDouble
}

fun convertDecimalToHourMinuteString(decimalHourMinute: Double): String {
    val hour = decimalHourMinute.toInt()
    val minute = ((decimalHourMinute - hour) * 60).roundToInt().toString().padStart(2, '0')
    return "$hour:$minute"
}