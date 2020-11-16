package com.webianks.hatkemessenger.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.amulyakhare.textdrawable.TextDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webianks.hatkemessenger.R
import com.webianks.hatkemessenger.SMS
import com.webianks.hatkemessenger.adapters.AllConversationAdapter.MyHolder
import com.webianks.hatkemessenger.utils.ColorGeneratorModified
import com.webianks.hatkemessenger.utils.Helpers
import com.webianks.hatkemessenger.utils.PersonLookup
import com.webianks.hatkemessenger.utils.createChannel


/**
 * Created by R Ankit on 25-12-2016.
 */
class AllConversationAdapter(private val context: Context, private val data: MutableList<SMS>)
    : RecyclerView.Adapter<MyHolder>() {

    private var itemClickListener: ItemCLickListener? = null
    private val generator = ColorGeneratorModified.MATERIAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.single_sms_small_layout, parent, false)
        return MyHolder(view)
    }

    private val lookup = PersonLookup(this.context)
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val sms = data[position]

        val savedContactName = lookup.lookupPerson(sms.address)?.name ?: sms.address ?: "NA"
        Log.w("LIST", "${sms.address} => $savedContactName")
        holder.senderContact.text = savedContactName
        holder.message.text = sms.msg

        val color = savedContactName.let { generator?.getColor(it) }
        val firstChar = savedContactName[0].toString()
        val drawable = TextDrawable.builder().buildRound(firstChar, color!!)

        holder.senderImage.setImageDrawable(drawable)
        sms.color = color

        if (sms.readState == "0") {
            holder.senderContact.setTypeface(holder.senderContact.typeface, Typeface.BOLD)
            holder.message.setTypeface(holder.message.typeface, Typeface.BOLD)
            holder.message.setTextColor(ContextCompat.getColor(context, R.color.black))
            holder.time.setTypeface(holder.time.typeface, Typeface.BOLD)
            holder.time.setTextColor(ContextCompat.getColor(context, R.color.black))
        } else {
            holder.senderContact.setTypeface(null, Typeface.NORMAL)
            holder.message.setTypeface(null, Typeface.NORMAL)
            holder.time.setTypeface(null, Typeface.NORMAL)
        }
        holder.time.text = sms.time.let { Helpers.getDate(it) }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    fun setItemClickListener(itemClickListener: ItemCLickListener?) {
        this.itemClickListener = itemClickListener
    }

    inner class MyHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener,
            OnLongClickListener {

        val senderImage: ImageView = itemView.findViewById(R.id.smsImage)
        val senderContact: TextView = itemView.findViewById(R.id.smsSender)
        val message: TextView = itemView.findViewById(R.id.smsContent)
        val time: TextView = itemView.findViewById(R.id.time)
        private val mainLayout: RelativeLayout = itemView.findViewById(R.id.small_layout_main)

        override fun onClick(view: View) {
            if (itemClickListener != null) {
                data[adapterPosition].readState = "1"
                notifyItemChanged(adapterPosition)

                itemClickListener?.itemClicked(
                        data[adapterPosition].color,
                        data[adapterPosition].address,
                        senderContact.text.toString(),
                        data[adapterPosition].id,
                        data[adapterPosition].readState
                )
            }
        }

        override fun onLongClick(view: View): Boolean {
            val items = arrayOf("Delete", "Mute/Un-Mute")
            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, android.R.id.text1, items)
            MaterialAlertDialogBuilder(context)
                    .setAdapter(adapter) { dialogInterface, idx ->
                        dialogInterface.dismiss()
                        if (idx == 0)
                            deleteDialog()
                        else
                            muteDialog()
                    }
                    .show()
            return true
        }

        private fun deleteDialog() {
            val alert = MaterialAlertDialogBuilder(context)
            alert.setMessage("Are you sure you want to delete this message?")
            alert.setPositiveButton("Yes") { _, _ -> deleteSMS(data[adapterPosition].id, adapterPosition) }
            alert.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            alert.create()
            alert.show()
        }

        private fun muteDialog() {
            val senderNo = data[adapterPosition].normAddress!!

            val intent: Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, senderNo)

            startActivity(this@AllConversationAdapter.context, intent, null)

        }

        private fun setNotification(num: String, yesOrNo: Boolean) {
            context.createChannel(num, "SMS Notification", yesOrNo)
        }

        init {
            mainLayout.setOnClickListener(this)
            mainLayout.setOnLongClickListener(this)
        }
    }

    private fun deleteSMS(messageId: Long, position: Int) {
        val affected = context.contentResolver.delete(
                Uri.parse("content://sms/$messageId"), null, null).toLong()
        if (affected != 0L) {
            data.removeAt(position)
            notifyItemRemoved(position)
        }
    }


}