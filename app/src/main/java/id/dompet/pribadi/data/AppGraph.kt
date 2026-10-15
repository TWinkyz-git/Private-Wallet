package id.dompet.pribadi.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.sqlite.db.SupportSQLiteDatabase

object AppGraph {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN wallet TEXT NOT NULL DEFAULT 'BALANCE'")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN openingCashIdr INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS cash_transfers (id TEXT NOT NULL, amountIdr INTEGER NOT NULL, occurredAtEpochMs INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_transfers_occurredAtEpochMs ON cash_transfers(occurredAtEpochMs)")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cash_transfers ADD COLUMN fromWallet TEXT NOT NULL DEFAULT 'BALANCE'")
        }
    }
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS transfer_notification_outbox (transferId TEXT NOT NULL, state TEXT NOT NULL, workId TEXT, attemptCount INTEGER NOT NULL, lastAttemptAtEpochMs INTEGER, sentAtEpochMs INTEGER, lastErrorCode TEXT, balanceAfterIdr INTEGER NOT NULL, PRIMARY KEY(transferId), FOREIGN KEY(transferId) REFERENCES cash_transfers(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transfer_notification_outbox_state ON transfer_notification_outbox(state)")
        }
    }
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN darkTheme INTEGER NOT NULL DEFAULT 0")
        }
    }
    @Volatile private var database: AppDatabase? = null
    @Volatile private var financeRepository: FinanceRepository? = null

    fun database(context: Context): AppDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dompet-pribadi.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val created = database(context)
                        AppDatabase.defaultCategories.forEach { created.categoryDao().insert(it) }
                        created.settingsDao().save(AppSettingsEntity())
                    }
                }
            })
            .build().also { database = it }
    }

    fun repository(context: Context): FinanceRepository = financeRepository ?: synchronized(this) {
        financeRepository ?: FinanceRepository(context.applicationContext, database(context), SecretStore(context.applicationContext))
            .also { financeRepository = it }
    }
}
