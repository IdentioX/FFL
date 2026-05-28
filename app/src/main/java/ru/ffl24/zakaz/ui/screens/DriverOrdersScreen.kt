package ru.ffl24.zakaz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.ui.components.DriverOrderCard
import ru.ffl24.zakaz.ui.theme.AppNotReady
import ru.ffl24.zakaz.ui.theme.AppOnPrimary
import ru.ffl24.zakaz.ui.theme.AppPrimary
import ru.ffl24.zakaz.ui.theme.AppPrimaryDark
import ru.ffl24.zakaz.ui.theme.AppPrimarySoft
import ru.ffl24.zakaz.ui.theme.AppReady
import ru.ffl24.zakaz.ui.theme.AppSubmitted
import ru.ffl24.zakaz.viewmodel.DriverOrdersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreen(
    viewModel: DriverOrdersViewModel,
    onOpenDetails: (DriverOrderDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val deliveringOrderIds by viewModel.deliveringOrderIds.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            isLoading && orders.isEmpty() -> LoadingState()
            errorMessage != null && orders.isEmpty() -> ErrorState(onRetry = viewModel::refresh)
            orders.isEmpty() -> EmptyState()
            else -> OrdersList(
                orders = orders,
                errorMessage = errorMessage,
                onRetry = viewModel::refresh,
                onOpenDetails = onOpenDetails,
                onRouteOpened = viewModel::logRouteOpened,
                onMarkDelivered = viewModel::markDelivered,
                deliveringOrderIds = deliveringOrderIds
            )
        }
    }
}

@Composable
private fun OrdersList(
    orders: List<DriverOrderDto>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenDetails: (DriverOrderDto) -> Unit,
    onRouteOpened: (DriverOrderDto) -> Unit,
    onMarkDelivered: (DriverOrderDto) -> Unit,
    deliveringOrderIds: Set<Long>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            OrdersSummaryCard(orders = orders)
        }

        if (errorMessage != null) {
            item {
                InlineMessageCard(
                    onRetry = onRetry
                )
            }
        }

        items(
            items = orders,
            key = { it.orderId }
        ) { order ->
            DriverOrderCard(
                order = order,
                onOpenDetails = onOpenDetails,
                onRouteOpened = onRouteOpened,
                onMarkDelivered = onMarkDelivered,
                isMarkingDelivered = order.orderId in deliveringOrderIds
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = AppPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Загрузка заказов...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        MessageCard {
            StateMessage(
                title = "Заказов нет"
            )
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        MessageCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StateMessage(
                    title = "Не удалось загрузить заказы",
                    body = "Проверьте подключение и повторите."
                )
                RetryButton(onClick = onRetry)
            }
        }
    }
}

@Composable
private fun InlineMessageCard(
    onRetry: () -> Unit
) {
    MessageCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Не удалось обновить заказы",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            RetryButton(onClick = onRetry)
        }
    }
}

@Composable
private fun MessageCard(
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(22.dp)) {
            content()
        }
    }
}

@Composable
private fun RetryButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppPrimary,
            contentColor = AppOnPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = "Повторить",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun OrdersSummaryCard(
    orders: List<DriverOrderDto>
) {
    val stats = orders.toSummaryStats()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppPrimaryDark,
        contentColor = AppOnPrimary,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сводка за день",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppPrimarySoft,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = AppPrimary.copy(alpha = 0.35f),
                    contentColor = AppPrimarySoft,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Всего: ${stats.total}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryMetric(
                    value = stats.ready,
                    label = "Готово",
                    color = AppReady,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    value = stats.notReady,
                    label = "Не готово",
                    color = AppNotReady,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    value = stats.delivered,
                    label = "Сдано",
                    color = AppPrimarySoft,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stats.readyFraction)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(AppReady)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Готовность к погрузке",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppSubmitted
                    )
                    Text(
                        text = "Готово: ${stats.ready} из ${stats.total} (${stats.readyPercent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppReady,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    value: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppSubmitted,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private data class OrderSummaryStats(
    val total: Int,
    val ready: Int,
    val notReady: Int,
    val delivered: Int
) {
    val readyFraction: Float
        get() = if (total == 0) 0f else ready.toFloat() / total.toFloat()

    val readyPercent: Int
        get() = (readyFraction * 100).toInt()
}

private fun List<DriverOrderDto>.toSummaryStats(): OrderSummaryStats {
    return OrderSummaryStats(
        total = size,
        ready = count { it.isReady },
        notReady = count { !it.isReady },
        delivered = count { it.isDelivered }
    )
}

@Composable
private fun StateMessage(
    title: String,
    body: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
