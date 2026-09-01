package com.mubashir.jarvis.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Reads the phone's contacts, so a name said out loud can become a number. */
class Contacts(private val context: Context) {

    fun canRead(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Every contact with a number. Read in one pass rather than querying per
     * name: the matching rules live in [ContactMatcher] where they can be
     * tested, and they need the whole book to know whether a name is ambiguous.
     */
    fun all(): List<Contact> {
        if (!canRead()) return emptyList()
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numberColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                if (nameColumn < 0 || numberColumn < 0) return emptyList()

                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)?.trim().orEmpty()
                        val number = cursor.getString(numberColumn)?.trim().orEmpty()
                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            add(Contact(name, number))
                        }
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
