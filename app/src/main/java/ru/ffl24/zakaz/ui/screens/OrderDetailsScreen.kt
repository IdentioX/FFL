package ru.ffl24.zakaz.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ffl24.zakaz.model.OrderDetailsDto
import ru.ffl24.zakaz.model.PreviousDriverDto
import ru.ffl24.zakaz.navigation.YandexRouteNavigator
import ru.ffl24.zakaz.ui.components.OrderNumberBadge
import ru.ffl24.zakaz.ui.theme.AppGray
import ru.ffl24.zakaz.ui.theme.AppNotReady
import ru.ffl24.zakaz.ui.theme.AppOnPrimary
import ru.ffl24.zakaz.ui.theme.AppPrimary
import ru.ffl24.zakaz.ui.theme.AppReady
import ru.ffl24.zakaz.ui.theme.AppSubmitted
import ru.ffl24.zakaz.viewmodel.OrderDetailsViewModel

@Composable
fun OrderDetailsScreen(
    orderId: Int,
    viewModel: OrderDetailsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(orderId) {
        viewModel.loadDetails(orderId)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoading && state.details == null -> DetailsLoadingState()
            state.errorMessage != null && state.details == null -> DetailsErrorState(
                message = state.errorMessage ?: "Не удалось загрузить заказ",
                onRetry = viewModel::retry
            )
            state.details != null -> DetailsContent(
                details = state.details ?: return@Box,
                isDelivering = state.isDelivering,
                onRouteClick = { details ->
                    YandexRouteNavigator.openRoute(
                        context = context,
                        routeText = details.routeText,
                        orderId = details.orderId
                    )
                },
                onMarkDelivered = viewModel::markDelivered
            )
        }
    }
}

@Composable
private fun DetailsLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = AppPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Загрузка заказа...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailsErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DetailsCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Не удалось загрузить заказ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPrimary,
                        contentColor = AppOnPrimary
                    )
                ) {
                    Text("Повторить")
                }
            }
        }
    }
}

@Composable
private fun DetailsContent(
    details: OrderDetailsDto,
    isDelivering: Boolean,
    onRouteClick: (OrderDetailsDto) -> Unit,
    onMarkDelivered: () -> Unit
) {
    var showDeliveredConfirmDialog by rememberSaveable(details.orderId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    OrderNumberBadge(orderNumber = details.orderNumber)
                }
            }

            item {
                DetailSection(
                    title = "Название",
                    body = details.title.ifBlank { details.orderNumber }
                )
            }

            item {
                DetailSection(
                    title = "Адрес",
                    body = details.address?.takeIf { it.isNotBlank() } ?: "Адрес не найден"
                )
            }

            item {
                DetailSection(
                    title = "Время",
                    body = details.deliveryTime?.takeIf { it.isNotBlank() } ?: "Не указано"
                )
            }

            item {
                DetailSection(
                    title = "Комментарий",
                    body = details.comment?.takeIf { it.isNotBlank() } ?: "Нет комментария"
                )
            }

            item {
                InvoicesSection(details = details)
            }

            item {
                PreviousDriversSection(previousDrivers = details.previousDrivers)
            }
        }

        BottomActions(
            details = details,
            isDelivering = isDelivering,
            onRouteClick = { onRouteClick(details) },
            onDeliveredClick = { showDeliveredConfirmDialog = true }
        )
    }

    if (showDeliveredConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDelivering) {
                    showDeliveredConfirmDialog = false
                }
            },
            title = { Text("Сдать заказ?") },
            text = { Text("После подтверждения заказ будет отмечен как сданный.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeliveredConfirmDialog = false
                        onMarkDelivered()
                    },
                    enabled = !isDelivering
                ) {
                    Text("Сдал")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeliveredConfirmDialog = false },
                    enabled = !isDelivering
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    body: String
) {
    DetailsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InvoicesSection(details: OrderDetailsDto) {
    DetailsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Накладные",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Накладных: ${details.invoicesCount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ReadinessBadge(
                label = details.readinessLabel,
                isReady = details.isReady
            )
            Text(
                text = details.quantityLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviousDriversSection(previousDrivers: List<PreviousDriverDto>) {
    DetailsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Кто возил ранее",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            if (previousDrivers.isEmpty()) {
                Text(
                    text = "Нет данных",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                previousDrivers.take(5).forEachIndexed { index, previousDriver ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    PreviousDriverRow(previousDriver = previousDriver)
                }
            }
        }
    }
}

@Composable
private fun PreviousDriverRow(previousDriver: PreviousDriverDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = previousDriver.driverName.ifBlank { "Водитель не указан" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = previousDriver.deliveryDate.ifBlank { "Дата не указана" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BottomActions(
    details: OrderDetailsDto,
    isDelivering: Boolean,
    onRouteClick: () -> Unit,
    onDeliveredClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRouteClick,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Открыть маршрут"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Маршрут",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val deliveredEnabled = !details.isDelivered &&
                details.isReady &&
                details.canMarkDelivered &&
                !isDelivering

            Button(
                onClick = onDeliveredClick,
                enabled = deliveredEnabled,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPrimary,
                    contentColor = AppOnPrimary,
                    disabledContainerColor = AppGray,
                    disabledContentColor = AppSubmitted
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = if (details.isDelivered) "Сдано" else "Сдал",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReadinessBadge(
    label: String,
    isReady: Boolean
) {
    Surface(
        color = if (isReady) AppReady else AppNotReady,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailsCard(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}
