package the.waste.fellow.sms.retention

import android.content.Context
import android.text.InputType
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Prompts for how many days of messages to keep for [sender]. Blank or 0 keeps them forever.
 * Persists the choice and kicks off an immediate cleanup. [onChanged] refreshes caller UI.
 */
fun showRetentionChooser(context: Context, sender: String, onChanged: (() -> Unit)? = null) {
    val prefs = SenderRetentionPrefs(context)
    val current = prefs.days(sender)

    val pad = (20 * context.resources.displayMetrics.density).toInt()
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        hint = "Days (0 = keep forever)"
        if (current > 0) setText(current.toString())
        setPadding(pad, pad / 2, pad, pad / 2)
    }

    MaterialAlertDialogBuilder(context)
        .setTitle("Auto-delete old messages")
        .setMessage("Delete messages from \"$sender\" older than this many days. " +
                "Leave blank or 0 to keep them forever.")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save") { _, _ ->
            val days = input.text.toString().trim().toIntOrNull() ?: 0
            prefs.set(sender, days)
            if (days > 0) RetentionCleaner.scheduleNow(context)
            onChanged?.invoke()
        }
        .show()
}
