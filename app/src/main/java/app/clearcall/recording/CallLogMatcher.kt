package app.clearcall.recording

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import app.clearcall.core.AppPermissions
import app.clearcall.data.CallDirection

data class CallLogInfo(
    val number: String?,
    val name: String?,
    /** Null when the log entry's type is not a plain incoming/outgoing call. */
    val direction: CallDirection?,
)

/**
 * Android only writes the call log once a call ends, and does not reliably hand outgoing
 * numbers to broadcast receivers. So after a recording stops we look up the newest log entry
 * to learn who the call was with. Everything here needs the optional READ_CALL_LOG /
 * READ_CONTACTS permissions and degrades to "Unknown caller" without them.
 */
object CallLogMatcher {

    fun find(context: Context, startedAt: Long): CallLogInfo? {
        if (!AppPermissions.has(context, Manifest.permission.READ_CALL_LOG)) return null
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${CallLog.Calls.DATE} >= ?")
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf((startedAt - 90_000).toString()),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE),
                query,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val number = cursor.getString(0)?.takeIf { it.isNotBlank() }
                val cachedName = cursor.getString(1)?.takeIf { it.isNotBlank() }
                val direction = when (cursor.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                    else -> null
                }
                val name = cachedName ?: number?.let { ContactResolver.nameFor(context, it) }
                CallLogInfo(number, name, direction)
            }
        } catch (_: SecurityException) {
            null
        }
    }
}

object ContactResolver {
    fun nameFor(context: Context, number: String): String? {
        if (!AppPermissions.has(context, Manifest.permission.READ_CONTACTS)) return null
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
