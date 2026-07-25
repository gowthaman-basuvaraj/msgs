package the.waste.fellow.sms.adapters

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import the.waste.fellow.sms.R
import the.waste.fellow.sms.adapters.SingleGroupAdapter.MyViewHolder
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.single_sms_detailed, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val cursor = dataCursor ?: return
        cursor.moveToPosition(position)
        holder.message.text = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
        val time = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
        holder.time.text = Helpers.getDate(time)

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
    }

    private fun tint(holder: MyViewHolder, bgAttr: Int, textAttr: Int) {
        holder.bubble.setCardBackgroundColor(MaterialColors.getColor(holder.bubble, bgAttr))
        holder.message.setTextColor(MaterialColors.getColor(holder.message, textAttr))
    }

    fun swapCursor(cursor: Cursor?) {
        if (dataCursor === cursor) return
        dataCursor = cursor
        if (cursor != null) notifyDataSetChanged()
    }

    override fun getItemCount(): Int = dataCursor?.count ?: 0

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubble: MaterialCardView = itemView.findViewById(R.id.message_bubble)
        val message: TextView = itemView.findViewById(R.id.message)
        val time: TextView = itemView.findViewById(R.id.time)
    }
}
