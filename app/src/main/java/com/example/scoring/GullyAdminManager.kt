package com.example.scoring

import android.app.Activity
import android.text.InputType
import android.util.TypedValue
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.Keep
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

@Keep
object GullyAdminManager {

    /**
     * Prompts the user for the 4-digit Admin PIN (or fallback Passcode) before executing an admin-sensitive action.
     */
    fun verifyAdminPinAndExecute(
        activity: Activity,
        gullyId: String,
        actionTitle: String = "Delete Record",
        onAdminVerified: () -> Unit
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("gullies").document(gullyId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val adminPin = doc.getString("adminPin")
                        val fallbackPasscode = doc.getString("passcode")
                        val expectedPin = if (!adminPin.isNullOrBlank()) adminPin else fallbackPasscode ?: ""

                        showPinInputDialog(activity, actionTitle, expectedPin, onAdminVerified)
                    } else {
                        Toast.makeText(activity, "League data not found on cloud.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(activity, "Network error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(activity, "Verification error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPinInputDialog(
        activity: Activity,
        actionTitle: String,
        expectedPin: String,
        onAdminVerified: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-Digit Admin PIN"
            textSize = 18f
        }

        val container = FrameLayout(activity).apply {
            val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(actionTitle)
            .setMessage("Enter the 4-digit Admin PIN for this League to authorize deletion:")
            .setView(container)
            .setPositiveButton("Verify & Delete") { dialog, _ ->
                val enteredPin = input.text.toString().trim()
                if (enteredPin.equals(expectedPin, ignoreCase = true)) {
                    dialog.dismiss()
                    onAdminVerified()
                } else {
                    Toast.makeText(activity, "Incorrect Admin PIN. Action cancelled.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
