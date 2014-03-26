package org.tritsch.scala.android.weather

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.{Menu, MenuItem, View}
import android.widget.TextView
import android.widget.{EditText, Toast}

object FirstActivity {
  val TAG = classOf[FirstActivity].getName
  val EXTRA_MESSAGE = TAG + ".MESSAGE"
}

class FirstActivity extends Activity {
  // overrides ...
  override def onCreate(savedInstanceState: Bundle): Unit = {
    Log.d(FirstActivity.TAG, "Enter - onCreate()")
    super.onCreate(savedInstanceState)
    setContentView(R.layout.first)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    Log.d(FirstActivity.TAG, "Enter - onCreateOptionsMenu()")
    getMenuInflater.inflate(R.menu.main, menu);
    super.onCreateOptionsMenu(menu);
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    Log.d(FirstActivity.TAG, "Enter - onOptionsItemSelected()")
    item.getItemId match {
      case R.id.action_search => openSearch
      case R.id.action_settings => openSettings
      case R.id.action_help => openHelp
      case R.id.action_about => openAbout
      case R.id.action_logout => openLogout
      case _ => super.onOptionsItemSelected(item)
    }
  }

  // public ...
  def sendMessage(v: View): Unit = {
    Log.d(FirstActivity.TAG, "Enter - sendMessage()")
    val intent = new Intent(this, classOf[DisplayMessageActivity])
    val editText = findViewById(R.id.edit_message).asInstanceOf[EditText]
    val message = editText.getText.toString
    intent.putExtra(FirstActivity.EXTRA_MESSAGE, message)
    startActivity(intent)
  }

  // private ...
  private def openSearch: Boolean = {
    Log.d(FirstActivity.TAG, "Enter - openSearch()")
    NIY
    true
  }

  private def openSettings: Boolean = {
    Log.d(FirstActivity.TAG, "Enter - openSettings()")
    NIY
    true
  }

  private def openHelp: Boolean = {
    Log.d(FirstActivity.TAG, "Enter - openHelp()")
    NIY
    true
  }

  private def openAbout: Boolean = {
    Log.d(FirstActivity.TAG, "Enter - openAbout()")
    aboutDialog.show
    true
  }

  private def openLogout: Boolean = {
    Log.d(FirstActivity.TAG, "Enter - openLogout()")
    NIY
    true
  }

  private def NIY: Unit = {
    Toast
      .makeText(getApplicationContext, "Not implemented yet!", Toast.LENGTH_SHORT)
      .show
  }

  private def aboutDialog: AlertDialog = {
    val title = new TextView(this)
    title.setText(R.string.about_dialog_title)
    title.setGravity(Gravity.CENTER)

    val message = new TextView(this)
    message.setText(R.string.about_dialog_message)
    message.setGravity(Gravity.CENTER)

    val dialog = new AlertDialog.Builder(this)
      .setCustomTitle(title)
      .setView(message)
      .setPositiveButton(R.string.ok, null)
      .create

    dialog
  }
}
