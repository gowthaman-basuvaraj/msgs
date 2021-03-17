package com.webianks.hatkemessenger.receivers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.Context.NOTIFICATION_SERVICE
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.webianks.hatkemessenger.R
import com.webianks.hatkemessenger.activities.SmsDetailedView
import com.webianks.hatkemessenger.constants.Constants
import com.webianks.hatkemessenger.services.SaveSmsService
import com.webianks.hatkemessenger.utils.PersonLookup
import com.webianks.hatkemessenger.utils.createChannel
import com.webianks.hatkemessenger.utils.getChannel
import java.util.*

class OtpCopy : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        val button = intent?.getStringExtra("BUTTON")
        val otp = intent?.getStringExtra("OTP")

        if (button == "COPY") {
            val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            cm?.clearPrimaryClip()
            cm?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
        }
    }

}


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
            val senderNo = lookupPerson?.normPhone ?: senderNoOriginal


            val regex4 = Regex("\\d{4}")
            val regex6 = Regex("\\d{6}")
            val regex8 = Regex("\\d{6}")
            val containsOTP = message.contains(regex4) || message.contains(Regex("\\d{6}"))|| message.contains(Regex("\\d{8}"))
            val isOTP = listOf("otp", "password").any { message.toLowerCase(Locale.ROOT).contains(it) }

            val otp4 = regex4.find(message)
            val otp6 = regex6.find(message)
            val otp8 = regex8.find(message)
            if (isOTP && containsOTP && (otp4 != null || otp6 != null || otp8 != null)) {

                val otp = otp8 ?: otp6 ?: otp4
                val otpNum = otp!!.groupValues.first()
                //issue OTP notification

                val otpChanId = "OTP from $senderNo"
                val otpChannel = context.getChannel(otpChanId)
                        ?: context.createChannel(otpChanId, "OTP Notifications", true, NotificationManager.IMPORTANCE_HIGH)


                showOTP(senderNo, otpNum, context, otpChanId)
            } else {
                val existingNC = context.getChannel(senderNo)
                        ?: context.createChannel(senderNo, "SMS Notifications")

                if (existingNC != null && existingNC.importance != NotificationManager.IMPORTANCE_NONE) {
                    issueNotification(context, senderNo, message, senderNo)
                }
            }
            saveSmsInInbox(context,
                    senderNo,
                    message,
                    ts)
        }
        abortBroadcast()
        // End of loop
    } // bundle null

    private fun showOTP(from: String, otp: String, context: Context, otpChannel: String) {
        val s = "OTP from $from"
        val builder = NotificationCompat.Builder(context, otpChannel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(s)
                .setContentText(otp)
                .setAutoCancel(true)
                .setAllowSystemGeneratedContextualActions(false)
                .setVibrate(listOf(0L).toLongArray())
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)


        builder.addAction(
                R.drawable.ic_baseline_content_copy_24,
                "Copy",
                PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, OtpCopy::class.java).apply {
                            action = "COPY"
                            putExtra("BUTTON", "COPY")
                            putExtra("OTP", otp)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
        )



        builder.addAction(
                R.drawable.ic_baseline_close_24,
                "Cancel",
                PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent(context, OtpCopy::class.java).apply {
                            action = "CANCEL"
                            putExtra("BUTTON", "CANCEL")
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
        )

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, builder.build())
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
        val mNotifyMgr = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager


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