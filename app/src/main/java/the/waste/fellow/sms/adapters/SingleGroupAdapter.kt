package the.waste.fellow.sms.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.Telephony
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import the.waste.fellow.sms.R
import the.waste.fellow.sms.adapters.SingleGroupAdapter.MyViewHolder
import the.waste.fellow.sms.notify.OtpDetector
import the.waste.fellow.sms.sync.PendingSyncStore
import the.waste.fellow.sms.utils.AppSettings
import the.waste.fellow.sms.utils.Helpers

/**
 * Renders a conversation two ways:
 *  - [isPersonal] = true  → chat bubbles (received left, sent right) for real two-way chats.
 *  - [isPersonal] = false → full-width message cards for one-way alphanumeric sender IDs
 *    (banks, OTPs), which have no replies and read better using the whole width.
 */
class SingleGroupAdapter(
    private val context: Context,
    private var dataCursor: Cursor?,
    private val isPersonal: Boolean,
) : RecyclerView.Adapter<MyViewHolder>() {

    private val sideMargin = (48 * context.resources.displayMetrics.density).toInt()

    // Per-message sync status is only meaningful (and only shown) when server sync is set up.
    private val syncOn = AppSettings(context).syncConfigured
    // Keys of messages still awaiting upload; a received message not in here counts as synced.
    // Keyed by (date_sent, body), which is exactly what the queue stores for an inbound SMS.
    private var pendingKeys: Set<String> = loadPending()

    private fun loadPending(): Set<String> =
        if (!syncOn) emptySet()
        else PendingSyncStore(context).all().map { syncKey(it.date, it.text) }.toSet()

    // date_sent + body identifies an inbound message; this is exactly what the queue stores.
    private fun syncKey(dateSent: Long, body: String) = "$dateSent|$body"

    /** Recompute sync state from the queue (e.g. on resume, after uploads may have drained). */
    fun refreshSyncState() {
        if (!syncOn) return
        pendingKeys = loadPending()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.single_sms_detailed, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val cursor = dataCursor ?: return
        cursor.moveToPosition(position)
        val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
        holder.message.text = body
        val time = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
        holder.time.text = Helpers.getDate(time)

        // Offer a Copy button when the message contains an OTP.
        val otp = OtpDetector.extract(body)
        if (otp.isOtp && otp.code != null) {
            holder.copyOtp.visibility = View.VISIBLE
            holder.copyOtp.setOnClickListener { copyOtp(otp.code) }
        } else {
            holder.copyOtp.visibility = View.GONE
            holder.copyOtp.setOnClickListener(null)
        }

        val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
        val isSent = typeIndex >= 0 &&
                cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_SENT

        val params = holder.bubble.layoutParams as FrameLayout.LayoutParams
        when {
            // One-way sender id → full-width card.
            !isPersonal -> {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.START
                params.marginStart = 0
                params.marginEnd = 0
                tint(holder, com.google.android.material.R.attr.colorSurfaceVariant,
                    com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            // Personal chat, sent by me → right-aligned tinted bubble.
            isSent -> {
                params.width = FrameLayout.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.END
                params.marginStart = sideMargin
                params.marginEnd = 0
                tint(holder, com.google.android.material.R.attr.colorPrimaryContainer,
                    com.google.android.material.R.attr.colorOnPrimaryContainer)
            }
            // Personal chat, received → left-aligned bubble.
            else -> {
                params.width = FrameLayout.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.START
                params.marginStart = 0
                params.marginEnd = sideMargin
                tint(holder, com.google.android.material.R.attr.colorSurfaceVariant,
                    com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        }
        holder.bubble.layoutParams = params

        // Subtle sync status — only for received messages, and only when server sync is on.
        // (Outbound messages are never synced; the server is a received-SMS forwarder.)
        if (syncOn && !isSent) {
            val dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT))
            val synced = syncKey(dateSent, body) !in pendingKeys
            holder.syncStatus.setImageResource(
                if (synced) R.drawable.ic_sync_done_16 else R.drawable.ic_sync_pending_16
            )
            holder.syncStatus.contentDescription =
                context.getString(if (synced) R.string.synced else R.string.sync_pending)
            holder.syncStatus.visibility = View.VISIBLE
        } else {
            holder.syncStatus.visibility = View.GONE
        }
    }

    private fun tint(holder: MyViewHolder, bgAttr: Int, textAttr: Int) {
        holder.bubble.setCardBackgroundColor(MaterialColors.getColor(holder.bubble, bgAttr))
        holder.message.setTextColor(MaterialColors.getColor(holder.message, textAttr))
    }

    private fun copyOtp(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP", code))
        // Android 13+ shows its own copy confirmation; avoid a duplicate toast there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
        }
    }

    fun swapCursor(cursor: Cursor?) {
        if (dataCursor === cursor) return
        dataCursor = cursor
        pendingKeys = loadPending()
        if (cursor != null) notifyDataSetChanged()
    }

    override fun getItemCount(): Int = dataCursor?.count ?: 0

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubble: MaterialCardView = itemView.findViewById(R.id.message_bubble)
        val message: TextView = itemView.findViewById(R.id.message)
        val time: TextView = itemView.findViewById(R.id.time)
        val copyOtp: MaterialButton = itemView.findViewById(R.id.copyOtp)
        val syncStatus: ImageView = itemView.findViewById(R.id.sync_status)
    }
}
