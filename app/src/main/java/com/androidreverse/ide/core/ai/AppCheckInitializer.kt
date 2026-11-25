package com.androidreverse.ide.core.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidreverse.ide.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val INJECTED_MARKER_KEY = "ai_studio_debug_token_injected"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() ensures the injected debug secret is persisted before provider init.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .putBoolean(INJECTED_MARKER_KEY, true)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "App Check debug secret configured and DebugAppCheckProviderFactory installed.")
                return
            }

            // Re-launches inside the AI Studio emulator keep the previously injected debug token.
            if (debugPrefs.getBoolean(INJECTED_MARKER_KEY, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
            ) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Reusing previously injected App Check debug token.")
                return
            }

            // Physical device running a debug APK without an injected emulator debug token:
            // use the provisioned Android reCAPTCHA Enterprise site key so App Check attestation succeeds.
            if (installRecaptchaProvider(context)) {
                return
            }

            Log.w(TAG, "No debug token or reCAPTCHA site key found; falling back to DebugAppCheckProviderFactory.")
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            installRecaptchaProvider(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
