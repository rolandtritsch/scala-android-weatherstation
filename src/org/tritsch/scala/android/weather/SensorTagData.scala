package org.tritsch.scala.android.weather

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log

object SensorTagData {
  val TAG = SensorTagData.getClass.getName

  def extractHumAmbientTemperature(c: BluetoothGattCharacteristic): Double = {
    require(c != null, "c cannot be null")
    Log.v(SensorTagData.TAG, "Enter - extractHumAmbientTemperature()")
    val temp = -46.85 + ((175.72/65536) * shortSignedAtOffset(c, 0))
    Log.d(SensorTagData.TAG, "Temperature >${temp}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractHumAmbientTemperature()")
    temp
  }

  def extractHumidity(c: BluetoothGattCharacteristic): Double = {
    Log.v(SensorTagData.TAG, "Enter - extractHumidity()")
    var a = shortUnsignedAtOffset(c, 2)
    // bits [1..0] are status bits and need to be cleared
    a = a - (a % 4)
    val hume = ((-6f) + 125f * (a / 65535f))
    Log.d(SensorTagData.TAG, s"Humidity >${hume}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractHumidity()")
    hume
  }

  def extractCalibrationCoefficients(c: BluetoothGattCharacteristic): Array[Integer] = {
    Log.v(SensorTagData.TAG, "Enter - extractCalibrationCoefficients()")
    var coefficients = new Array[Integer](8)

    coefficients(0) = shortUnsignedAtOffset(c, 0)
    coefficients(1) = shortUnsignedAtOffset(c, 2)
    coefficients(2) = shortUnsignedAtOffset(c, 4)
    coefficients(3) = shortUnsignedAtOffset(c, 6)
    coefficients(4) = shortSignedAtOffset(c, 8)
    coefficients(5) = shortSignedAtOffset(c, 10)
    coefficients(6) = shortSignedAtOffset(c, 12)
    coefficients(7) = shortSignedAtOffset(c, 14)

    Log.v(SensorTagData.TAG, "Leave - extractCalibrationCoefficients()")
    coefficients
  }

  def extractBarTemperature(characteristic: BluetoothGattCharacteristic, c: Array[Integer]): Double = {
    Log.v(SensorTagData.TAG, "Enter - extractBarTemperature()")
    val t_r = shortSignedAtOffset(characteristic, 0)
    val t_a = (100 * (c(0) * t_r / Math.pow(2,8) + c(1) * Math.pow(2,6))) / Math.pow(2,16)

    val temp = t_a / 100
    Log.d(SensorTagData.TAG, s"BarTemperature >${temp}< ...")
    Log.v(SensorTagData.TAG, "Enter - extractBarTemperature()")
    temp
  }

  def extractBarometer(characteristic: BluetoothGattCharacteristic, c: Array[Integer]): Double = {
    // c holds the calibration coefficients
    Log.d(SensorTagData.TAG, "Enter - extractBarometer()")

    val t_r = shortSignedAtOffset(characteristic, 0)
    val p_r = shortUnsignedAtOffset(characteristic, 2)

    val S = c(2) + c(3) * t_r / Math.pow(2,17) + ((c(4) * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,19)
    val O = c(5) * Math.pow(2,14) + c(6) * t_r / Math.pow(2,3) + ((c(7) * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,4)
    val p_a = (S * p_r + O) / Math.pow(2,14)

    // Convert pascal to in. Hg
    // @todo - use a constant here
    val p_hg = p_a * 0.000296
    Log.d(SensorTagData.TAG, s"BarPressure >${p_hg}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractBarometer()")
    p_hg
  }

  /**
    * Gyroscope, Magnetometer, Barometer, IR temperature
    * all store 16 bit two's complement values in the awkward format
    * LSB MSB, which cannot be directly parsed as getIntValue(FORMAT_SINT16, offset)
    * because the bytes are stored in the "wrong" direction.
    *
    * This function extracts these 16 bit two's complement values.
    * */
  private def shortSignedAtOffset(c: BluetoothGattCharacteristic, offset: Integer): Integer =  {
    val lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset)
    val upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1) // Note: interpret MSB as signed.

    val s = (upperByte << 8) + lowerByte
    Log.v(SensorTagData.TAG, s"shortSignedAtOffset >${s}< ...")
    s
  }

  private def shortUnsignedAtOffset(c: BluetoothGattCharacteristic, offset: Integer): Integer = {
    val lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset)
    val upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1) // Note: interpret MSB as unsigned.

    val s = (upperByte << 8) + lowerByte
    Log.v(SensorTagData.TAG, s"shortUnsignedAtOffset >${s}< ...")
    s
  }
}
