package org.albaspazio.k4b.activity

import android.app.Activity
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import org.albaspazio.k4b.R
import org.albaspazio.core.accessory.*


/**
 *
 * Simple activity that shows the info page (help page).
 * The content and style of this activity are defined entirely
 * in the resource XML files. The content is interpreted here
 * as an HTML string, i.e. one can use simple HTML-formatting
 * and linking.
 *
 * @author Kaarel Kaljurand
 */
class AboutActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        val ab = actionBar
        if (ab != null) {
            ab.setTitle(R.string.app_name)
            ab.subtitle = "v" + getVersionName(this)
        }
        val tvAbout = findViewById<TextView>(R.id.tvAbout)
        tvAbout.movementMethod = LinkMovementMethod.getInstance()
        val about = String.format(
            getString(R.string.tvAbout),
            getString(R.string.app_name)
        )
        tvAbout.text = Html.fromHtml(about)
    }
}