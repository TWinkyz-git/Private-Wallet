package id.dompet.pribadi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.dompet.pribadi.data.AppGraph
import id.dompet.pribadi.data.AppSettingsEntity
import id.dompet.pribadi.data.CategoryEntity
import id.dompet.pribadi.data.TransactionWithDetails
import id.dompet.pribadi.data.Totals
import id.dompet.pribadi.data.CashTransferWithNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinanceUiState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val monthTotals: Totals = Totals(0, 0),
    val cashTransfers: List<CashTransferWithNotification> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppGraph.repository(application)
    private val _message = MutableStateFlow<String?>(null)
    private val financeState = combine(repository.transactions, repository.categories, repository.settings, repository.monthTotals(), repository.cashTransfers) { tx, cats, settings, totals, transfers ->
        FinanceUiState(tx, cats, settings ?: AppSettingsEntity(), totals, transfers, false)
    }
    val state = combine(financeState, _message) { finance, message -> finance.copy(message = message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    init { viewModelScope.launch { repository.recoverPending() } }

    fun save(id: String?, type: String, amount: Long, category: String, time: Long, note: String?, wallet: String) = viewModelScope.launch {
        try { repository.saveTransaction(id, type, amount, category, time, note, wallet); _message.value = if (type == "TRANSFER") "Transfer berhasil dicatat" else "Transaksi berhasil disimpan" }
        catch (e: Exception) { _message.value = e.message ?: "Transaksi belum dapat disimpan" }
    }
    fun delete(id: String) = viewModelScope.launch {
        try { repository.deleteTransaction(id); _message.value = "Transaksi dihapus" }
        catch (_: Exception) { _message.value = "Transaksi belum dapat dihapus" }
    }
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch {
        try {
            val current = repository.settingsValue() ?: AppSettingsEntity()
            repository.saveSettings(current.copy(darkTheme = enabled))
        } catch (_: Exception) { _message.value = "Tema tidak dapat disimpan" }
    }
    fun setDiscordEnabled(enabled: Boolean) = viewModelScope.launch {
        try { repository.setDiscordEnabled(enabled); _message.value = if (enabled) "Notifikasi Discord diaktifkan" else "Notifikasi Discord dinonaktifkan" }
        catch (_: Exception) { _message.value = "Pengaturan tidak dapat disimpan" }
    }
    fun saveWebhook(value: String) = viewModelScope.launch {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://discord.com/api/webhooks/") && !trimmed.startsWith("https://discordapp.com/api/webhooks/")) {
            _message.value = "URL webhook Discord tidak valid"; return@launch
        }
        try {
            repository.secrets.setWebhook(trimmed.ifBlank { null })
            _message.value = if (trimmed.isBlank()) "Webhook dihapus" else "Webhook disimpan dengan aman"
        } catch (_: Exception) { _message.value = "Webhook tidak dapat disimpan dengan aman di perangkat ini" }
    }
    fun hasWebhook(): Boolean = repository.secrets.hasWebhook()
    fun addCategory(name: String, type: String) = viewModelScope.launch {
        try { repository.addCategory(name, type); _message.value = "Kategori ditambahkan" }
        catch (_: Exception) { _message.value = "Kategori tidak dapat ditambahkan" }
    }
    fun setCategoryActive(id: String, active: Boolean) = viewModelScope.launch { repository.setCategoryActive(id, active) }
    fun updateCategory(id: String, name: String, type: String) = viewModelScope.launch {
        try { repository.updateCategory(id, name, type); _message.value = "Kategori diperbarui" }
        catch (_: Exception) { _message.value = "Kategori tidak dapat diperbarui" }
    }
    fun retry(id: String) = viewModelScope.launch { repository.retryNotification(id); _message.value = "Notifikasi dijadwalkan ulang" }
    fun clearMessage() { _message.value = null }
}
