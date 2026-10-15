package id.dompet.pribadi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isActive DESC, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Query("UPDATE categories SET name = :name, type = :type WHERE id = :id")
    suspend fun update(id: String, name: String, type: String)

    @Query("UPDATE categories SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun find(id: String): CategoryEntity?
}

@Dao
interface TransactionDao {
    @Query("""
        SELECT t.id, t.type, t.amountIdr, t.categoryId, c.name AS categoryName, t.wallet,
               'BALANCE' AS transferToWallet,
               t.occurredAtEpochMs, t.note, o.state AS notificationState
        FROM transactions t JOIN categories c ON c.id = t.categoryId
        LEFT JOIN notification_outbox o ON o.transactionId = t.id
        ORDER BY t.occurredAtEpochMs DESC
    """)
    fun observeAll(): Flow<List<TransactionWithDetails>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun find(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @androidx.room.Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountIdr ELSE 0 END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountIdr ELSE 0 END), 0) AS expense
        FROM transactions
    """)
    suspend fun allTotals(): Totals

    @Query("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amountIdr ELSE -amountIdr END),0) FROM transactions WHERE wallet=:wallet")
    suspend fun walletNet(wallet: String): Long

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountIdr ELSE 0 END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountIdr ELSE 0 END), 0) AS expense
        FROM transactions WHERE occurredAtEpochMs >= :start AND occurredAtEpochMs < :end
    """)
    fun observePeriodTotals(start: Long, end: Long): Flow<Totals>

    @Query("SELECT COUNT(*) FROM transactions WHERE occurredAtEpochMs >= :start AND occurredAtEpochMs < :end")
    fun observePeriodCount(start: Long, end: Long): Flow<Int>
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: NotificationOutboxEntity)

    @Query("UPDATE notification_outbox SET workId = :workId WHERE transactionId = :id")
    suspend fun setWorkId(id: String, workId: String)

    @Query("SELECT * FROM notification_outbox WHERE transactionId = :transactionId LIMIT 1")
    suspend fun find(transactionId: String): NotificationOutboxEntity?

    @Query("SELECT * FROM notification_outbox WHERE transactionId = :transactionId LIMIT 1")
    fun observe(transactionId: String): Flow<NotificationOutboxEntity?>

    @Query("UPDATE notification_outbox SET state = 'SENDING', attemptCount = attemptCount + 1, lastAttemptAtEpochMs = :now, lastErrorCode = NULL WHERE transactionId = :id AND state IN ('PENDING','SENDING','FAILED')")
    suspend fun markSending(id: String, now: Long): Int

    @Query("UPDATE notification_outbox SET state = 'SENT', sentAtEpochMs = :now, lastErrorCode = NULL WHERE transactionId = :id")
    suspend fun markSent(id: String, now: Long)

    @Query("UPDATE notification_outbox SET state = :state, lastErrorCode = :code WHERE transactionId = :id")
    suspend fun markError(id: String, state: String, code: String)

    @Query("UPDATE notification_outbox SET state = 'PENDING', lastErrorCode = :code WHERE transactionId = :id")
    suspend fun markPending(id: String, code: String)

    @Query("UPDATE notification_outbox SET state = 'PENDING', lastErrorCode = NULL WHERE transactionId = :id AND state = 'FAILED'")
    suspend fun retry(id: String): Int

    @Query("SELECT * FROM notification_outbox WHERE state IN ('PENDING','SENDING')")
    suspend fun pending(): List<NotificationOutboxEntity>

    @Query("UPDATE notification_outbox SET state = 'PENDING' WHERE state = 'SENDING'")
    suspend fun recoverSending()
}

@Dao
interface CashTransferDao {
    @Query("SELECT c.id, c.amountIdr, c.fromWallet, c.occurredAtEpochMs, c.createdAtEpochMs, o.state AS notificationState FROM cash_transfers c LEFT JOIN transfer_notification_outbox o ON o.transferId=c.id ORDER BY c.occurredAtEpochMs DESC")
    fun observeAll(): Flow<List<CashTransferWithNotification>>
    @Query("SELECT COALESCE(SUM(amountIdr),0) FROM cash_transfers WHERE fromWallet=:wallet")
    suspend fun totalSent(wallet: String): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: CashTransferEntity)
    @Query("SELECT * FROM cash_transfers WHERE id=:id LIMIT 1")
    suspend fun find(id: String): CashTransferEntity?
}

@Dao
interface TransferOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TransferNotificationOutboxEntity)
    @Query("UPDATE transfer_notification_outbox SET workId=:workId WHERE transferId=:id")
    suspend fun setWorkId(id: String, workId: String)
    @Query("SELECT * FROM transfer_notification_outbox WHERE transferId=:id LIMIT 1")
    suspend fun find(id: String): TransferNotificationOutboxEntity?
    @Query("UPDATE transfer_notification_outbox SET state='SENDING',attemptCount=attemptCount+1,lastAttemptAtEpochMs=:now,lastErrorCode=NULL WHERE transferId=:id AND state IN ('PENDING','SENDING','FAILED')")
    suspend fun markSending(id: String, now: Long): Int
    @Query("UPDATE transfer_notification_outbox SET state='SENT',sentAtEpochMs=:now,lastErrorCode=NULL WHERE transferId=:id")
    suspend fun markSent(id: String, now: Long)
    @Query("UPDATE transfer_notification_outbox SET state=:state,lastErrorCode=:code WHERE transferId=:id")
    suspend fun markError(id: String, state: String, code: String)
    @Query("UPDATE transfer_notification_outbox SET state='PENDING',lastErrorCode=:code WHERE transferId=:id")
    suspend fun markPending(id: String, code: String)
    @Query("UPDATE transfer_notification_outbox SET state='PENDING',lastErrorCode=NULL WHERE transferId=:id AND state='FAILED'")
    suspend fun retry(id: String): Int
    @Query("SELECT * FROM transfer_notification_outbox WHERE state IN ('PENDING','SENDING')")
    suspend fun pending(): List<TransferNotificationOutboxEntity>
    @Query("UPDATE transfer_notification_outbox SET state='PENDING' WHERE state='SENDING'")
    suspend fun recoverSending()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: AppSettingsEntity)
}
