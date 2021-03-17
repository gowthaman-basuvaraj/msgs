package the.waste.fellow.sms.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import java.util.*
import kotlin.collections.LinkedHashMap

class PersonLookup(private val context: Context) {

    companion object {
        private val nameCheck = Regex("[A-Z]{2}\\-[A-Z]{6}")
        private val numberCheck = Regex("[A-Z]{2}\\-[0-9]{6}")


        const val MAX_SIZE = 500
        val cache: MutableMap<String, LocalContact> = object : LinkedHashMap<String, LocalContact>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LocalContact>?): Boolean {
                return size > MAX_SIZE
            }
        }
    }

    fun lookupPerson(address: String?): LocalContact? {


        if (address.isNullOrEmpty()) {
            return null
        }



        val input = address.toUpperCase(Locale.ROOT)
        val senderNo = if (nameCheck.matches(input) || numberCheck.matches(input)) {
            address.split("-").last().toUpperCase(Locale.ROOT)
        } else {
            address.toUpperCase(Locale.ROOT)
        }
        Log.w("LOOKUP", "$input -> $senderNo")

        val defLocalContact = LocalContact(name = senderNo, phone = address, normPhone = senderNo)

        if (address.length < 10) {
            return defLocalContact
        }



        return cache.getOrPut(address, {
            val name = getContactName(address)
            if (name.isNullOrEmpty()) defLocalContact
            else LocalContact(name, address, normPhone = senderNo)
        })
    }

    private fun getContactName(number: String?): String? {
        var c: Cursor? = null
        var cName: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val nameColumn = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            c = context.contentResolver.query(uri, nameColumn, null, null, null)
            cName = if (c == null || c.count == 0) {
                number
            } else {
                c.moveToFirst()
                c.getString(0)
            }
        } catch (e: Exception) {
            cName = number
        } finally {
            if (c != null && !c.isClosed) {
                c.close()
            }
        }
        return cName
    }

}

data class LocalContact(val name: String, val phone: String, val normPhone: String)
