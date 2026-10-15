package id.dompet.pribadi.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, CashTransferEntity::class, NotificationOutboxEntity::class, TransferNotificationOutboxEntity::class, AppSettingsEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun cashTransferDao(): CashTransferDao
    abstract fun transferOutboxDao(): TransferOutboxDao
    abstract fun outboxDao(): OutboxDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        val defaultCategories = listOf(
            CategoryEntity("food", "Makanan", "EXPENSE", sortOrder = 0),
            CategoryEntity("transport", "Transportasi", "EXPENSE", sortOrder = 1),
            CategoryEntity("bills", "Tagihan", "EXPENSE", sortOrder = 2),
            CategoryEntity("shopping", "Belanja", "EXPENSE", sortOrder = 3),
            CategoryEntity("salary", "Gaji", "INCOME", sortOrder = 4),
            CategoryEntity("other", "Lainnya", "BOTH", sortOrder = 5),
        )
    }
}
