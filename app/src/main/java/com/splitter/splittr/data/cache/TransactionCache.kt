import android.util.Log
import com.splitter.splittr.model.Transaction
import kotlinx.coroutines.withContext

// First, let's update TransactionCache to include timestamp and freshness check
object TransactionCache {
    private const val TAG = "TransactionCache"
    private const val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    private var transactions: List<Transaction>? = null
    private var lastUpdateTimestamp: Long = 0
    private var lastError: String? = null  // Add this line


    fun getTransactions(): List<Transaction>? {
        Log.d(TAG, "Getting all transactions from cache. Count: ${transactions?.size ?: 0}")
        return transactions
    }

    fun saveTransactions(transactions: List<Transaction>) {
        Log.d(TAG, "Saving ${transactions.size} transactions to cache")
        Log.d(TAG, "First transaction: ${transactions.firstOrNull()?.transactionId ?: "none"}")
        Log.d(TAG, "Last transaction: ${transactions.lastOrNull()?.transactionId ?: "none"}")
        this.transactions = transactions
        this.lastUpdateTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Successfully saved transactions to cache with timestamp: $lastUpdateTimestamp")
    }

    fun isCacheFresh(): Boolean {
        val isFresh = transactions != null &&
                System.currentTimeMillis() - lastUpdateTimestamp < CACHE_DURATION_MS
        Log.d(TAG, "Checking if cache is fresh: $isFresh")
        return isFresh
    }

    fun setError(error: String) {
        Log.e(TAG, "Setting cache error: $error")
        lastError = error
        // Optionally clear or keep stale data depending on your needs
        // transactions = null
        // lastUpdateTimestamp = 0
    }

    fun clearCache() {
        Log.d(TAG, "Clearing transaction cache")
        transactions = null
        lastUpdateTimestamp = 0
        Log.d(TAG, "Cache cleared successfully")
    }

    fun getCacheStatus(): String {
        val age = if (lastUpdateTimestamp > 0) {
            (System.currentTimeMillis() - lastUpdateTimestamp) / 1000
        } else 0

        return """
            Cache Status:
            Transactions: ${transactions?.size ?: 0}
            Last Updated: ${if (lastUpdateTimestamp > 0) "$age seconds ago" else "never"}
            Is Fresh: ${isCacheFresh()}
        """.trimIndent()
    }
}