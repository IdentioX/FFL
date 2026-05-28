package ru.ffl24.zakaz.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.navigation.YandexRouteNavigator
import ru.ffl24.zakaz.ui.theme.AppDarkBackground
import ru.ffl24.zakaz.ui.theme.AppGray
import ru.ffl24.zakaz.ui.theme.AppNotReady
import ru.ffl24.zakaz.ui.theme.AppOnPrimary
import ru.ffl24.zakaz.ui.theme.AppPrimary
import ru.ffl24.zakaz.ui.theme.AppReady
import ru.ffl24.zakaz.ui.theme.AppSubmitted

@Composable
fun DriverOrderCard(
    order: DriverOrderDto,
    onOpenDetails: (DriverOrderDto) -> Unit,
    onRouteOpened: (DriverOrderDto) -> Unit,
    onMarkDelivered: (DriverOrderDto) -> Unit,
    isMarkingDelivered: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeliveredConfirmDialog by rememberSaveable(order.orderId) { mutableStateOf(false) }
    var confirmCountdown by rememberSaveable(order.orderId) { mutableIntStateOf(5) }
    val mainStatus = if (order.isReady) {
        OrderStatus.Ready
    } else {
        OrderStatus.NotReady
    }
    val routePreview = order.routeText
        ?.takeIf { it.isNotBlank() }
        ?: order.clientInfo.takeIf { it.isNotBlank() }
        ?: order.deliveryNote
        ?: "Адрес не указан"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetails(order) },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (order.isDelivered) 0.dp else 6.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (order.isDelivered) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ReadinessBadge(
                        label = if (order.isReady) "Готово" else "Не готово",
                        status = mainStatus
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    OrderNumberBadge(
                        orderNumber = order.orderNumber,
                        modifier = Modifier.offset(x = (-10).dp)
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    InfoBadge(
                        label = "Накладных: ${order.invoicesCount}"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = if (order.isDelivered) AppSubmitted else AppPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = order.displayTitle.ifBlank { order.orderNumber },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (order.isDelivered) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = routePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (YandexRouteNavigator.openRoute(context, order)) {
                            onRouteOpened(order)
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPrimary,
                        contentColor = AppOnPrimary
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
                        fontWeight = FontWeight.Black
                    )
                }

                DeliveredButton(
                    isReady = order.isReady,
                    isDelivered = order.isDelivered,
                    canMarkDelivered = order.canMarkDelivered,
                    isLoading = isMarkingDelivered,
                    onClick = { showDeliveredConfirmDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                )
            }
        }
    }

    if (showDeliveredConfirmDialog) {
        LaunchedEffect(order.orderId) {
            confirmCountdown = 5
            while (confirmCountdown > 0) {
                delay(1_000)
                confirmCountdown -= 1
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!isMarkingDelivered) {
                    showDeliveredConfirmDialog = false
                }
            },
            title = { Text("Сдать заказ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (confirmCountdown > 0) {
                            "Подтвердить можно через $confirmCountdown сек."
                        } else {
                            "Можно подтвердить сдачу заказа."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (confirmCountdown > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text("После подтверждения заказ будет отмечен как сданный и перемещён вниз списка.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeliveredConfirmDialog = false
                        onMarkDelivered(order)
                    },
                    enabled = !isMarkingDelivered && confirmCountdown == 0
                ) {
                    Text(if (confirmCountdown > 0) "Сдал ($confirmCountdown)" else "Сдал")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeliveredConfirmDialog = false },
                    enabled = !isMarkingDelivered
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DeliveredButton(
    isReady: Boolean,
    isDelivered: Boolean,
    canMarkDelivered: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = isReady && !isDelivered && canMarkDelivered && !isLoading
    val contentDescription = if (isDelivered) {
        "Заказ уже сдан"
    } else if (!isReady) {
        "Заказ не готов"
    } else {
        "Отметить заказ как сданный"
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = deliveredButtonContainerColor(),
            contentColor = deliveredButtonContentColor(),
            disabledContainerColor = DeliveredDisabledContainer,
            disabledContentColor = DeliveredDisabledContent
        ),
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        Text(
            text = if (isDelivered) "Сдано" else "Сдать",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReadinessBadge(
    label: String,
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = status.containerColor,
        contentColor = status.contentColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class OrderStatus(
    val containerColor: Color,
    val contentColor: Color
) {
    Ready(AppReady, Color.White),
    NotReady(AppNotReady, Color.White)
}

@Composable
private fun deliveredButtonContainerColor(): Color {
    return if (MaterialTheme.colorScheme.background == AppDarkBackground) {
        DeliveredButtonContainerDark
    } else {
        DeliveredButtonContainerLight
    }
}

@Composable
private fun deliveredButtonContentColor(): Color {
    return if (MaterialTheme.colorScheme.background == AppDarkBackground) {
        DeliveredButtonContentDark
    } else {
        DeliveredButtonContentLight
    }
}

private val DeliveredButtonContainerLight = Color(0xFFD1FAE5)
private val DeliveredButtonContainerDark = Color(0xFF064E3B)
private val DeliveredButtonContentLight = Color(0xFF047857)
private val DeliveredButtonContentDark = Color(0xFFA7F3D0)
private val DeliveredDisabledContainer = AppGray
private val DeliveredDisabledContent = AppSubmitted
