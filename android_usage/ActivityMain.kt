package com.vbb.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope

class ActivityMain : AppCompatActivity() {

    lateinit var googleSignInClient: GoogleSignInClient
    var account: GoogleSignInAccount? = null

    val SCOPE_DRIVE = Scope(Scopes.DRIVE_FULL)
    val REQUEST_CODE_SIGNIN = 20201

    var gDrive: GDrive? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            signIn()
        } else {
            driverFunction()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && REQUEST_CODE_SIGNIN == requestCode) {
            Log.i("MAIN", "*************** Logged in, Permission acquired. **************")
            account = GoogleSignIn.getLastSignedInAccount(this)
            driverFunction()
        }
        else {
            driverFunction()
        }
    }

    private fun signIn() {
        if (account == null) {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(SCOPE_DRIVE)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
            startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_SIGNIN)
        }
    }

    private fun driverFunction() {
        gDrive = GDrive.getGDrive(this)
        if (gDrive == null) {

            // TODO do the appropriate thing here
            return
        }

        // TODO start using GDrive
    }
}
