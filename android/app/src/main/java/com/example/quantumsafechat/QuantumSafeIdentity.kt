package com.example.quantumsafechat

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuantumSafeAccount(
    val accountId: String,
    val phoneNumber: String,
    val displayName: String,
    val deviceId: String,
    val createdAt: Long
)

class QuantumSafeIdentityStore(context: Context) {

    companion object {
        private const val PREFS = "quantumsafe_identity"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PHONE = "phone_number"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_DARK_MODE = "dark_mode"

        // Development-only OTP. Replace only the verification layer later.
        const val DEMO_OTP = "123456"

        fun normalizePhone(value: String): String =
            value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")

        fun accountIdForPhone(phoneNumber: String): String {
            val normalized = normalizePhone(phoneNumber)
            return "qs_" + sha256(normalized).take(24)
        }

        fun displayInitial(displayName: String): String {
            val letter = displayName.trim().firstOrNull { it.isLetterOrDigit() }
            return (letter ?: 'Q').uppercaseChar().toString()
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): QuantumSafeAccount? {
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val phone = prefs.getString(KEY_PHONE, null) ?: return null
        val name = prefs.getString(KEY_DISPLAY_NAME, null) ?: return null
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return null

        return QuantumSafeAccount(
            accountId = accountId,
            phoneNumber = phone,
            displayName = name,
            deviceId = deviceId,
            createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        )
    }

    fun createAccount(phoneNumber: String, displayName: String): QuantumSafeAccount {
        val normalizedPhone = normalizePhone(phoneNumber)
        val accountId = accountIdForPhone(normalizedPhone)
        val existingDeviceId = prefs.getString(KEY_DEVICE_ID, null)
        val deviceId = existingDeviceId ?: "android_" + UUID.randomUUID().toString()

        val account = QuantumSafeAccount(
            accountId = accountId,
            phoneNumber = normalizedPhone,
            displayName = displayName.trim(),
            deviceId = deviceId,
            createdAt = System.currentTimeMillis()
        )

        prefs.edit()
            .putString(KEY_ACCOUNT_ID, account.accountId)
            .putString(KEY_PHONE, account.phoneNumber)
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .putString(KEY_DEVICE_ID, account.deviceId)
            .putLong(KEY_CREATED_AT, account.createdAt)
            .apply()

        return account
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
}

@Composable
fun QuantumSafeOnboardingScreen(
    onAccountCreated: (QuantumSafeAccount) -> Unit
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("+91 ") }
    var displayName by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpRequested by remember { mutableStateOf(false) }
    var createdAccount by remember { mutableStateOf<QuantumSafeAccount?>(null) }
    var error by remember { mutableStateOf("") }

    val colors = LocalQuantumSafePalette.current
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.textSecondary,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.cyberPeaceBlue,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.cyberpeace_mark),
                contentDescription = "CyberPeace",
                modifier = Modifier.size(72.dp).clip(CircleShape)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "QuantumSafe",
                color = colors.navy,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "by CyberPeace",
                color = colors.cyberPeaceBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(30.dp))

            Text(
                when {
                    createdAccount != null -> "Account created"
                    otpRequested -> "Verify your number"
                    else -> "Create your account"
                },
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            val account = createdAccount
            if (account != null) {
                AccountIdentitySummary(account)

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { onAccountCreated(account) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to chats")
                }
            } else if (!otpRequested) {
                TextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mobile number") },
                    singleLine = true,
                    colors = fieldColors
                )

                Spacer(Modifier.height(14.dp))

                TextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    singleLine = true,
                    colors = fieldColors
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (phone.filter { it.isDigit() }.length < 10) {
                            error = "Enter a valid mobile number."
                        } else if (displayName.trim().length < 2) {
                            error = "Enter your display name."
                        } else {
                            otpRequested = true
                            error = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send verification code")
                }
            } else {
                Text(
                    "Demo verification code: 123456",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(12.dp))

                TextField(
                    value = otp,
                    onValueChange = {
                        otp = it.filter(Char::isDigit).take(6)
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("6-digit code") },
                    singleLine = true,
                    colors = fieldColors
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (otp == QuantumSafeIdentityStore.DEMO_OTP) {
                            val store = QuantumSafeIdentityStore(context)
                            createdAccount = store.createAccount(phone, displayName)
                            error = ""
                        } else {
                            error = "Incorrect verification code."
                        }
                    },
                    enabled = otp.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify and continue")
                }

                TextButton(
                    onClick = {
                        otpRequested = false
                        otp = ""
                        error = ""
                    }
                ) {
                    Text("Change number")
                }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = colors.danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Your account is protected by QuantumSafe's post-quantum security layer.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AccountIdentitySummary(account: QuantumSafeAccount) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IdentityLine("Display name", account.displayName)
        IdentityLine("Mobile number", account.phoneNumber)
        IdentityLine("Account ID", account.accountId)
        IdentityLine("Device ID", account.deviceId)
    }
}

@Composable
private fun IdentityLine(label: String, value: String) {
    val colors = LocalQuantumSafePalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = colors.textPrimary, fontSize = 12.sp)
    }
}
