package the.waste.fellow.sms.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import the.waste.fellow.sms.R
import the.waste.fellow.sms.notify.NotificationPolicy
import the.waste.fellow.sms.notify.RuleAction
import the.waste.fellow.sms.notify.SenderRule

/**
 * Manages the per-sender keyword rules that force-notify or mute messages, overriding the
 * default mute/whitelist behaviour. Rules are persisted via [NotificationPolicy].
 */
class RulesActivity : AppCompatActivity() {

    private lateinit var policy: NotificationPolicy
    private lateinit var adapter: RulesAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Keyword rules"

        policy = NotificationPolicy(this)
        emptyView = findViewById(R.id.emptyView)

        adapter = RulesAdapter(
            rules = policy.rules().toMutableList(),
            onDelete = { index ->
                policy.removeRuleAt(index)
                reload()
            }
        )
        findViewById<RecyclerView>(R.id.rulesRecycler).apply {
            layoutManager = LinearLayoutManager(this@RulesActivity)
            adapter = this@RulesActivity.adapter
        }
        findViewById<ExtendedFloatingActionButton>(R.id.addRuleFab).setOnClickListener {
            showRuleDialog()
        }
        updateEmptyState()
    }

    private fun reload() {
        adapter.replaceAll(policy.rules())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun showRuleDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val senderInput = view.findViewById<TextInputEditText>(R.id.inputSender)
        val keywordsInput = view.findViewById<TextInputEditText>(R.id.inputKeywords)
        val toggle = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.actionToggle)
        toggle.check(R.id.actionNotify)

        MaterialAlertDialogBuilder(this)
            .setTitle("New rule")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val sender = senderInput.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: SenderRule.WILDCARD
                val keywords = keywordsInput.text?.toString().orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val action = if (toggle.checkedButtonId == R.id.actionMute)
                    RuleAction.MUTE else RuleAction.NOTIFY
                policy.addRule(SenderRule(sender.uppercase(), keywords, action))
                reload()
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

private class RulesAdapter(
    private val rules: MutableList<SenderRule>,
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<RulesAdapter.Holder>() {

    fun replaceAll(newRules: List<SenderRule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rule = rules[position]
        holder.sender.text = if (rule.sender == SenderRule.WILDCARD) "All senders" else rule.sender
        holder.keywords.text = if (rule.keywords.isEmpty())
            "Any message" else rule.keywords.joinToString(", ")
        holder.action.text = rule.action.name.lowercase().replaceFirstChar { it.uppercase() }
        holder.delete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = rules.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sender: TextView = itemView.findViewById(R.id.ruleSender)
        val keywords: TextView = itemView.findViewById(R.id.ruleKeywords)
        val action: Chip = itemView.findViewById(R.id.ruleAction)
        val delete: ImageButton = itemView.findViewById(R.id.ruleDelete)
    }
}
