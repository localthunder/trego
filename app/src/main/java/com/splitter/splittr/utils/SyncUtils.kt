import android.content.Context
import android.content.SharedPreferences

object SyncUtils {
    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    }

    const val STALE_DATA_THRESHOLD = 15 * 60 * 1000 // 15 minutes in milliseconds

    fun isDataStale(key: String): Boolean {
        val lastSync = getLastSyncTimestamp(key)
        return System.currentTimeMillis() - lastSync > STALE_DATA_THRESHOLD
    }

    fun getLastSyncTimestamp(key: String): Long {
        return prefs.getLong(key, 0)
    }

    fun updateLastSyncTimestamp(key: String) {
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }
}