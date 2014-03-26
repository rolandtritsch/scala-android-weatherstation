package org.tritsch.scala.android.weather

import android.app.Activity
import android.os.{Build, Bundle}
import android.support.v4.app.NavUtils
import android.util.Log
import android.view.MenuItem
import android.widget.TextView

object DisplayMessageActivity {
  val TAG = classOf[DisplayMessageActivity].getName
}

class DisplayMessageActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(DisplayMessageActivity.TAG, "Enter - onCreate()")

    // Make sure we're running on Honeycomb or higher to use ActionBar APIs
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      // Show the Up button in the action bar.
      getActionBar.setDisplayHomeAsUpEnabled(true)
    }

    // Extract the message from the intent that this activity is processing
    val message = getIntent.getStringExtra(FirstActivity.EXTRA_MESSAGE).reverse.toUpperCase
    Log.d(DisplayMessageActivity.TAG, s"Message - >${message}<")

    // Create the text view
    val textView = new TextView(this)
    textView.setTextSize(40)
    textView.setText(message)

    // Set the text view as the activity layout
    setContentView(textView)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    Log.d(DisplayMessageActivity.TAG, "Enter - onOptionsItemSelected()")
    item.getItemId match {
      case android.R.id.home => {
        NavUtils.navigateUpFromSameTask(this)
        true
      }
      case _ => super.onOptionsItemSelected(item)
    }
  }
}
