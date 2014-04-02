# Scala Android Weatherstation

Implement a weather station using the [TI Sensor Tag](http://www.ti.com/ww/en/wireless_connectivity/sensortag/).

## What is this about?

* this is based on the demo from the guys from [Double Encore](http://www.doubleencore.com/2013/12/bluetooth-smart-for-android)
* you obviously need to [get a Sensor Tag](http://www.ti.com/ww/en/wireless_connectivity/sensortag/) first :)
* you should then [install one of the Sensor Tag apps](https://play.google.com/store/apps/details?id=sample.ble.sensortag) to see the tag in action
* last but not least you need to clone, update and build the app (see below) and you should be in business

## Making it work

* clone the repo and build it as it is with `sbt compile`
* connect your device with an USB cable
    * the device must run android 4.4 (android-19)
	* check with `adb devices` that you can see the device and that it is the first/only one in the list
* install the app with `sbt install`
* on the device, go to your apps folder and start the `WeatherStation` app

## TODOs

* Look/grep for `@todo`
