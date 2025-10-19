package com.sensars.eurostars.ui.screens.clinician

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sensars.eurostars.viewmodel.AuthViewModel
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.sensars.eurostars.ui.utils.rememberWindowWidthClass

@Composable
fun ClinicianSignupScreen(
    onSignupComplete: () -> Unit,
    onBackToLogin: () -> Unit,
    vm: AuthViewModel = viewModel()
) {
    val widthClass = rememberWindowWidthClass()

    // Form state
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // UI state
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // Validation
    val firstErr = first.isBlank()
    val lastErr = last.isBlank()
    val emailErr = !email.isValidEmail()
    val passErr  = password.passwordError() // null if OK, else message
    val isValid = !firstErr && !lastErr && !emailErr && passErr == null

    val contentWidth = when (widthClass) {
        WindowWidthSizeClass.Compact -> Modifier.fillMaxWidth()
        WindowWidthSizeClass.Medium  -> Modifier.widthIn(max = 560.dp).fillMaxWidth()
        else                         -> Modifier.widthIn(max = 720.dp).fillMaxWidth()
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .then(contentWidth)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create Clinician Account",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sign up with your work email. We’ll send a verification link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (error != null) {
                    AssistiveBanner(
                        text = error!!,
                        color = MaterialTheme.colorScheme.errorContainer,
                        onColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // First name
                OutlinedTextField(
                    value = first,
                    onValueChange = { newText -> first = newText },
                    label = { Text("First name") },
                    singleLine = true,
                    isError = firstErr,
                    supportingText = {
                        if (firstErr) {
                            Text("First name is required")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))

                // Last name
                OutlinedTextField(
                    value = last,
                    onValueChange = { last = it },
                    label = { Text("Last name") },
                    singleLine = true,
                    isError = lastErr,
                    supportingText = {
                        if (lastErr) {
                            Text("Last name is required")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Work email") },
                    singleLine = true,
                    isError = email.isNotEmpty() && emailErr,
                    supportingText = {
                        if (email.isNotEmpty() && emailErr) Text("Enter a valid email address")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = password.isNotEmpty() && passErr != null,
                    supportingText = {
                        Text(passErr ?: "Min 8 chars, 1 upper, 1 number")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        loading = true; error = null
                        vm.signUpClinician(
                            first.trim(), last.trim(), email.trim(), password,
                            onSuccess = { loading = false; onSignupComplete() },
                            onError = { e -> loading = false; error = e }
                        )
                    },
                    enabled = isValid && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Creating account…" else "Create account")
                }

                TextButton(
                    onClick = { if (!loading) onBackToLogin() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Back to sign in")
                }

                Spacer(Modifier.height(24.dp))
                // Helpful tips / legal (optional)
                Text(
                    "By creating an account, you agree to receive a verification email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// Simple banner for errors/success
@Composable
private fun AssistiveBanner(text: String, color: androidx.compose.ui.graphics.Color, onColor: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        contentColor = onColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/* -------------------------- Validation helpers -------------------------- */

private fun String.isValidEmail(): Boolean {
    // Simple, fast regex; good enough for client-side
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    return this.matches(emailRegex)
}

/** Returns null when OK, or an error message when invalid */
private fun String.passwordError(): String? {
    if (length < 8) return "Password must be at least 8 characters"
    if (!any { it.isUpperCase() }) return "Include at least one uppercase letter"
    if (!any { it.isDigit() }) return "Include at least one number"
    return null
}
