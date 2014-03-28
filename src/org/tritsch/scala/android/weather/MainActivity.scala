// @todo - add copyright header
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
// @todo - put all hard coded config data into seperate file (e.g. the DEVICE_NAME and all the UUIDs)
// @todo - get rid of the vars. Make the code look like scala code
// @todo - refactor the code and start to build the beginnings of a common lib (e.g. a view to scan/list beacons)
// @todo - pick a good license
// @todo - add test cases
// @todo - find a better solution to do the logging (e.g. enter/leave)
private object MainActivity {
  val TAG = classOf[MainActivity].getName

  // Use this to *only* find the TI sensor tags
  val DEVICE_NAME = "SensorTag"

  // Humidity Service
  val HUMIDITY_SERVICE = UUID.fromString("f000aa20-0451-4000-b000-000000000000")
  val HUMIDITY_DATA_CHAR = UUID.fromString("f000aa21-0451-4000-b000-000000000000")
  val HUMIDITY_CONFIG_CHAR = UUID.fromString("f000aa22-0451-4000-b000-000000000000")

  // Barometric Pressure Service
  val PRESSURE_SERVICE = UUID.fromString("f000aa40-0451-4000-b000-000000000000")
  val PRESSURE_DATA_CHAR = UUID.fromString("f000aa41-0451-4000-b000-000000000000")
  val PRESSURE_CONFIG_CHAR = UUID.fromString("f000aa42-0451-4000-b000-000000000000")
  val PRESSURE_CAL_CHAR = UUID.fromString("f000aa43-0451-4000-b000-000000000000")

  // Client Configuration Descriptor
  val CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

  // Message ids for the handler
  val MSG_HUMIDITY = 101
  val MSG_PRESSURE = 102
  val MSG_PRESSURE_CAL = 103
  val MSG_PROGRESS = 201
  val MSG_DISMISS = 202
  val MSG_CLEAR = 301
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
    // mHandler.removeCallbacks(mStartRunnable)
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
      menu.add(0, mDevices.keyAt(i), 0, mDevices.valueAt(i).getName)
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

        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PROGRESS, s"Connecting to ${device.getName} ..."))
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
/*
  private val mStartRunnable: Runnable = new Runnable {
    override def run: Unit = {
      startScan
    }
  }
*/
  private def startScan: Unit = {
    Log.d(MainActivity.TAG, "Enter - startScan")
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
  override def onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: Array[Byte]): Unit = {
    Log.d(MainActivity.TAG, "Enter - onLeScan")
    Log.i(MainActivity.TAG, s"Found new LE device ${device.getName} @ ${rssi} ...")
    if(MainActivity.DEVICE_NAME.equals(device.getName)) {
      Log.d(MainActivity.TAG, "Found new SensorTag ...")
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
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x02))
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Enabling pressure ...")
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x01))
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Enabling humidity ...")
          characteristic = gatt.getService(MainActivity.HUMIDITY_SERVICE).getCharacteristic(MainActivity.HUMIDITY_CONFIG_CHAR)
          characteristic.setValue(Array.fill[Byte](1)(0x01))
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensors enabled ...")
          mHandler.sendEmptyMessage(MainActivity.MSG_DISMISS)
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
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_CAL_CHAR)
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Reading pressure ...")
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_DATA_CHAR)
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Reading humidity ...")
          characteristic = gatt.getService(MainActivity.HUMIDITY_SERVICE).getCharacteristic(MainActivity.HUMIDITY_DATA_CHAR)
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensors read ...")
          mHandler.sendEmptyMessage(MainActivity.MSG_DISMISS)
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
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_CAL_CHAR)
        }
        case 1 => {
          Log.i(MainActivity.TAG, "Set notify pressure ...")
          characteristic = gatt.getService(MainActivity.PRESSURE_SERVICE).getCharacteristic(MainActivity.PRESSURE_DATA_CHAR)
        }
        case 2 => {
          Log.i(MainActivity.TAG, "Set notify humidity ...")
          characteristic = gatt.getService(MainActivity.HUMIDITY_SERVICE).getCharacteristic(MainActivity.HUMIDITY_DATA_CHAR)
        }
        case _ => {
          Log.i(MainActivity.TAG, "All sensor notifications set ...")
          mHandler.sendEmptyMessage(MainActivity.MSG_DISMISS)
        }
      }

      if(characteristic != null) {
        Log.i(MainActivity.TAG, "Enable local notifications ...")
        gatt.setCharacteristicNotification(characteristic, true)

        Log.i(MainActivity.TAG, "Enabled remote notifications ...")
        val desc = characteristic.getDescriptor(MainActivity.CONFIG_DESCRIPTOR)
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
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PROGRESS, "Discovering Services ..."))
      } else if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
        Log.i(MainActivity.TAG, "Just disconnected ...")
        mHandler.sendEmptyMessage(MainActivity.MSG_CLEAR)
      } else if(status != BluetoothGatt.GATT_SUCCESS) {
        Log.w(MainActivity.TAG, "Error: Bad state detected ... disconnecting ...")
        gatt.disconnect
      } else {
        Log.w(MainActivity.TAG, "Error: *Really* bad state detected ... disconnecting ...")
        gatt.disconnect
      }
      Log.d(MainActivity.TAG, "Leave - OnConnectionStateChange")
    }

    override def onServicesDiscovered(gatt: BluetoothGatt, status: Int): Unit = {
      Log.d(MainActivity.TAG, "Enter - onServicesDiscovered")
      Log.i(MainActivity.TAG, s"Services discovered >${status}< ...")
      mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PROGRESS, "Enabling Sensors ..."))
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
      if(MainActivity.HUMIDITY_DATA_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_HUMIDITY, characteristic))
      } else if(MainActivity.PRESSURE_DATA_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PRESSURE, characteristic))
      } else if(MainActivity.PRESSURE_CAL_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PRESSURE_CAL, characteristic))
      } else {
        Log.w(MainActivity.TAG, s"Error: Unknown characteristic detected >${characteristic.getUuid}< ...")
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
       * value changes will be posted here.  Similar to read, we hand these up to the
       * UI thread to update the display.
       */
      if(MainActivity.HUMIDITY_DATA_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_HUMIDITY, characteristic))
      }
      if(MainActivity.PRESSURE_DATA_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PRESSURE, characteristic))
      }
      if(MainActivity.PRESSURE_CAL_CHAR.equals(characteristic.getUuid)) {
        mHandler.sendMessage(Message.obtain(null, MainActivity.MSG_PRESSURE_CAL, characteristic))
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
        case MainActivity.MSG_HUMIDITY => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.w(MainActivity.TAG, "Error obtaining humidity value!!!")
          } else {
            updateHumidityValues(characteristic)
          }
        }
        case MainActivity.MSG_PRESSURE => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.w(MainActivity.TAG, "Error obtaining pressure value!!!")
          } else {
            updatePressureValue(characteristic)
          }
        }
        case MainActivity.MSG_PRESSURE_CAL => {
          val characteristic = msg.obj.asInstanceOf[BluetoothGattCharacteristic]
          if(characteristic.getValue == null) {
            Log.w(MainActivity.TAG, "Error obtaining cal value!!!")
          } else {
            updatePressureCals(characteristic)
          }
        }
        case MainActivity.MSG_PROGRESS => {
          mProgress.setMessage(msg.obj.asInstanceOf[String])
          if(!mProgress.isShowing) {
            mProgress.show
          }
        }
        case MainActivity.MSG_DISMISS => mProgress.hide
        case MainActivity.MSG_CLEAR => clearDisplayValues
      }
      Log.d(MainActivity.TAG, "Leave - handleMessage")
    }
  }

  // Methods to extract sensor data and update the UI
  private def updateHumidityValues(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updateHumidityValues")
    val humidity = SensorTagData.extractHumidity(characteristic)
    mHumidity.setText(f"${humidity}%.0f")
    Log.d(MainActivity.TAG, "Leave - updateHumidityValues")
  }

  private var mPressureCals: Array[Int] = null
  private def updatePressureCals(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updatePressureCals")
    mPressureCals = SensorTagData.extractCalibrationCoefficients(characteristic)
    Log.d(MainActivity.TAG, "Leave - updatePressureCals")
  }

  private def updatePressureValue(characteristic: BluetoothGattCharacteristic): Unit = {
    Log.d(MainActivity.TAG, "Enter - updatePressureValue")
    if(mPressureCals != null) {
      val pressure = SensorTagData.extractBarometer(characteristic, mPressureCals)
      val temperature = SensorTagData.extractBarTemperature(characteristic, mPressureCals)

      mTemperature.setText(f"${temperature}%.1f")
      mPressure.setText(f"${pressure}%.2f")
    }
    Log.d(MainActivity.TAG, "Leave - updatePressureValue")
  }
}
