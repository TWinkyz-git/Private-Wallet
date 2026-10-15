package id.dompet.pribadi

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.dompet.pribadi.data.CategoryEntity
import id.dompet.pribadi.data.TransactionWithDetails
import id.dompet.pribadi.ui.FinanceUiState
import id.dompet.pribadi.ui.FinanceViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: FinanceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FinanceApp(viewModel) }
    }
}

@Composable
private fun DompetTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val lightColors = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF18715A),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFFB7E4D1),
        background = androidx.compose.ui.graphics.Color(0xFFF6F8F5),
        surface = androidx.compose.ui.graphics.Color.White,
        error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    )
    val darkColors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF83D5B3),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF003829),
        secondary = androidx.compose.ui.graphics.Color(0xFFB7E4D1),
        background = androidx.compose.ui.graphics.Color(0xFF101210),
        surface = androidx.compose.ui.graphics.Color(0xFF1C1E1B),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE5E3DE),
        error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    )
    MaterialTheme(colorScheme = if (darkTheme) darkColors else lightColors, content = content)
}

@Composable
private fun FinanceApp(vm: FinanceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    DompetTheme(state.settings.darkTheme) { FinanceContent(vm, state) }
}

@Composable
private fun FinanceContent(vm: FinanceViewModel, state: FinanceUiState) {
    var tab by remember { mutableStateOf("Ringkasan") }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransactionWithDetails?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab != "Pengaturan") FloatingActionButton(onClick = { editing = null; showForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah transaksi")
            }
        },
        bottomBar = {
            NavigationBar {
                listOf("Ringkasan" to Icons.Default.Home, "Transaksi" to Icons.Default.List, "Pengaturan" to Icons.Default.Settings).forEach { (label, icon) ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        when (tab) {
            "Ringkasan" -> SummaryScreen(state, onAdd = { editing = null; showForm = true }, onSelect = { editing = it; showForm = true }, modifier = Modifier.padding(padding))
            "Transaksi" -> TransactionsScreen(state, onSelect = { editing = it; showForm = true }, onDelete = vm::delete, onRetry = vm::retry, modifier = Modifier.padding(padding))
            else -> SettingsScreen(state, vm, Modifier.padding(padding))
        }
    }
    if (showForm) TransactionForm(state, editing, onDismiss = { showForm = false }, onSave = { id, type, amount, category, time, note, wallet ->
        vm.save(id, type, amount, category, time, note, wallet); showForm = false
    })
}

@Composable
private fun SummaryScreen(state: FinanceUiState, onAdd: () -> Unit, onSelect: (TransactionWithDetails) -> Unit, modifier: Modifier = Modifier) {
    val toCash = state.cashTransfers.filter { it.fromWallet == "BALANCE" }.sumOf { it.amountIdr }
    val toBalance = state.cashTransfers.filter { it.fromWallet == "CASH" }.sumOf { it.amountIdr }
    val cashBalance = state.settings.openingCashIdr + state.transactions.filter { it.wallet == "CASH" }.sumOf { if (it.type == "INCOME") it.amountIdr else -it.amountIdr } + toCash - toBalance
    val accountBalance = state.settings.openingBalanceIdr + state.transactions.filter { it.wallet == "BALANCE" }.sumOf { if (it.type == "INCOME") it.amountIdr else -it.amountIdr } - toCash + toBalance
    val balance = accountBalance + cashBalance
    val idr = { value: Long -> formatIdr(value) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Dompet Pribadi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Saldo saat ini", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                    Spacer(Modifier.height(8.dp)); Text(idr(balance), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp)); Text("Ringkasan bulan ini", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Pemasukan", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)); Text(idr(state.monthTotals.income), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
                        Column { Text("Pengeluaran", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)); Text(idr(state.monthTotals.expense), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Saldo non-tunai", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)); Text(idr(accountBalance), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Tunai", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)); Text(idr(cashBalance), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        item { Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Catat transaksi") } }
        item { Text("Transaksi terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (historyItems(state).isEmpty()) item { EmptyState("Belum ada transaksi", "Tambahkan pemasukan, pengeluaran, atau transfer pertamamu.") }
        items(historyItems(state).take(5), key = { it.id }) { item -> TransactionRow(item, onClick = { if (item.type != "TRANSFER") onSelect(item) }) }
    }
}

@Composable
private fun TransactionsScreen(state: FinanceUiState, onSelect: (TransactionWithDetails) -> Unit, onDelete: (String) -> Unit, onRetry: (String) -> Unit, modifier: Modifier = Modifier) {
    var typeFilter by remember { mutableStateOf("SEMUA") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var periodFilter by remember { mutableStateOf("BULAN") }
    var categoryMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionWithDetails?>(null) }
    val monthStart = remember { Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val filtered = historyItems(state).filter { (typeFilter == "SEMUA" || it.type == typeFilter) && (categoryFilter == null || it.categoryId == categoryFilter) && (periodFilter == "SEMUA" || it.occurredAtEpochMs >= monthStart) }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Transaksi", Modifier.padding(top = 28.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SEMUA" to "Semua", "INCOME" to "Masuk", "EXPENSE" to "Keluar", "TRANSFER" to "Transfer").forEach { (value, label) ->
                FilterChip(selected = typeFilter == value, onClick = { typeFilter = value }, label = { Text(label) })
            }
            Box {
                FilterChip(selected = categoryFilter != null, onClick = { categoryMenu = true }, label = { Text(state.categories.firstOrNull { it.id == categoryFilter }?.name ?: "Kategori") })
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text("Semua kategori") }, onClick = { categoryFilter = null; categoryMenu = false })
                    state.categories.filter { it.isActive }.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryFilter = c.id; categoryMenu = false }) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = periodFilter == "BULAN", onClick = { periodFilter = "BULAN" }, label = { Text("Bulan ini") })
            FilterChip(selected = periodFilter == "SEMUA", onClick = { periodFilter = "SEMUA" }, label = { Text("Semua periode") })
        }
        if (filtered.isEmpty()) EmptyState("Tidak ada transaksi", "Coba ubah filter atau tambahkan transaksi.", Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { row ->
                TransactionRow(row, onClick = { if (row.type != "TRANSFER") onSelect(row) }, onDelete = if (row.type == "TRANSFER") null else ({ pendingDelete = row }), onRetry = { onRetry(row.id) })
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Hapus transaksi?") }, text = { Text("Transaksi ${formatIdr(item.amountIdr)} akan dihapus dari perangkat.") },
            confirmButton = { TextButton(onClick = { onDelete(item.id); pendingDelete = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } })
    }
}

@Composable
private fun TransactionRow(item: TransactionWithDetails, onClick: () -> Unit, onDelete: (() -> Unit)? = null, onRetry: (() -> Unit)? = null) {
    val income = item.type == "INCOME"
    val isTransfer = item.type == "TRANSFER"
    Card(Modifier.fillMaxWidth().clickable(enabled = !isTransfer, onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (isTransfer) MaterialTheme.colorScheme.secondary.copy(alpha = .45f) else if (income) MaterialTheme.colorScheme.secondary.copy(alpha = .45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = .65f)) {
                Text(if (isTransfer) "⇄" else if (income) "＋" else "−", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (isTransfer) "Transfer" else item.categoryName, fontWeight = FontWeight.SemiBold)
                Text(if (isTransfer) "${walletLabel(item.wallet)} → ${walletLabel(item.transferToWallet)}" else walletLabel(item.wallet), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(item.occurredAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.notificationState?.let { Text(notificationLabel(it), style = MaterialTheme.typography.labelSmall, color = notificationColor(it)) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (isTransfer) "" else if (income) "+" else "−"}${formatIdr(item.amountIdr)}", color = if (income || isTransfer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                if (onRetry != null && item.notificationState == "FAILED") TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { Text("Coba lagi") }
                if (onDelete != null) TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: FinanceUiState, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    var webhook by remember { mutableStateOf("") }
    var hasWebhook by remember { mutableStateOf(vm.hasWebhook()) }
    var showCategories by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Pengaturan", Modifier.padding(top = 24.dp, bottom = 18.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Tema aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pilih tampilan putih atau hitam.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !state.settings.darkTheme, onClick = { vm.setDarkTheme(false) }, label = { Text("Putih") })
                        FilterChip(selected = state.settings.darkTheme, onClick = { vm.setDarkTheme(true) }, label = { Text("Hitam") })
                    }
                } }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Notifikasi Discord", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${if (hasWebhook) "Webhook tersimpan" else "Webhook belum diatur"}", style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = state.settings.discordEnabled, onCheckedChange = vm::setDiscordEnabled)
                    }
                    if (state.settings.discordEnabled && !hasWebhook) {
                        Text("Webhook belum diatur. Transaksi tetap tersimpan, tetapi notifikasi Discord tidak akan dikirim.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(webhook, { webhook = it }, Modifier.fillMaxWidth(), label = { Text("URL webhook Discord") }, placeholder = { Text(if (hasWebhook) "Tersimpan aman · masukkan untuk mengganti" else "https://discord.com/api/webhooks/…") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val candidate = webhook.trim()
                            if (candidate.isBlank() || candidate.startsWith("https://discord.com/api/webhooks/") || candidate.startsWith("https://discordapp.com/api/webhooks/")) {
                                vm.saveWebhook(candidate); if (candidate.isNotBlank()) hasWebhook = true; webhook = ""
                            } else vm.saveWebhook(candidate)
                        }) { Text("Simpan webhook") }
                        if (hasWebhook) OutlinedButton(onClick = { vm.saveWebhook(""); hasWebhook = false }) { Text("Hapus") }
                    }
                    Text("Webhook adalah rahasia. Siapa pun yang memilikinya dapat mengirim pesan ke channel terkait. Pesan transaksi menyertakan catatan yang Anda masukkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${state.categories.count { it.isActive }} kategori aktif", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { showCategories = !showCategories }) { Text(if (showCategories) "Tutup" else "Kelola") }
                    }
                    if (showCategories) {
                        state.categories.forEach { category ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(category.name, Modifier.weight(1f).clickable { editingCategory = category }); Text(category.type.categoryLabel(), style = MaterialTheme.typography.labelSmall)
                                Switch(checked = category.isActive, onCheckedChange = { vm.setCategoryActive(category.id, it) })
                            }
                        }
                        TextButton(onClick = { showAddCategory = true }) { Icon(Icons.Default.Add, null); Text("Tambah kategori") }
                    }
                } }
            }
        }
    }
    if (showAddCategory) AddCategoryDialog(onDismiss = { showAddCategory = false }, onAdd = { name, type -> vm.addCategory(name, type); showAddCategory = false })
    editingCategory?.let { category -> EditCategoryDialog(category, onDismiss = { editingCategory = null }, onSave = { name, type -> vm.updateCategory(category.id, name, type); editingCategory = null }) }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BOTH") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kategori baru") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama kategori") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("BOTH" to "Semua", "INCOME" to "Masuk", "EXPENSE" to "Keluar").forEach { (value, label) ->
                    FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name, type) }) { Text("Tambah") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun EditCategoryDialog(category: CategoryEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var type by remember(category.id) { mutableStateOf(category.type) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ubah kategori") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama kategori") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("BOTH" to "Semua", "INCOME" to "Masuk", "EXPENSE" to "Keluar").forEach { (value, label) ->
                    FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, type) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun TransactionForm(state: FinanceUiState, editing: TransactionWithDetails?, onDismiss: () -> Unit, onSave: (String?, String, Long, String, Long, String?, String) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(editing?.type ?: "EXPENSE") }
    var amount by remember { mutableStateOf(editing?.amountIdr?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: state.categories.firstOrNull { it.isActive && (it.type == "BOTH" || it.type == type) }?.id.orEmpty()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var wallet by remember { mutableStateOf(editing?.wallet ?: "BALANCE") }
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAtEpochMs ?: System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    val validCategories = state.categories.filter { (it.isActive || it.id == editing?.categoryId) && (it.type == "BOTH" || it.type == type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Tambah transaksi" else "Edit transaksi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = if (editing == null) listOf("EXPENSE" to "Keluar", "INCOME" to "Masuk", "TRANSFER" to "Transfer") else listOf("EXPENSE" to "Keluar", "INCOME" to "Masuk")
                    types.forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = {
                            type = value
                            if (value != "TRANSFER") categoryId = state.categories.firstOrNull { it.isActive && (it.type == "BOTH" || it.type == value) }?.id.orEmpty()
                        }, label = { Text(label) })
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Jumlah (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = wallet == "BALANCE", onClick = { wallet = "BALANCE" }, label = { Text(if (type == "TRANSFER") "Dari saldo" else "Saldo non-tunai") })
                    FilterChip(selected = wallet == "CASH", onClick = { wallet = "CASH" }, label = { Text(if (type == "TRANSFER") "Dari tunai" else "Tunai") })
                }
                if (type == "TRANSFER") {
                    Text("Ke ${if (wallet == "BALANCE") "tunai" else "saldo non-tunai"}. Transfer tidak dihitung sebagai pemasukan atau pengeluaran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(validCategories.firstOrNull { it.id == categoryId }?.name ?: "Pilih kategori") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            validCategories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; expanded = false }) }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val current = Calendar.getInstance().apply { timeInMillis = occurredAt }
                    DatePickerDialog(context, { _, y, m, d ->
                        val date = Calendar.getInstance().apply { timeInMillis = occurredAt; set(y, m, d) }
                        TimePickerDialog(context, { _, hour, minute -> date.set(Calendar.HOUR_OF_DAY, hour); date.set(Calendar.MINUTE, minute); occurredAt = date.timeInMillis }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Waktu: ${formatDate(occurredAt)}") }
                if (type != "TRANSFER") OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") }, minLines = 2, maxLines = 3)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val value = amount.toLongOrNull()
            if (value == null || value <= 0) validation = "Masukkan jumlah lebih dari Rp0"
                    else if (type != "TRANSFER" && (categoryId.isBlank() || validCategories.none { it.id == categoryId })) validation = "Pilih kategori terlebih dahulu"
            else onSave(editing?.id, type, value, categoryId, occurredAt, note.ifBlank { null }, wallet)
        }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun historyItems(state: FinanceUiState): List<TransactionWithDetails> =
    (state.transactions + state.cashTransfers.map { transfer ->
        TransactionWithDetails(
            id = "transfer-${transfer.id}",
            type = "TRANSFER",
            amountIdr = transfer.amountIdr,
            categoryId = "__transfer__",
            categoryName = "Transfer",
            wallet = transfer.fromWallet,
            transferToWallet = if (transfer.fromWallet == "CASH") "BALANCE" else "CASH",
            occurredAtEpochMs = transfer.occurredAtEpochMs,
            note = null,
            notificationState = transfer.notificationState,
        )
    }).sortedByDescending { it.occurredAtEpochMs }

private fun walletLabel(wallet: String): String = if (wallet == "CASH") "Tunai" else "Saldo non-tunai"

private fun formatIdr(amount: Long): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }.format(amount)
private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))
private fun notificationLabel(state: String): String = when (state) { "PENDING", "SENDING" -> "Discord · Menunggu"; "SENT" -> "Discord · Terkirim"; else -> "Discord · Gagal" }
@Composable
private fun notificationColor(state: String) = when (state) { "SENT" -> MaterialTheme.colorScheme.primary; "FAILED" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary }
private fun String.categoryLabel(): String = when (this) { "INCOME" -> "Masuk"; "EXPENSE" -> "Keluar"; else -> "Semua" }
