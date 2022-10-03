package org.schabi.newpipe.about

import android.content.Context
import android.util.Base64
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object LicenseFragmentHelper {
    /**
     * @param context the context to use
     * @param license the license
     * @return String which contains a HTML formatted license page
     * styled according to the context's theme
     */
    private fun getFormattedLicense(context: Context, license: License): String {
        val licenseContent = StringBuilder()
        val webViewData: String
        try {
            BufferedReader(
                InputStreamReader(
                    context.assets.open(license.filename),
                    StandardCharsets.UTF_8
                )
            ).use { `in` ->
                var str: String?
                while (`in`.readLine().also { str = it } != null) {
                    licenseContent.append(str)
                }

                // split the HTML file and insert the stylesheet into the HEAD of the file
                webViewData = "$licenseContent".replace(
                    "</head>",
                    "<style>" + getLicenseStylesheet(context) + "</style></head>"
                )
            }
        } catch (e: IOException) {
            throw IllegalArgumentException(
                "Could not get license file: " + license.filename, e
            )
        }
        return webViewData
    }

    /**
     * @param context the Android context
     * @return String which is a CSS stylesheet according to the context's theme
     */
    private fun getLicenseStylesheet(context: Context): String {
        val isLightTheme = ThemeHelper.isLightThemeSelected(context)
        return (
            "body{padding:12px 15px;margin:0;" + "background:#" + getHexRGBColor(
                context,
                if (isLightTheme) R.color.light_license_background_color
                else R.color.dark_license_background_color
            ) + ";" + "color:#" + getHexRGBColor(
                context,
                if (isLightTheme) R.color.light_license_text_color
                else R.color.dark_license_text_color
            ) + "}" + "a[href]{color:#" + getHexRGBColor(
                context,
                if (isLightTheme) R.color.light_youtube_primary_color
                else R.color.dark_youtube_primary_color
            ) + "}" + "pre{white-space:pre-wrap}"
            )
    }

    /**
     * Cast R.color to a hexadecimal color value.
     *
     * @param context the context to use
     * @param color   the color number from R.color
     * @return a six characters long String with hexadecimal RGB values
     */
    private fun getHexRGBColor(context: Context, color: Int): String {
        return context.getString(color).substring(3)
    }

    fun showLicense(context: Context?, license: License): Disposable {
        return showLicense(context, license) { alertDialog ->
            alertDialog.setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
        }
    }

    fun showLicense(context: Context?, component: SoftwareComponent): Disposable {
        return showLicense(context, component.license) { alertDialog ->
            alertDialog.setPositiveButton(R.string.dismiss) { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.setNeutralButton(R.string.open_website_license) { _, _ ->
                ShareUtils.openUrlInBrowser(context!!, component.link)
            }
        }
    }

    private fun showLicense(
        context: Context?,
        license: License,
        block: (AlertDialog.Builder) -> Unit
    ): Disposable {
        return if (context == null) {
            Disposable.empty()
        } else {
            Observable.fromCallable { getFormattedLicense(context, license) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { formattedLicense ->
                    val webViewData = Base64.encodeToString(
                        formattedLicense.toByteArray(StandardCharsets.UTF_8), Base64.NO_PADDING
                    )
                    val webView = WebView(context)
                    webView.loadData(webViewData, "text/html; charset=UTF-8", "base64")

                    AlertDialog.Builder(context).apply {
                        setTitle(license.name)
                        setView(webView)
                        Localization.assureCorrectAppLanguage(context)
                        block(this)
                        show()
                    }
                }
        }
    }
}
