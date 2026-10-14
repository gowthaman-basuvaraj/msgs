package the.waste.fellow.sms.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import the.waste.fellow.sms.R
import the.waste.fellow.sms.block.BlockedSenderPrefs
import the.waste.fellow.sms.block.showBlockChooser
import the.waste.fellow.sms.utils.AppSettings
import the.waste.fellow.sms.utils.Helpers

/**
 * The list of blocked senders, reachable from Settings. Messages from these senders are
 * deleted as they arrive (never stored, notified or synced — see
 * [the.waste.fellow.sms.receivers.SmsReceiver]). Senders can be unblocked here, or added
 * by hand with the FAB for a sender you haven't received anything from yet.
 */
class BlockedSendersActivity : AppCompatActivity() {

    private lateinit var prefs: BlockedSenderPrefs
    private lateinit var adapter: BlockedAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Blocked senders"

        prefs = BlockedSenderPrefs(this)
        emptyView = findViewById(R.id.emptyView)

        adapter = BlockedAdapter(
            entries = prefs.all().toMutableList(),
            onUnblock = { sender -> confirmUnblock(sender) }
        )
        findViewById<RecyclerView>(R.id.blockedRecycler).apply {
            layoutManager = LinearLayoutManager(this@BlockedSendersActivity)
            adapter = this@BlockedSendersActivity.adapter
        }
        findViewById<ExtendedFloatingActionButton>(R.id.addBlockedFab).setOnClickListener {
            showAddDialog()
        }
        updateEmptyState()
    }

    private fun reload() {
        adapter.replaceAll(prefs.all())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun confirmUnblock(sender: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unblock \"$sender\"?")
            .setMessage("New messages from this sender will be delivered, notified and synced again.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unblock") { _, _ ->
                prefs.unblock(sender)
                reload()
            }
            .show()
    }

    /** Block a sender by typing its id, for senders with no conversation to long-press. */
    private fun showAddDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "Sender id or phone number"
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Block a sender")
            .setMessage("Enter a sender id (e.g. INDPOST) or a phone number. It is stored in " +
                    "the grouped form, so every network prefix of that sender is covered.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Next") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) return@setPositiveButton
                val sender = AppSettings(this).normalizeSender(raw)
                if (prefs.isBlocked(sender)) {
                    Toast.makeText(this, "$sender is already blocked", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showBlockChooser(this, sender) { reload() }
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

private class BlockedAdapter(
    private val entries: MutableList<Pair<String, Long>>,
    private val onUnblock: (String) -> Unit,
) : RecyclerView.Adapter<BlockedAdapter.Holder>() {

    fun replaceAll(newEntries: List<Pair<String, Long>>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (sender, blockedAt) = entries[position]
        holder.sender.text = sender
        holder.since.text = if (blockedAt > 0)
            "Blocked ${Helpers.getDate(blockedAt)}" else "Blocked"
        holder.unblock.setOnClickListener { onUnblock(sender) }
    }

    override fun getItemCount(): Int = entries.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sender: TextView = itemView.findViewById(R.id.blockedSender)
        val since: TextView = itemView.findViewById(R.id.blockedSince)
        val unblock: MaterialButton = itemView.findViewById(R.id.blockedUnblock)
    }
}
