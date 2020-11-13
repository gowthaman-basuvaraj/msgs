package com.webianks.hatkemessenger.receivers

import android.annotation.SuppressLint
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
import java.util.*

/**
 * Created by R Ankit on 24-12-2016.
 */
class SmsReceiver : BroadcastReceiver() {
    private val TAG = SmsReceiver::class.java.simpleName

    @SuppressLint("NewApi")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            Log.e(TAG, "smsReceiver")
            val bundle = intent.extras ?: return
            val pdu_Objects = bundle["pdus"] as Array<Any>? ?: return

            var senderNoOriginal = ""
            var message = ""
            var ts = 0L
            for (aObject in pdu_Objects) {
                val currentSMS = getIncomingMessage(aObject, bundle)
                senderNoOriginal = currentSMS.displayOriginatingAddress
                message += currentSMS.displayMessageBody
                ts = currentSMS.timestampMillis
            }


            //for INDIA, we get lot of SMS from Banks and other providers of this format
            //2 Alphabets - (hyhen) 6 Alphabets
            //first 2 will change but the last 6 will remain same,
            //lets group by last 6 if it matched the pattern


            val lookupPerson = PersonLookup(context).lookupPerson(senderNoOriginal)
            val cn = lookupPerson?.name ?: lookupPerson?.normPhone ?: senderNoOriginal
            val senderNo = lookupPerson?.normPhone ?: senderNoOriginal
            val existingNC = getChannel(senderNo, context)
            if (existingNC != null) {
                if (existingNC.importance != NotificationManager.IMPORTANCE_NONE) {
                    issueNotification(context, senderNo, message, cn)
                } else {
                    //do not issue
                }
            } else {
                //create a channal but set it to no notify...
                createChannel(cn, "SMS Notifications", context)
                //we will not notify by default :P
                //issueNotification(context, senderNo, message, cn)
            }
            saveSmsInInbox(context,
                    senderNo,
                    message,
                    ts)
        }
        abortBroadcast()
        // End of loop
    } // bundle null

}

private fun saveSmsInInbox(context: Context, sender: String, mesg: String, date: Long) {
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
            importance = getImportanceLevel(senderNo)
        }
        mNotifyMgr.createNotificationChannel(channel)
    }
}

@SuppressLint("InlinedApi")
private fun getImportanceLevel(senderNo: String): Int {
    //by default we will not notify for anything
    //however we will notify based on preference
    return NotificationManager.IMPORTANCE_NONE
}

private fun getChannel(senderNo: String, context: Context): NotificationChannel? {
    val mNotifyMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mNotifyMgr.getNotificationChannel(senderNo)
    } else {
        null
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
