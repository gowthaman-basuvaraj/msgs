package com.webianks.hatkemessenger.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import com.webianks.hatkemessenger.R
import com.webianks.hatkemessenger.activities.SmsDetailedView
import com.webianks.hatkemessenger.constants.Constants
import com.webianks.hatkemessenger.services.SaveSmsService
import com.webianks.hatkemessenger.utils.PersonLookup

/**
 * Created by R Ankit on 24-12-2016.
 */
class SmsReceiver : BroadcastReceiver() {
    private val TAG = SmsReceiver::class.java.simpleName
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            Log.e(TAG, "smsReceiver")
            val bundle = intent.extras
            if (bundle != null) {
                val pdu_Objects = bundle["pdus"] as Array<Any>?
                if (pdu_Objects != null) {
                    for (aObject in pdu_Objects) {
                        val currentSMS = getIncomingMessage(aObject, bundle)
                        val senderNo = currentSMS.displayOriginatingAddress
                        val message = currentSMS.displayMessageBody
                        val lookupPerson = PersonLookup(context).lookupPerson(senderNo)
                        val cn = lookupPerson?.name ?: senderNo
                        createChannel(cn, "SMS Notifications", context)
                        issueNotification(context, senderNo, message, cn)
                        saveSmsInInbox(context,
                                currentSMS.displayOriginatingAddress,
                                currentSMS.displayMessageBody,
                                currentSMS.timestampMillis)
                    }
                    abortBroadcast()
                    // End of loop
                }
            }
        } // bundle null

    }

    private fun saveSmsInInbox(context: Context, sender: String, mesg: String, date: Long ) {
        val serviceIntent = Intent(context, SaveSmsService::class.java)
        serviceIntent.putExtra("sender_no", sender)
        serviceIntent.putExtra("message", mesg)
        serviceIntent.putExtra("date", date)
        context.startService(serviceIntent)
    }

    private fun issueNotification(context: Context, senderNo: String, message: String, cn: String) {
        val resultIntent = Intent(context, SmsDetailedView::class.java)
        resultIntent.putExtra(Constants.CONTACT_NAME, senderNo)
        resultIntent.putExtra(Constants.FROM_SMS_RECIEVER, true)
        val resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val icon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val mNotifyMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        val mBuilder = NotificationCompat.Builder(context, cn)
                .setLargeIcon(icon)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(cn)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val mNotificationId = 10001
        mNotifyMgr.notify(mNotificationId, mBuilder.build())

    }

    private fun createChannel(senderNo: String, message: String, context: Context) {
        val mNotifyMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(senderNo, senderNo, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = message
            }
            mNotifyMgr.createNotificationChannel(channel)
        }
    }

    private fun getIncomingMessage(aObject: Any, bundle: Bundle): SmsMessage {
        val currentSMS: SmsMessage
        currentSMS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val format = bundle.getString("format")
            SmsMessage.createFromPdu(aObject as ByteArray, format)
        } else {
            SmsMessage.createFromPdu(aObject as ByteArray)
        }
        return currentSMS
    }
}