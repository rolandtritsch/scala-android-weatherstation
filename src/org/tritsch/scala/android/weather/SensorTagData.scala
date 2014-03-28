package org.tritsch.scala.android.weather

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log

final object SensorTagData {
  val TAG = SensorTagData.getClass.getName

  def extractHumAmbientTemperature(c: BluetoothGattCharacteristic): Double = {
    require(c != null, "c cannot be null")
    Log.v(SensorTagData.TAG, "Enter - extractHumAmbientTemperature()")

    val t = -46.85 + ((175.72/65536) * shortSignedAtOffset(c, 0))

    Log.d(SensorTagData.TAG, s"Temperature >${t}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractHumAmbientTemperature()")
    t
  }

  def extractHumidity(c: BluetoothGattCharacteristic): Double = {
    require(c != null, "c cannot be null")
    Log.v(SensorTagData.TAG, "Enter - extractHumidity()")

    val b = shortUnsignedAtOffset(c, 2)
    // bits [1..0] are status bits and need to be cleared
    val a = b - (b % 4)
    val h = ((-6f) + 125f * (a / 65535f))

    Log.d(SensorTagData.TAG, s"Humidity >${h}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractHumidity()")
    h
  }

  def extractCalibrationCoefficients(c: BluetoothGattCharacteristic): Array[Int] = {
    require(c != null, "c cannot be null")
    Log.v(SensorTagData.TAG, "Enter - extractCalibrationCoefficients()")
    val coefficients = Array.fill(8)(0)

    coefficients(0) = shortUnsignedAtOffset(c, 0)
    coefficients(1) = shortUnsignedAtOffset(c, 2)
    coefficients(2) = shortUnsignedAtOffset(c, 4)
    coefficients(3) = shortUnsignedAtOffset(c, 6)
    coefficients(4) = shortSignedAtOffset(c, 8)
    coefficients(5) = shortSignedAtOffset(c, 10)
    coefficients(6) = shortSignedAtOffset(c, 12)
    coefficients(7) = shortSignedAtOffset(c, 14)

    val s = coefficients.mkString(",")
    Log.d(SensorTagData.TAG, s"coefficients >${s}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractCalibrationCoefficients()")
    coefficients
  } ensuring(_.forall(_ != 0))

  def extractBarTemperature(c: BluetoothGattCharacteristic, coef: Array[Int]): Double = {
    require(c != null, "c cannot be null")
    require(coef != null, "coef cannot be null")
    require(coef.size == 8, "coef needs exactly 8 elemements")
    require(coef.forall(_ != 0), "coef needs to be not 0")
    Log.v(SensorTagData.TAG, "Enter - extractBarTemperature()")

    val t = ((100 * (coef(0) * shortSignedAtOffset(c, 0) / Math.pow(2,8) + coef(1) * Math.pow(2,6))) / Math.pow(2,16)) / 100

    Log.d(SensorTagData.TAG, s"BarTemperature >${t}< ...")
    Log.v(SensorTagData.TAG, "Leave - extractBarTemperature()")
    t
  }

  def extractBarometer(c: BluetoothGattCharacteristic, coef: Array[Int]): Double = {
    val PASCAL_TO_INHG = 0.000296

    require(c != null, "c cannot be null")
    require(coef != null, "coef cannot be null")
    require(coef.size == 8, "coef needs exactly 8 elemements")
    require(coef.forall(_ != 0), "coef needs to be not 0")
    Log.d(SensorTagData.TAG, "Enter - extractBarometer()")

    val t_r = shortSignedAtOffset(c, 0)
    val p_r = shortUnsignedAtOffset(c, 2)

    val S = coef(2) + coef(3) * t_r / Math.pow(2,17) + ((coef(4) * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,19)
    val O = coef(5) * Math.pow(2,14) + coef(6) * t_r / Math.pow(2,3) + ((coef(7) * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,4)
    val p_hg = ((S * p_r + O) / Math.pow(2,14)) * PASCAL_TO_INHG

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
    */
  private def shortSignedAtOffset(c: BluetoothGattCharacteristic, offset: Int): Int =  {
    val lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset)
    val upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1) // Note: interpret MSB as signed.

    val s = (upperByte << 8) + lowerByte
    Log.v(SensorTagData.TAG, s"shortSignedAtOffset >${s}< ...")
    s
  }

  private def shortUnsignedAtOffset(c: BluetoothGattCharacteristic, offset: Int): Int = {
    val lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset)
    val upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1) // Note: interpret MSB as unsigned.

    val s = (upperByte << 8) + lowerByte
    Log.v(SensorTagData.TAG, s"shortUnsignedAtOffset >${s}< ...")
    s
  }
}
