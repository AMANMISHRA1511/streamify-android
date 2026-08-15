package com.aman.streamify

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
            "streamify_auth",
            Context.MODE_PRIVATE
        )
    }

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var heading: TextView
    private lateinit var subtitle: TextView
    private lateinit var action: Button
    private lateinit var toggle: TextView

    private var creating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getBoolean("logged_in", false)) {
            openStreamify()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(42, 60, 42, 60)
            setBackgroundColor(
                Color.parseColor("#050506")
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(
                resources.getIdentifier(
                    "streamify_logo",
                    "drawable",
                    packageName
                )
            )
            scaleType =
                ImageView.ScaleType.CENTER_INSIDE
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(
                220,
                220
            )
        )

        root.addView(
            TextView(this).apply {
                text = "STREAMIFY"
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
        )

        heading = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, 70, 0, 8)
        }

        subtitle = TextView(this).apply {
            textSize = 15f
            setTextColor(
                Color.parseColor("#9999A6")
            )
            setPadding(0, 0, 0, 25)
        }

        email = EditText(this).apply {
            hint = "Email address"
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

            setTextColor(Color.WHITE)
            setHintTextColor(
                Color.parseColor("#777780")
            )
            setPadding(30, 0, 30, 0)
            setBackgroundColor(
                Color.parseColor("#15151C")
            )
        }

        password = EditText(this).apply {
            hint = "Password"

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

            setTextColor(Color.WHITE)
            setHintTextColor(
                Color.parseColor("#777780")
            )

            setPadding(30, 0, 30, 0)

            setBackgroundColor(
                Color.parseColor("#15151C")
            )
        }

        action = Button(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(
                Color.parseColor("#D91AFF")
            )
            isAllCaps = false
            textSize = 17f
        }

        toggle = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(
                Color.parseColor("#E83CFF")
            )
            textSize = 15f
            setPadding(0, 25, 0, 20)
        }

        root.addView(heading)
        root.addView(subtitle)

        root.addView(
            email,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
            )
        )

        root.addView(
            password,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                topMargin = 20
            }
        )

        root.addView(
            action,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                topMargin = 30
            }
        )

        root.addView(toggle)

        setContentView(root)

        renderMode()

        toggle.setOnClickListener {
            creating = !creating
            renderMode()
        }

        action.setOnClickListener {
            submit()
        }
    }

    private fun renderMode() {

        if (creating) {
            heading.text = "Create account"
            subtitle.text =
                "Create your Streamify account"
            action.text = "Create Account"
            toggle.text =
                "Already have an account? Login"
        } else {
            heading.text = "Welcome back"
            subtitle.text =
                "Login to continue listening"
            action.text = "Login"
            toggle.text =
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
            email.error =
                "Enter a valid email"
            return
        }

        if (pass.length < 6) {
            password.error =
                "Minimum 6 characters"
            return
        }

        val hashed =
            hash("$mail:$pass")

        if (creating) {

            prefs.edit()
                .putString("email", mail)
                .putString(
                    "password_hash",
                    hashed
                )
                .putBoolean(
                    "logged_in",
                    true
                )
                .apply()

            openStreamify()

        } else {

            val savedMail =
                prefs.getString(
                    "email",
                    null
                )

            val savedPassword =
                prefs.getString(
                    "password_hash",
                    null
                )

            if (
                savedMail == mail &&
                savedPassword == hashed
            ) {

                prefs.edit()
                    .putBoolean(
                        "logged_in",
                        true
                    )
                    .apply()

                openStreamify()

            } else {

                Toast.makeText(
                    this,
                    "Incorrect email or password",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun hash(v: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(v.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }

    private fun openStreamify() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )
        finish()
    }
}
