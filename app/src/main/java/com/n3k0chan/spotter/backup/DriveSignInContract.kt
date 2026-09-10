package com.n3k0chan.spotter.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.gms.common.AccountPicker

/**
 * ActivityResultContract para pedir al usuario que elija una cuenta de Google del dispositivo.
 * Devuelve el `accountName` (email) seleccionado, o null si canceló.
 *
 * Solo cuentas tipo "com.google" — no muestra otros tipos.
 */
class PickGoogleAccountContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val options = AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf("com.google"))
            .setAlwaysShowAccountPicker(false)
            .setTitleOverrideText("Elige la cuenta para los backups")
            .build()
        return AccountPicker.newChooseAccountIntent(options)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return intent.getStringExtra("authAccount")
    }
}
