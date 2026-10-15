package id.dompet.pribadi.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.dompet.pribadi.data.AppDatabase
import id.dompet.pribadi.data.AppGraph
import id.dompet.pribadi.data.SecretStore
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class DiscordNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_TRANSACTION_ID = "transaction_id"
        const val KEY_TRANSFER_ID = "transfer_id"
    }

    override suspend fun doWork(): Result {
        inputData.getString(KEY_TRANSFER_ID)?.let { return doTransferWork(it) }
        val id = inputData.getString(KEY_TRANSACTION_ID) ?: return Result.failure()
        val db = AppGraph.database(applicationContext)
        val outbox = db.outboxDao().find(id) ?: return Result.success()
        if (outbox.state == "SENT") return Result.success()
        if (db.outboxDao().markSending(id, System.currentTimeMillis()) == 0) return Result.success()

        val config = db.settingsDao().get()
        val webhook = SecretStore(applicationContext).getWebhook()
        if (config?.discordEnabled != true || webhook.isNullOrBlank() || !isDiscordWebhook(webhook)) {
            db.outboxDao().markError(id, "FAILED", "WEBHOOK_NOT_CONFIGURED")
            return Result.failure()
        }
        val row = db.queryTransactionForDiscord(id) ?: run {
            db.outboxDao().markError(id, "FAILED", "TRANSACTION_NOT_FOUND")
            return Result.failure()
        }
        return try {
            val connection = (URL(webhook).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = discordPayload(row, outbox.balanceAfterIdr).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            when {
                code in 200..299 -> {
                    db.outboxDao().markSent(id, System.currentTimeMillis())
                    Result.success()
                }
                code == 429 || code >= 500 -> {
                    db.outboxDao().markPending(id, "HTTP_$code")
                    Result.retry()
                }
                else -> {
                    db.outboxDao().markError(id, "FAILED", "HTTP_$code")
                    Result.failure()
                }
            }
        } catch (_: Exception) {
            db.outboxDao().markPending(id, "NETWORK_ERROR")
            Result.retry()
        }
    }

    private suspend fun doTransferWork(id: String): Result {
        val db = AppGraph.database(applicationContext)
        val outbox = db.transferOutboxDao().find(id) ?: return Result.success()
        if (outbox.state == "SENT") return Result.success()
        if (db.transferOutboxDao().markSending(id, System.currentTimeMillis()) == 0) return Result.success()

        val config = db.settingsDao().get()
        val webhook = SecretStore(applicationContext).getWebhook()
        if (config?.discordEnabled != true || webhook.isNullOrBlank() || !isDiscordWebhook(webhook)) {
            db.transferOutboxDao().markError(id, "FAILED", "WEBHOOK_NOT_CONFIGURED")
            return Result.failure()
        }
        val transfer = db.cashTransferDao().find(id) ?: run {
            db.transferOutboxDao().markError(id, "FAILED", "TRANSFER_NOT_FOUND")
            return Result.failure()
        }
        return try {
            val connection = (URL(webhook).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = transferPayload(transfer.fromWallet, transfer.amountIdr, transfer.occurredAtEpochMs, outbox.balanceAfterIdr).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            when {
                code in 200..299 -> {
                    db.transferOutboxDao().markSent(id, System.currentTimeMillis())
                    Result.success()
                }
                code == 429 || code >= 500 -> {
                    db.transferOutboxDao().markPending(id, "HTTP_$code")
                    Result.retry()
                }
                else -> {
                    db.transferOutboxDao().markError(id, "FAILED", "HTTP_$code")
                    Result.failure()
                }
            }
        } catch (_: Exception) {
            db.transferOutboxDao().markPending(id, "NETWORK_ERROR")
            Result.retry()
        }
    }

    private fun transferPayload(fromWallet: String, amount: Long, occurredAt: Long, balance: Long): JSONObject {
        val idr = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }
        val toWallet = if (fromWallet == "CASH") "BALANCE" else "CASH"
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(occurredAt))
        val fields = JSONArray()
            .put(embedField("Jumlah", idr.format(amount)))
            .put(embedField("Dari", if (fromWallet == "CASH") "Tunai" else "Saldo non-tunai"))
            .put(embedField("Ke", if (toWallet == "CASH") "Tunai" else "Saldo non-tunai"))
            .put(embedField("Saldo total", idr.format(balance)))
            .put(embedField("Waktu", date))
        val embed = JSONObject()
            .put("title", "🔄 Transfer antar saldo")
            .put("color", 0x1565C0)
            .put("fields", fields)
            .put("footer", JSONObject().put("text", "Dompet Pribadi • IDR"))
        return JSONObject().put("embeds", JSONArray().put(embed))
            .put("allowed_mentions", JSONObject().put("parse", JSONArray()))
    }

    private fun isDiscordWebhook(value: String): Boolean = try {
        val url = URL(value)
        url.protocol == "https" && url.host in setOf("discord.com", "discordapp.com") && url.path.startsWith("/api/webhooks/")
    } catch (_: Exception) { false }

    private fun discordPayload(row: DiscordRow, balance: Long): JSONObject {
        val idr = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(row.occurredAtEpochMs))
        val isIncome = row.type == "INCOME"
        val fields = JSONArray()
            .put(embedField("Jumlah", idr.format(row.amountIdr)))
            .put(embedField("Saldo terbaru", idr.format(balance)))
            .put(embedField("Kategori", row.categoryName.take(256)))
            .put(embedField("Sumber dana", if (row.wallet == "CASH") "Tunai" else "Saldo non-tunai"))
            .put(embedField("Waktu", date))
        row.note?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            fields.put(embedField("Catatan", note.take(1024)))
        }

        val embed = JSONObject()
            .put("title", if (isIncome) "💰 Pemasukan baru" else "💸 Pengeluaran baru")
            .put("color", if (isIncome) 0x2E7D32 else 0xC62828)
            .put("fields", fields)
            .put("footer", JSONObject().put("text", "Dompet Pribadi • IDR"))

        return JSONObject()
            .put("embeds", JSONArray().put(embed))
            // Catatan/kategori dapat berisi teks seperti @everyone; jangan biarkan webhook memicu mention.
            .put("allowed_mentions", JSONObject().put("parse", JSONArray()))
    }

    private fun embedField(name: String, value: String): JSONObject =
        JSONObject().put("name", name).put("value", value.ifBlank { "—" }).put("inline", true)
}

private suspend fun AppDatabase.queryTransactionForDiscord(id: String): DiscordRow? =
    openHelper.readableDatabase.query(
        "SELECT t.type, t.amountIdr, c.name, t.wallet, t.occurredAtEpochMs, t.note FROM transactions t JOIN categories c ON c.id=t.categoryId WHERE t.id=?",
        arrayOf(id),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else DiscordRow(cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getString(5))
    }

private data class DiscordRow(val type: String, val amountIdr: Long, val categoryName: String, val wallet: String, val occurredAtEpochMs: Long, val note: String?)
