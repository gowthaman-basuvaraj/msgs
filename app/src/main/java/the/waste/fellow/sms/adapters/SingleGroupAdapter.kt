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
 * Created by R Ankit on 25-12-2016.
 * Renders a conversation as chat bubbles: received messages align to the start, sent
 * messages (persisted to content://sms/sent by SmsSender) align to the end with a tinted
 * bubble.
 */
class SingleGroupAdapter(
    private val context: Context,
    private var dataCursor: Cursor?,
    @Suppress("UNUSED_PARAMETER") color: Int,
    @Suppress("UNUSED_PARAMETER") savedContactName: String?,
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
        if (isSent) {
            params.gravity = Gravity.END
            params.marginStart = sideMargin
            params.marginEnd = 0
            holder.bubble.setCardBackgroundColor(
                MaterialColors.getColor(holder.bubble, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            holder.message.setTextColor(
                MaterialColors.getColor(holder.message, com.google.android.material.R.attr.colorOnPrimaryContainer)
            )
        } else {
            params.gravity = Gravity.START
            params.marginStart = 0
            params.marginEnd = sideMargin
            holder.bubble.setCardBackgroundColor(
                MaterialColors.getColor(holder.bubble, com.google.android.material.R.attr.colorSurfaceVariant)
            )
            holder.message.setTextColor(
                MaterialColors.getColor(holder.message, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
        }
        holder.bubble.layoutParams = params
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
