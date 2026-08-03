package the.waste.fellow.sms.retention

import android.content.Context
import android.text.InputType
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Prompts for how many of the newest messages to keep for [sender]. Blank or 0 keeps them
 * all. Persists the choice and kicks off an immediate cleanup. [onChanged] refreshes caller UI.
 */
fun showRetentionChooser(context: Context, sender: String, onChanged: (() -> Unit)? = null) {
    val prefs = SenderRetentionPrefs(context)
    val current = prefs.keepCount(sender)

    val pad = (20 * context.resources.displayMetrics.density).toInt()
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        hint = "Messages to keep (0 = keep all)"
        if (current > 0) setText(current.toString())
        setPadding(pad, pad / 2, pad, pad / 2)
    }

    MaterialAlertDialogBuilder(context)
        .setTitle("Keep only recent messages")
        .setMessage("Keep only this many of the newest messages from \"$sender\" and delete " +
                "the rest. Leave blank or 0 to keep them all.")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save") { _, _ ->
            val keepCount = input.text.toString().trim().toIntOrNull() ?: 0
            prefs.set(sender, keepCount)
            if (keepCount > 0) RetentionCleaner.scheduleNow(context)
            onChanged?.invoke()
        }
        .show()
}
