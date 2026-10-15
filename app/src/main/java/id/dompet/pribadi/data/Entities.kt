package id.dompet.pribadi.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** INCOME, EXPENSE, or BOTH */
    val type: String = "BOTH",
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId"), Index("occurredAtEpochMs"), Index("type")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountIdr: Long,
    val categoryId: String,
    /** CASH or BALANCE (saldo non-tunai) */
    val wallet: String = "BALANCE",
    val occurredAtEpochMs: Long,
    val note: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "cash_transfers", indices = [Index("occurredAtEpochMs")])
data class CashTransferEntity(
    @PrimaryKey val id: String,
    val amountIdr: Long,
    /** CASH or BALANCE: wallet that sends the amount */
    val fromWallet: String = "BALANCE",
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "transfer_notification_outbox",
    foreignKeys = [ForeignKey(entity = CashTransferEntity::class, parentColumns = ["id"], childColumns = ["transferId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("state")],
)
data class TransferNotificationOutboxEntity(
    @PrimaryKey val transferId: String,
    val state: String = "PENDING",
    val workId: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMs: Long? = null,
    val sentAtEpochMs: Long? = null,
    val lastErrorCode: String? = null,
    val balanceAfterIdr: Long,
)

data class CashTransferWithNotification(
    val id: String,
    val amountIdr: Long,
    val fromWallet: String,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val notificationState: String?,
)

@Entity(
    tableName = "notification_outbox",
    foreignKeys = [ForeignKey(
        entity = TransactionEntity::class,
        parentColumns = ["id"], childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["transactionId"], unique = true), Index("state")],
)
data class NotificationOutboxEntity(
    @PrimaryKey val transactionId: String,
    val state: String = "PENDING",
    val workId: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMs: Long? = null,
    val sentAtEpochMs: Long? = null,
    val lastErrorCode: String? = null,
    val balanceAfterIdr: Long,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val openingBalanceIdr: Long = 0L,
    val openingCashIdr: Long = 0L,
    val discordEnabled: Boolean = false,
    val darkTheme: Boolean = false,
)

data class TransactionWithDetails(
    val id: String,
    val type: String,
    val amountIdr: Long,
    val categoryId: String,
    val categoryName: String,
    val wallet: String,
    val transferToWallet: String,
    val occurredAtEpochMs: Long,
    val note: String?,
    val notificationState: String?,
)

data class Totals(
    val income: Long,
    val expense: Long,
)
