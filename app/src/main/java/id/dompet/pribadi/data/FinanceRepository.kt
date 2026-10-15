package id.dompet.pribadi.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import id.dompet.pribadi.notify.DiscordNotificationWorker
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

class FinanceRepository(
    private val context: Context,
    private val db: AppDatabase,
    val secrets: SecretStore,
) {
    val transactions = db.transactionDao().observeAll()
    val categories = db.categoryDao().observeAll()
    val settings = db.settingsDao().observe()
    val cashTransfers = db.cashTransferDao().observeAll()
    suspend fun settingsValue(): AppSettingsEntity? = db.settingsDao().get()
    suspend fun saveSettings(settings: AppSettingsEntity) = db.settingsDao().save(settings)
    private val workManager = WorkManager.getInstance(context)

    fun monthTotals(): Flow<Totals> {
        val now = Calendar.getInstance()
        now.set(Calendar.DAY_OF_MONTH, 1)
        now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
        val start = now.timeInMillis
        now.add(Calendar.MONTH, 1)
        return db.transactionDao().observePeriodTotals(start, now.timeInMillis)
    }

    suspend fun saveTransaction(
        id: String?, type: String, amount: Long, categoryId: String,
        occurredAt: Long, note: String?, wallet: String,
    ) {
        if (type == "TRANSFER") {
            require(id == null) { "Transfer tidak dapat diedit sebagai transaksi biasa" }
            transferFunds(amount, wallet, occurredAt)
            return
        }
        require(type == "INCOME" || type == "EXPENSE")
        require(amount > 0)
        require(wallet == "CASH" || wallet == "BALANCE")
        val transactionDao = db.transactionDao()
        var shouldSchedule = false
        var taskId: String? = null
        db.withTransaction {
            val category = db.categoryDao().find(categoryId) ?: error("Kategori tidak ditemukan")
            require(category.isActive && (category.type == "BOTH" || category.type == type))
            val now = System.currentTimeMillis()
            if (id == null) {
                val transaction = TransactionEntity(UUID.randomUUID().toString(), type, amount, categoryId, wallet, occurredAt, note?.trim()?.ifBlank { null }, now, now)
                transactionDao.insert(transaction)
                val config = db.settingsDao().get() ?: AppSettingsEntity()
                if (config.discordEnabled && secrets.hasWebhook()) {
                    val totals = transactionDao.allTotals()
                    val balance = config.openingBalanceIdr + config.openingCashIdr + totals.income - totals.expense
                    db.outboxDao().insert(NotificationOutboxEntity(transactionId = transaction.id, balanceAfterIdr = balance))
                    shouldSchedule = true
                    taskId = transaction.id
                }
            } else {
                val old = transactionDao.find(id) ?: error("Transaksi tidak ditemukan")
                transactionDao.update(old.copy(type = type, amountIdr = amount, categoryId = categoryId, wallet = wallet, occurredAtEpochMs = occurredAt, note = note?.trim()?.ifBlank { null }, updatedAtEpochMs = now))
            }
        }
        if (shouldSchedule && taskId != null) enqueue(taskId!!)
    }

    suspend fun deleteTransaction(id: String) = db.transactionDao().delete(id)

    suspend fun transferFunds(amount: Long, fromWallet: String, occurredAt: Long = System.currentTimeMillis()) {
        require(amount > 0)
        require(fromWallet == "BALANCE" || fromWallet == "CASH")
        var transferId: String? = null
        db.withTransaction {
            val settings = db.settingsDao().get() ?: AppSettingsEntity()
            val transfers = db.cashTransferDao()
            val walletNet = db.transactionDao().walletNet(fromWallet)
            val available = if (fromWallet == "BALANCE") {
                settings.openingBalanceIdr + walletNet - transfers.totalSent("BALANCE") + transfers.totalSent("CASH")
            } else {
                settings.openingCashIdr + walletNet - transfers.totalSent("CASH") + transfers.totalSent("BALANCE")
            }
            require(available >= amount) { if (fromWallet == "BALANCE") "Saldo non-tunai tidak cukup" else "Saldo tunai tidak cukup" }
            val now = System.currentTimeMillis()
            val transfer = CashTransferEntity(UUID.randomUUID().toString(), amount, fromWallet, occurredAt, now)
            transfers.insert(transfer)
            val config = db.settingsDao().get() ?: AppSettingsEntity()
            if (config.discordEnabled && secrets.hasWebhook()) {
                val totals = db.transactionDao().allTotals()
                val balance = config.openingBalanceIdr + config.openingCashIdr + totals.income - totals.expense
                db.transferOutboxDao().insert(TransferNotificationOutboxEntity(transferId = transfer.id, balanceAfterIdr = balance))
                transferId = transfer.id
            }
        }
        transferId?.let { enqueueTransfer(it) }
    }

    suspend fun setDiscordEnabled(enabled: Boolean) {
        val current = db.settingsDao().get() ?: AppSettingsEntity()
        db.settingsDao().save(current.copy(discordEnabled = enabled))
    }

    suspend fun addCategory(name: String, type: String) {
        require(name.isNotBlank())
        db.categoryDao().insert(CategoryEntity(UUID.randomUUID().toString(), name.trim(), type, sortOrder = 100))
    }

    suspend fun updateCategory(id: String, name: String, type: String) {
        require(name.isNotBlank())
        db.categoryDao().update(id, name.trim(), type)
    }

    suspend fun setCategoryActive(id: String, active: Boolean) = db.categoryDao().setActive(id, active)

    suspend fun retryNotification(id: String) {
        if (id.startsWith("transfer-")) {
            val transferId = id.removePrefix("transfer-")
            if (db.transferOutboxDao().retry(transferId) > 0) enqueueTransfer(transferId)
        } else if (db.outboxDao().retry(id) > 0) enqueue(id)
    }

    suspend fun recoverPending() {
        db.outboxDao().recoverSending()
        db.outboxDao().pending().forEach { enqueue(it.transactionId) }
        db.transferOutboxDao().recoverSending()
        db.transferOutboxDao().pending().forEach { enqueueTransfer(it.transferId) }
    }

    private suspend fun enqueue(id: String) {
        val request = OneTimeWorkRequestBuilder<DiscordNotificationWorker>()
            .setInputData(androidx.work.workDataOf(DiscordNotificationWorker.KEY_TRANSACTION_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork("discord-transaction-$id", ExistingWorkPolicy.KEEP, request)
        db.outboxDao().setWorkId(id, request.id.toString())
    }

    private suspend fun enqueueTransfer(id: String) {
        val request = OneTimeWorkRequestBuilder<DiscordNotificationWorker>()
            .setInputData(androidx.work.workDataOf(DiscordNotificationWorker.KEY_TRANSFER_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork("discord-transfer-$id", ExistingWorkPolicy.KEEP, request)
        db.transferOutboxDao().setWorkId(id, request.id.toString())
    }
}
