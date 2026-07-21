package the.waste.fellow.sms.constants

import android.net.Uri


object SmsContract {
    /** Incoming messages + the main conversation list. */
    @JvmField
    val INBOX_URI: Uri = Uri.parse("content://sms/inbox")

    /** Sent messages written by this (default) app. */
    @JvmField
    val SENT_URI: Uri = Uri.parse("content://sms/sent")

    /** All messages (inbound + outbound) — used to render a full conversation thread. */
    @JvmField
    val CONVERSATION_URI: Uri = Uri.parse("content://sms/")

    const val SMS_SELECTION = "address = ? "
    const val SMS_SELECTION_ID = "_id = ? "
    const val COLUMN_ID = "_id"
    const val SMS_SELECTION_SEARCH = "address LIKE ? OR body LIKE ?"
    const val SORT_DESC = "date DESC"
    const val SORT_ASC = "date ASC"
}
