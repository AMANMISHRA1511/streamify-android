package com.aman.streamify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    companion object {
        const val STREAMIFY_URL =
            "https://streamify-india-5dyhat.v2.appdeploy.ai/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = getSharedPreferences(
            "streamify_auth",
            Context.MODE_PRIVATE
        )

        if (!auth.getBoolean("logged_in", false)) {
            startActivity(
                Intent(this, LoginActivity::class.java)
            )
            finish()
            return
        }

        webView = WebView(this)
        setContentView(webView)

        WebView.setWebContentsDebuggingEnabled(false)

        webView.setBackgroundColor(
            android.graphics.Color.BLACK
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode =
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString =
                "$userAgentString StreamifyAndroid/9.0"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    val url =
                        request?.url?.toString()
                            ?: return false

                    return !url.startsWith(
                        "https://streamify-india-5dyhat.v2.appdeploy.ai"
                    )
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    super.onPageFinished(view, url)

                    view?.evaluateJavascript(
                        """
                        (() => {
                          document.documentElement.style.background='#050506';
                          document.body.style.background='#050506';

                          const meta =
                            document.querySelector(
                              'meta[name="viewport"]'
                            );

                          if (meta) {
                            meta.setAttribute(
                              'content',
                              'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover'
                            );
                          }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

        webView.loadUrl(STREAMIFY_URL)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized &&
            webView.canGoBack()
        ) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager
            .getInstance()
            .flush()
    }

    override fun onDestroy() {

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient =
                WebViewClient()
            webView.destroy()
        }

        super.onDestroy()
    }
}
