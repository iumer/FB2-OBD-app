package com.fb2.obd.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Sign-In + OAuth token for Drive uploads.
 * Never logs access tokens.
 */
object GoogleDriveAuth {

    /** Full Drive scope so the user can pick an existing folder they own. */
    const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"

    fun signInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE))
            .build()
        return GoogleSignIn.getClient(context.applicationContext, gso)
    }

    fun signInIntent(context: Context): Intent = signInClient(context).signInIntent

    fun lastAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context.applicationContext)

    fun isSignedIn(context: Context): Boolean = lastAccount(context) != null

    fun accountEmail(context: Context): String =
        lastAccount(context)?.email?.trim().orEmpty()

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(signInClient(context).signOut()) }
    }

    /**
     * OAuth access token for Drive REST calls.
     */
    suspend fun accessToken(context: Context): String = withContext(Dispatchers.IO) {
        val account = lastAccount(context)
            ?: error("Not signed in to Google")
        val acct = account.account ?: error("Google account missing")
        GoogleAuthUtil.getToken(context.applicationContext, acct, "oauth2:$SCOPE_DRIVE")
    }
}
