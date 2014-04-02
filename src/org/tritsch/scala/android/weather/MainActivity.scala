/*
__________       .__                     .___
\______   \ ____ |  | _____    ____    __| _/
 |       _//  _ \|  | \__  \  /    \  / __ |
 |    |   (  <_> )  |__/ __ \|   |  \/ /_/ |
 |____|_  /\____/|____(____  /___|  /\____ |
        \/                 \/     \/      \/
Copyright (c), 2014, roland@tritsch.org
http://www.tritsch.org
*/

package org.tritsch.scala.android.weather

import android.app.{Activity, ProgressDialog}
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.{Intent, Context}
import android.content.pm.PackageManager
import android.os.{Bundle, Handler, Message}
import android.util.{Log, SparseArray}
import android.view.{Menu, MenuItem, Window}
import android.widget.{TextView, Toast}

import java.util.UUID

// @todo - add documentation
// @todo - split class into two. One activity. One callback
// @todo - add test cases

private object MSG {
  private var i = 100

  val HUMIDITY = { i = i + 1; i }
  val PRESSURE = { i = i + 1; i }
  val PRESSURE_CAL = { i = i + 1; i }
  val PROGRESS = { i = i + 1; i }
  val DISMISS = { i = i + 1; i }
  val CLEAR = { i = i + 1; i }
}

private object MainActivity {
  val TAG = MainActivity.getClass.getName
}

class MainActivity extends Activity with BluetoothAdapter.LeScanCallback {
  private var mBluetoothAdapter: BluetoothAdapter = null
  private val mDevices = new SparseArray[BluetoothDevice]
  private var mConnectedGatt: BluetoothGatt = null
  private var mTemperature, mHumidity, mPressure: TextView = null
  private var mProgress: ProgressDialog = null

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)
    Log.d(MainActivity.TAG, "Enter - onCreate")

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(R.layout.activity_main)
    setProgressBarIndeterminate(true)

    mTemperature = findViewById(R.id.text_temperature).asInstanceOf[TextView]
    mHumidity = findViewById(R.id.text_humidity).asInstanceOf[TextView]
    mPressure = findViewById(R.id.text_pressure).asInstanceOf[TextView]

    val manager = getSystemService(Context.BLUETOOTH_SERVICE).asInstanceOf[BluetoothManager]
    mBluetoothAdapter = manager.getAdapter

    mProgress = new ProgressDialog(this)
    mProgress.setIndeterminate(true)
    mProgress.setCancelable(false)
    Log.d(MainActivity.TAG, "Leave - onCreate")
  }

  override def onResume: Unit = {
    super.onResume
    Log.d(MainActivity.TAG, "Enter - onResume")
    if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
      Log.i(MainActivity.TAG, "Bluetooth not enabled ...")
      startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
      finish
    } else {
      if(!getPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        Log.i(MainActivity.TAG, "Bluetooth not available ...")
        Toast.makeText(this, "No BLE Support!!!", Toast.LENGTH_SHORT).show
        finish
      } else {
        clearDisplayValues
      }
    }
    Log.d(MainActivity.TAG, "Leave - onResume")
  }

  override def onPause: Unit = {
    super.onPause
    Log.d(MainActivity.TAG, "Enter - onPause")
    mProgress.dismiss

    Log.i(MainActivity.TAG, "Cancel any scans in progress ...")
    mHandler.removeCallbacks(mStopRunnable)
    mBluetoothAdapter.stopLeScan(this)
    Log.d(MainActivity.TAG, "Leave - onPause")
  }

  override def onStop: Unit = {
    super.onStop
    Log.d(MainActivity.TAG, "Enter - onStop")

    Log.i(MainActivity.TAG, "Disconnect from any active tag connection ...")
    if(mConnectedGatt != null) {
      mConnectedGatt.disconnect
      mConnectedGatt = null
    }
    Log.d(MainActivity.TAG, "Leave - onStop")
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    Log.d(MainActivity.TAG, "Enter - onCreateOptionsMenu")

    Log.i(MainActivity.TAG, "Add the *scan* option to the menu ...")
    getMenuInflater.inflate(R.menu.main, menu)

    Log.i(MainActivity.TAG, s"Add ${mDevices.size} devices to the overflow menu ...")
    for (i <- 0 until mDevices.size) {
      menu.add(0, mDevices.keyAt(i), 0, f"${mDevices.valueAt(i).getAddress}%10s ...")
    }
    Log.d(MainActivity.TAG, "Leave - onCreateOptionsMenu")
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    Log.d(MainActivity.TAG, "Enter - onOptionsItemSelected")

    val didIt = item.getItemId match {
      case R.id.action_scan => {
        Log.i(MainActivity.TAG, "Start scan ...")
        mDevices.clear
        startScan
        true
      }
      case _ => {
        val device = mDevices.get(item.getItemId)

        Log.i(MainActivity.TAG, s"Connecting to ${device.getName} ...")
        mConnectedGatt = device.connectGatt(this, false, mGattCallback)

        mHandler.sendMessage(Message.obtain(null, MSG.PROGRESS, s"Connecting to ${device.getName} ..."))
        super.onOptionsItemSelected(item)
      }
    }
    Log.d(MainActivity.TAG, "Leave - onOptionsItemSelected")
    didIt
  }

  private def clearDisplayValues: Unit = {
    Log.d(MainActivity.TAG, "Enter - clearDisplayValues")
    mTemperature.setText("---")
    mHumidity.setText("---")
    mPressure.setText("---")
    Log.d(MainActivity.TAG, "Leave - clearDisplayValues")
  }

  private val mStopRunnable: Runnable = new Runnable {
    override def run: Unit = {
      stopScan
    }
  }

  private def startScan: Unit = {
    Log.d(MainActivity.TAG, "Enter - startScan")
/* @todo - go back to this implementation ...
 * ... as soon as this https://code.google.com/p/android/issues/detail?id=59490 is fixed.
 *
    mBluetoothAdapter.startLeScan(
      Array[UUID](
        SensorTag.HUMIDITY_SERVICE,
        SensorTag.PRESSURE_SERVICE
      ),
      this
    )
*/
    mBluetoothAdapter.startLeScan(this)
    setProgressBarIndeterminateVisibility(true)
    // @todo - put this conf var into a conf file
    mHandler.postDelayed(mStopRunnable, 2500)
    Log.d(MainActivity.TAG, "Leave - startScan")
  }

  private def stopScan: Unit =  {
    Log.d(MainActivity.TAG, "Enter - stopScan")
    mBluetoothAdapter.stopLeScan(this)
    setProgressBarIndeterminateVisibility(false)
    Log.d(MainActivity.TAG, "Leave - stopScan")
  }

  // @todo - Move the implementation of BluetoothAdapter.LeScanCallback to a seperate file
  override def onLeScan(device: BluetoothDevice, rssi: Int, sr: Array[Byte]): Unit = {
    require(device.getName.equals("SensorTag"), "Only SensorTag devices should be found")
    Log.d(MainActivity.TAG, "Enter - onLeScan")
    Log.i(MainActivity.TAG, s"Found new LE device >${device.getName}/${device.getAddress}< @ >${rssi}< ...")
    Log.v(MainActivity.TAG, s"With ScanRecord >${SensorTag.dump(sr)}< ...")
    // @todo - remove the if as soon as this https://code.google.com/p/android/issues/detail?id=59490 is fixed
    if(device.getName.equals("SensorTag")) {
      mDevices.put(device.hashCode, device)
      invalidateOptionsMenu
    }
    Log.d(MainActivity.TAG, "Leave - onLeScan")
  }

  /*
   * In this callback, we've created a bit of a state machine to enforce that only
   * one characteristic be read or written at a time until all of our sensors
   * are enabled and we are registered to get notifications.
   */
  private val  mGattCallback: BluetoothGattCallback = new BluetoothGattCallback {
    private var mState = 0

    private def reset: Unit = { mState = 0 }

    private def advance: Unit = { mState = mState + 1 }

    /*
     * Send an enable command to each sensor by writing a configuration
     * characteristic.  This is specific to the SensorTag to keep power
     * low by disabling sensors you aren't using.
     */
    private def enableNextSensor(gatt: BluetoothGatt): Unit = {
      Log.d(MainActivity.TAG, "Enter - enableNextSensor")
      var characteristic: BluetoothGattCharacteristic = null
      mState match {
        case 0 => {
          Log.i(MainActivity.TAG, "Enabling pressure calibration ...")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x02))
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Enabling pressure ...")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x01))
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Enabling humidity ...")
          characteristic = gatt.getService(SensorTag.HUMIDITY_SERVICE).getCharacteristic(SensorTag.HUMIDITY_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x01))
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensors enabled ...")
          mHandler.sendEmptyMessage(MSG.DISMISS)
        }
      }

      if(characteristic != null) gatt.writeCharacteristic(characteristic)
      Log.d(MainActivity.TAG, "Leave - enableNextSensor")
    }

    /*
     * Read the data characteristic's value for each sensor explicitly
     */
    private def readNextSensor(gatt: BluetoothGatt): Unit = {
      Log.d(MainActivity.TAG, "Enter - readNextSensor")
      var characteristic: BluetoothGattCharacteristic = null
      mState match {
        case 0 => {
          Log.i(MainActivity.TAG, "Reading pressure calibration ....")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_CAL_CHAR)
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Reading pressure ...")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_DATA_CHAR)
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Reading humidity ...")
          characteristic = gatt.getService(SensorTag.HUMIDITY_SERVICE).getCharacteristic(SensorTag.HUMIDITY_DATA_CHAR)
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensors read ...")
          mHandler.sendEmptyMessage(MSG.DISMISS)
        }
      }

      if(characteristic != null) gatt.readCharacteristic(characteristic)
      Log.d(MainActivity.TAG, "Leave - readNextSensor")
    }

    /*
     * Enable notification of changes on the data characteristic for each sensor
     * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
     * configuration descriptor.
     */
    private def setNotifyNextSensor(gatt: BluetoothGatt): Unit = {
      Log.d(MainActivity.TAG, "Enter - setNotifyNextSensor")
      var characteristic: BluetoothGattCharacteristic = null
      mState match {
        case 0 => {
          Log.i(MainActivity.TAG, "Set notify pressure calibration ...")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_CAL_CHAR)
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Set notify pressure ...")
          characteristic = gatt.getService(SensorTag.PRESSURE_SERVICE).getCharacteristic(SensorTag.PRESSURE_DATA_CHAR)
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Set notify humidity ...")
          characteristic = gatt.getService(SensorTag.HUMIDITY_SERVICE).getCharacteristic(SensorTag.HUMIDITY_DATA_CHAR)
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensor notifications set ...")
          mHandler.sendEmptyMessage(MSG.DISMISS)
        }
      }

      if(characteristic != null) {
        Log.i(MainActivity.TAG, "Enable local notifications ...")
        gatt.setCharacteristicNotification(characteristic, true)

        Log.i(MainActivity.TAG, "Enabled remote notifications ...")
        val desc = characteristic.getDescriptor(SensorTag.CONFIG_DESCRIPTOR)
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        gatt.writeDescriptor(desc)
      }
      Log.d(MainActivity.TAG, "Leave - setNotifyNextSensor")
    }

    override def onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - OnConnectionStateChange")
      Log.i(MainActivity.TAG, s"Connection state change from >${connectionState(status)}< to >${connectionState(newState)}<")
      if(status == newState) {
        Log.w(MainActivity.TAG, "Error: Non-state change event dedected ... ignoring the event ...")
      } else if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
        Log.i(MainActivity.TAG, "Discovering services ...")
        gatt.discoverServices
        mHandler.sendMessage(Message.obtain(null, MSG.PROGRESS, "Discovering Services ..."))
      } else if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
        Log.i(MainActivity.TAG, "Just disconnected ...")
        mHandler.sendEmptyMessage(MSG.CLEAR)
      } else if(status != BluetoothGatt.GATT_SUCCESS) {
        Log.e(MainActivity.TAG, "Error: Bad state detected ... disconnecting ...")
        gatt.disconnect
      } else {
        Log.wtf(MainActivity.TAG, "Error: *Really* bad state detected ... disconnecting ...")
        gatt.disconnect
      }
      Log.d(MainActivity.TAG, "Leave - OnConnectionStateChange")
    }

    override def onServicesDiscovered(gatt: BluetoothGatt, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onServicesDiscovered")
      Log.i(MainActivity.TAG, s"Services discovered >${status}< ...")
      mHandler.sendMessage(Message.obtain(null, MSG.PROGRESS, "Enabling Sensors ..."))
      /*
       * With services discovered, we are going to reset our state machine and start
       * working through the sensors we need to enable
       */
      reset
      enableNextSensor(gatt)
      Log.d(MainActivity.TAG, "Leave - onServicesDiscovered")
    }

    override def onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onCharacteristicRead")
      Log.i(MainActivity.TAG, s"Reading characteristic >${characteristic.getUuid}< ...")
      // For each read, pass the data up to the UI thread to update the display ...
      characteristic.getUuid match {
        case SensorTag.HUMIDITY_DATA_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.HUMIDITY, characteristic))
        case SensorTag.PRESSURE_DATA_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.PRESSURE, characteristic))
        case SensorTag.PRESSURE_CAL_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.PRESSURE_CAL, characteristic))
        case _ =>
          Log.e(MainActivity.TAG, s"Error: Unknown characteristic detected >${characteristic.getUuid}< ...")
      }

      // After reading the initial value, next we enable notifications ...
      setNotifyNextSensor(gatt)
      Log.d(MainActivity.TAG, "Leave - onCharacteristicRead")
    }

    override def onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onCharacteristicRead")
      // After writing the enable flag, next we read the initial value ...
      readNextSensor(gatt)
      Log.d(MainActivity.TAG, "Enter - onCharacteristicRead")
    }

    override def onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Unit = {
      Log.d(MainActivity.TAG, "Enter - onCharacteristicChanged")
      /*
       * After notifications are enabled, all updates from the device on characteristic
       * value changes will be posted here. Similar to read, we hand these up to the
       * UI thread to update the display.
       */
      characteristic.getUuid match {
        case SensorTag.HUMIDITY_DATA_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.HUMIDITY, characteristic))
        case SensorTag.PRESSURE_DATA_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.PRESSURE, characteristic))
        case SensorTag.PRESSURE_CAL_CHAR =>
          mHandler.sendMessage(Message.obtain(null, MSG.PRESSURE_CAL, characteristic))
        case _ =>
          Log.e(MainActivity.TAG, s"Error: Unknown characteristic detected >${characteristic.getUuid}< ...")
      }
      Log.d(MainActivity.TAG, "Leave - onCharacteristicChanged")
    }

    override def onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onDescriptorWrite")
      // Once notifications are enabled, we move to the next sensor and start over with enable ...
      advance
      enableNextSensor(gatt)
      Log.d(MainActivity.TAG, "Leave - onDescriptorWrite")
    }

    override def onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onReadRemoteRssi")
      Log.i(MainActivity.TAG, s"Remote RSSI >${rssi}< ...")
      Log.d(MainActivity.TAG, "Leave - onReadRemoteRssi")
    }

    private def connectionState(status: Int): String = {
      Log.v(MainActivity.TAG, "Enter - connectionState")
      val s = status match {
        case BluetoothProfile.STATE_CONNECTED => "Connected"
        case BluetoothProfile.STATE_DISCONNECTED => "Disconnected"
        case BluetoothProfile.STATE_CONNECTING => "Connecting"
        case BluetoothProfile.STATE_DISCONNECTING => "Disconnecting"
        case _ => String.valueOf(status)
      }
      Log.v(MainActivity.TAG, "Leave - connectionState")
      s
    }
  }

  /*
   * We have a Handler to process event results on the main thread
   */
  private val mHandler: Handler = new Handler {
    override def handleMessage(msg: Message): Unit = {
      Log.d(MainActivity.TAG, "Enter - handleMessage")
      msg.what match {
        case MSG.HUMIDITY => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.e(MainActivity.TAG, "Error obtaining humidity value!!!")
          } else {
            updateHumidityValues(characteristic)
          }
        }
        case MSG.PRESSURE => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.e(MainActivity.TAG, "Error obtaining pressure value!!!")
          } else {
            updatePressureValue(characteristic)
          }
        }
        case MSG.PRESSURE_CAL => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.e(MainActivity.TAG, "Error obtaining cal value!!!")
          } else {
            updatePressureCals(characteristic)
          }
        }
        case MSG.PROGRESS => {
          mProgress.setMessage(msg.obj.asInstanceOf[String])
          if(!mProgress.isShowing) {
            mProgress.show
          }
        }
        case MSG.DISMISS => mProgress.hide
        case MSG.CLEAR => clearDisplayValues
      }
      Log.d(MainActivity.TAG, "Leave - handleMessage")
    }
  }

  // Methods to extract sensor data and update the UI
  private def updateHumidityValues(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updateHumidityValues")
    val humidity = SensorTag.extractHumidity(characteristic)
    mHumidity.setText(f"${humidity}%.0f")
    Log.d(MainActivity.TAG, "Leave - updateHumidityValues")
  }

  private var mPressureCals: Array[Int] = null
  private def updatePressureCals(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updatePressureCals")
    mPressureCals = SensorTag.extractCalibrationCoefficients(characteristic)
    Log.d(MainActivity.TAG, "Leave - updatePressureCals")
  }

  private def updatePressureValue(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updatePressureValue")
    if(mPressureCals != null) {
      val pressure = SensorTag.extractBarometer(characteristic, mPressureCals)
      val temperature = SensorTag.extractBarTemperature(characteristic, mPressureCals)

      mTemperature.setText(f"${temperature}%.1f")
      mPressure.setText(f"${pressure}%.2f")
    }
    Log.d(MainActivity.TAG, "Leave - updatePressureValue")
  }
}
