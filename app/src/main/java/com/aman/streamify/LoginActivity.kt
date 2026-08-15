package com.aman.streamify

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Patterns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import java.security.MessageDigest

class LoginActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences(
            "streamify_native_auth_v10",
            Context.MODE_PRIVATE
        )
    }

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var action: Button
    private lateinit var switch: TextView

    private var create = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getBoolean("logged", false)) {
            openApp()
            return
        }

        window.statusBarColor =
            Color.parseColor("#050506")

        window.navigationBarColor =
            Color.parseColor("#050506")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(45), dp(28), dp(35))
            setBackgroundColor(Color.parseColor("#050506"))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(100),
                dp(100)
            )
        )

        root.addView(
            TextView(this).apply {
                text = "STREAMIFY"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
        )

        title = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, dp(45), 0, dp(6))
        }

        subtitle = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#92909A"))
            setPadding(0, 0, 0, dp(22))
        }

        email = input("Email address")
        password = input("Password").apply {
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        action = Button(this).apply {
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)

            background =
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#E51BEA"),
                        Color.parseColor("#7438FF")
                    )
                ).apply {
                    cornerRadius = dp(30).toFloat()
                }
        }

        switch = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.parseColor("#E53FFF"))
            setPadding(0, dp(20), 0, dp(20))
        }

        root.addView(title)
        root.addView(subtitle)

        root.addView(
            email,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        root.addView(
            password,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(12)
            }
        )

        root.addView(
            action,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(22)
            }
        )

        root.addView(switch)

        setContentView(root)

        render()

        switch.setOnClickListener {
            create = !create
            render()
        }

        action.setOnClickListener {
            submit()
        }
    }

    private fun input(hintText: String) =
        EditText(this).apply {

            hint = hintText
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#77747E"))
            setPadding(dp(18), 0, dp(18), 0)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15141B"))
                setStroke(
                    dp(1),
                    Color.parseColor("#30283A")
                )
                cornerRadius = dp(16).toFloat()
            }
        }

    private fun render() {
        if (create) {
            title.text = "Create account"
            subtitle.text =
                "Create your Streamify account"
            action.text = "Create Account"
            switch.text =
                "Already have an account? Login"
        } else {
            title.text = "Welcome back"
            subtitle.text =
                "Login to continue listening"
            action.text = "Login"
            switch.text =
                "New to Streamify? Create Account"
        }
    }

    private fun submit() {

        val mail =
            email.text.toString()
                .trim()
                .lowercase()

        val pass =
            password.text.toString()

        if (!Patterns.EMAIL_ADDRESS
                .matcher(mail)
                .matches()
        ) {
            email.error = "Enter valid email"
            return
        }

        if (pass.length < 6) {
            password.error =
                "Minimum 6 characters"
            return
        }

        val hashed = hash("$mail:$pass")

        if (create) {

            prefs.edit()
                .putString("email", mail)
                .putString("pass", hashed)
                .putBoolean("logged", true)
                .apply()

            openApp()

        } else {

            if (
                prefs.getString("email", "") == mail &&
                prefs.getString("pass", "") == hashed
            ) {

                prefs.edit()
                    .putBoolean("logged", true)
                    .apply()

                openApp()

            } else {

                Toast.makeText(
                    this,
                    "Wrong login. Create account first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun hash(s: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }

    private fun openApp() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )
        finish()
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()
}
