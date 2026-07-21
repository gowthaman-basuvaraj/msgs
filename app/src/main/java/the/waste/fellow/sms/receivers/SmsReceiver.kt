package the.waste.fellow.sms.receivers

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import the.waste.fellow.sms.R
import the.waste.fellow.sms.activities.OtpCopyActivity
import the.waste.fellow.sms.activities.SmsDetailedView
import the.waste.fellow.sms.constants.Constants
import the.waste.fellow.sms.notify.NotificationPolicy
import the.waste.fellow.sms.notify.OtpDetector
import the.waste.fellow.sms.services.SaveSmsService
import the.waste.fellow.sms.utils.AppSettings
import the.waste.fellow.sms.utils.PersonLookup
import the.waste.fellow.sms.utils.createChannel
import the.waste.fellow.sms.utils.getChannel

/**
 * Created by R Ankit on 24-12-2016.
 *
 * As the default SMS app the system delivers [Telephony.Sms.Intents.SMS_DELIVER_ACTION];
 * we write those to the provider AND notify. [Telephony.Sms.Intents.SMS_RECEIVED_ACTION]
 * is handled only when we are NOT the default app (notify only, no provider write) so we
 * never double-process a message.
 */
class SmsReceiver : BroadcastReceiver() {
    private val TAG = SmsReceiver::class.java.simpleName

    private val immutableFlag =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    @SuppressLint("NewApi")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val isReceived = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!isDeliver && !isReceived) return

        val amDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        // Default app already handles SMS_DELIVER; skip the duplicate SMS_RECEIVED.
        if (isReceived && amDefault) return

        Log.d(TAG, "onReceive $action default=$amDefault")
        val bundle = intent.extras ?: return
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val pduObjects = bundle["pdus"] as Array<Any>? ?: return

        var senderNoOriginal = ""
        var message = ""
        var ts = 0L
        for (aObject in pduObjects) {
            val currentSMS = getIncomingMessage(aObject, bundle)
            senderNoOriginal = currentSMS.displayOriginatingAddress
            message += currentSMS.displayMessageBody
            ts = currentSMS.timestampMillis
        }

        // For India, banks/providers send from headers like AX-INDPOST(-S); group by the
        // stripped core so the same sender coalesces. See PersonLookup / SenderNormalizer.
        val lookupPerson = PersonLookup(context).lookupPerson(senderNoOriginal)
        val senderNo = lookupPerson?.normPhone ?: senderNoOriginal

        val otp = OtpDetector.extract(message)

        // Baseline whitelist state: an un-muted channel, or the global default-notify pref.
        val existingChannel = context.getChannel(senderNo)
            ?: context.createChannel(senderNo, "SMS Notifications")
        val baselineNotify = (existingChannel != null &&
                existingChannel.importance != NotificationManager.IMPORTANCE_NONE) ||
                AppSettings(context).defaultNotify

        val shouldNotify = NotificationPolicy(context)
            .shouldNotify(senderNo, message, baselineNotify)

        if (shouldNotify) {
            if (otp.isOtp && otp.code != null) {
                val otpChanId = "OTP from $senderNo"
                context.getChannel(otpChanId)
                    ?: context.createChannel(
                        otpChanId, "OTP Notifications", true, NotificationManager.IMPORTANCE_HIGH
                    )
                showOTP(senderNo, otp.code, context, otpChanId)
            } else {
                issueNotification(context, senderNo, message, senderNo)
            }
        }

        if (isDeliver) {
            saveSmsInInbox(context, senderNo, message, ts)
        }
    }

    private fun showOTP(from: String, otp: String, context: Context, otpChannel: String) {
        val title = "OTP from $from"
        val builder = NotificationCompat.Builder(context, otpChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(otp)
            .setAutoCancel(true)
            .setAllowSystemGeneratedContextualActions(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Copy action routes through a transparent trampoline Activity — writing the
        // clipboard from a background BroadcastReceiver is blocked on Android 10+, which
        // is why the old OtpCopy receiver silently did nothing.
        val copyIntent = Intent(context, OtpCopyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(OtpCopyActivity.EXTRA_OTP, otp)
            putExtra(OtpCopyActivity.EXTRA_NOTIFICATION_ID, OTP_NOTIFICATION_ID)
        }
        builder.addAction(
            R.drawable.ic_baseline_content_copy_24,
            "Copy",
            PendingIntent.getActivity(context, 0, copyIntent, immutableFlag)
        )

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(OTP_NOTIFICATION_ID, builder.build())
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
            context, 0, resultIntent, immutableFlag
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        mNotifyMgr.notify(MESSAGE_NOTIFICATION_ID, mBuilder.build())
    }

    private fun getIncomingMessage(aObject: Any, bundle: Bundle): SmsMessage {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val format = bundle.getString("format")
            SmsMessage.createFromPdu(aObject as ByteArray, format)
        } else {
            @Suppress("DEPRECATION")
            SmsMessage.createFromPdu(aObject as ByteArray)
        }
    }

    companion object {
        private const val OTP_NOTIFICATION_ID = 999
        private const val MESSAGE_NOTIFICATION_ID = 10001
    }
}
