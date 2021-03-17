package the.waste.fellow.sms.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import the.waste.fellow.sms.constants.Constants
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*


object Helpers {

    fun getDate(milliSeconds: Long): String {
        val dateFormat = "dd/MM/yyyy"
        val formatter = SimpleDateFormat(dateFormat)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return formatter.format(calendar.time)
    }
}

fun Context.getChannel(senderNo: String): NotificationChannel? {
    val mNotifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mNotifyMgr.getNotificationChannel(senderNo)
    } else {
        null
    }
}

fun Context.createChannel(senderNo: String, message: String, notify: Boolean? = null, notifyImp: Int = NotificationManager.IMPORTANCE_DEFAULT): NotificationChannel? {
    val mNotifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(senderNo, senderNo, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = message
            importance = if (notify != null) {
                if (notify) {
                    notifyImp
                } else {
                    NotificationManager.IMPORTANCE_NONE
                }

            } else {
                getImportanceLevel(senderNo)
            }
            enableVibration(importance != NotificationManager.IMPORTANCE_NONE)
        }
        mNotifyMgr.createNotificationChannel(channel)
        return channel
    }
    return null
}

@SuppressLint("InlinedApi")
private fun getImportanceLevel(senderNo: String): Int {
    //by default we will not notify for anything
    //however we will notify based on preference
    return NotificationManager.IMPORTANCE_NONE
}
