package the.waste.fellow.sms.block

import android.content.Context
import android.widget.CheckBox
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

/**
 * Block / unblock [sender] (a normalized sender id) with a confirmation dialog. Blocking
 * optionally deletes the messages already received from that sender; future ones are always
 * dropped on arrival. [onChanged] runs on the main thread once the preference is updated so
 * callers can refresh their UI.
 */
fun showBlockChooser(context: Context, sender: String, onChanged: (() -> Unit)? = null) {
    val prefs = BlockedSenderPrefs(context)

    if (prefs.isBlocked(sender)) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Unblock \"$sender\"?")
            .setMessage("New messages from this sender will be delivered, notified and synced again.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unblock") { _, _ ->
                prefs.unblock(sender)
                Toast.makeText(context, "Unblocked $sender", Toast.LENGTH_SHORT).show()
                onChanged?.invoke()
            }
            .show()
        return
    }

    val pad = (20 * context.resources.displayMetrics.density).toInt()
    val deleteExisting = CheckBox(context).apply {
        text = "Also delete messages already received from this sender"
        setPadding(pad, pad / 2, pad, pad / 2)
    }

    MaterialAlertDialogBuilder(context)
        .setTitle("Block \"$sender\"?")
        .setMessage("New messages from this sender will be deleted as they arrive — no " +
                "notification, nothing stored, nothing synced to the server.")
        .setView(deleteExisting)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Block") { _, _ ->
            prefs.block(sender)
            val app = context.applicationContext
            val alsoDelete = deleteExisting.isChecked
            Toast.makeText(context, "Blocked $sender", Toast.LENGTH_SHORT).show()
            onChanged?.invoke()
            // Provider deletes and queue surgery touch the SMS database — keep them off the
            // main thread, then refresh the conversation list.
            thread {
                BlockedCleaner.purgeQueuedSync(app, sender)
                if (alsoDelete) {
                    BlockedCleaner.deleteMessagesFrom(app, sender)
                    BlockedCleaner.notifyListChanged(app)
                }
            }
        }
        .show()
}
