package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.firestore
import com.sensars.eurostars.data.SessionRepository
import com.sensars.eurostars.data.RoleRepository
import com.sensars.eurostars.data.UserRole
import kotlinx.coroutines.launch

/**
 * Handles clinician email/password signup & login and basic session persistence.
 * Patient ID login is in PatientAuthViewModel.
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val session = SessionRepository(app)
    private val roleRepo = RoleRepository(app)

    // -------------------- Sign Up --------------------

    /**
     * Create a clinician account:
     *  - FirebaseAuth.createUserWithEmailAndPassword
     *  - Send verification email
     *  - Create clinician profile in Firestore
     */
    fun signUpClinician(
        first: String,
        last: String,
        emailRaw: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val email = emailRaw.trim()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user == null) {
                    onError("Signup succeeded but user is null. Try signing in.")
                    return@addOnSuccessListener
                }

                // Fire & forget verification email (we’ll show Verify screen next)
                user.sendEmailVerification()

                // Store a minimal profile
                val profile = hashMapOf(
                    "firstName" to first.trim(),
                    "lastName" to last.trim(),
                    "email" to email,
                    "emailVerified" to false,
                    // Keep a clinician number to satisfy SRD; you can swap this for a nicer generator later.
                    "clinicianId" to "C-${System.currentTimeMillis()}"
                )

                db.collection("clinicians").document(user.uid).set(profile)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError("Profile save failed: ${friendlyError(e)}") }
            }
            .addOnFailureListener { e -> onError(friendlyError(e)) }
    }

    // -------------------- Login --------------------

    /**
     * Login a clinician:
     *  - If email is verified → persist session and call onVerified
     *  - If not verified → call onNeedsVerification (UI should navigate to Verify screen)
     */
    fun loginClinician(
        emailRaw: String,
        password: String,
        onVerified: () -> Unit,
        onNeedsVerification: () -> Unit,
        onError: (String) -> Unit
    ) {
        val email = emailRaw.trim()
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                when {
                    user == null -> onError("Login succeeded but user is null. Try again.")
                    user.isEmailVerified -> {
                        viewModelScope.launch {
                            session.setClinician(email)
                            roleRepo.setRole(UserRole.CLINICIAN)
                            onVerified()
                        }
                    }
                    else -> onNeedsVerification()
                }
            }
            .addOnFailureListener { e -> onError(friendlyError(e)) }
    }

    // -------------------- Verification helpers --------------------

    /** Resend verification email to the currently signed-in user. */
    fun resendVerification(onDone: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return onError("No user is signed in.")
        user.sendEmailVerification()
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { e -> onError(friendlyError(e)) }
    }

    /**
     * Re-fetch current user and report if verified.
     * Useful on Verify screen after the user taps the email link and comes back.
     */
    fun refreshAndCheckVerified(
        onVerified: () -> Unit,
        onNotVerified: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser ?: return onError("No user is signed in.")
        user.reload()
            .addOnSuccessListener {
                if (user.isEmailVerified) onVerified() else onNotVerified()
            }
            .addOnFailureListener { e -> onError(friendlyError(e)) }
    }

    // -------------------- Session & sign-out --------------------

    fun signOut(onDone: () -> Unit) {
        auth.signOut()
        viewModelScope.launch {
            session.clear()
            roleRepo.clearRole()
        }.invokeOnCompletion { onDone() }
    }

    fun currentUserEmail(): String? = auth.currentUser?.email

    // -------------------- Error normalization --------------------

    private fun friendlyError(e: Exception): String = when (e) {
        is FirebaseAuthUserCollisionException -> "An account with this email already exists."
        is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
        is FirebaseAuthInvalidUserException -> "No account found for this email."
        is FirebaseNetworkException -> "Network error. Check your connection and try again."
        else -> e.message ?: "Unexpected error."
    }
}
