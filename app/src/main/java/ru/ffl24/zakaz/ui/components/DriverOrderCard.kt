package ru.ffl24.zakaz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.navigation.YandexRouteNavigator

@Composable
fun DriverOrderCard(
    order: DriverOrderDto,
    onRouteOpened: (DriverOrderDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = when {
        order.isReady -> DriverOrderVisualState.Ready
        order.isPartiallyReady -> DriverOrderVisualState.Partial
        else -> DriverOrderVisualState.NotReady
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = state.containerColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                ReadinessBadge(label = order.readinessLabel, state = state)
            }

            Text(
                text = order.deliveryNote ?: "No delivery note",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Invoices: ${order.invoicesCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    if (YandexRouteNavigator.openRoute(context, order)) {
                        onRouteOpened(order)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Open route"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Route",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ReadinessBadge(
    label: String,
    state: DriverOrderVisualState
) {
    Surface(
        color = state.accentColor().copy(alpha = 0.16f),
        contentColor = state.accentColor(),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private enum class DriverOrderVisualState {
    Ready,
    Partial,
    NotReady
}

@Composable
private fun DriverOrderVisualState.containerColor() = when (this) {
    DriverOrderVisualState.Ready -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    DriverOrderVisualState.Partial -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    DriverOrderVisualState.NotReady -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
}

@Composable
private fun DriverOrderVisualState.accentColor() = when (this) {
    DriverOrderVisualState.Ready -> MaterialTheme.colorScheme.primary
    DriverOrderVisualState.Partial -> MaterialTheme.colorScheme.tertiary
    DriverOrderVisualState.NotReady -> MaterialTheme.colorScheme.error
}
