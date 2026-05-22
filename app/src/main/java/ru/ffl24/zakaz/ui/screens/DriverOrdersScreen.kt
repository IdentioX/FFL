package ru.ffl24.zakaz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.ui.components.DriverOrderCard
import ru.ffl24.zakaz.viewmodel.DriverOrdersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreen(
    viewModel: DriverOrdersViewModel,
    modifier: Modifier = Modifier
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isLoading && orders.isEmpty() -> LoadingState()
            errorMessage != null && orders.isEmpty() -> ErrorState(
                message = errorMessage ?: "Unable to load orders.",
                onRetry = viewModel::refresh
            )
            orders.isEmpty() -> EmptyState()
            else -> OrdersList(
                orders = orders,
                errorMessage = errorMessage,
                onRetry = viewModel::refresh,
                onRouteOpened = viewModel::logRouteOpened
            )
        }
    }
}

@Composable
private fun OrdersList(
    orders: List<DriverOrderDto>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onRouteOpened: (DriverOrderDto) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (errorMessage != null) {
            item {
                ErrorBanner(
                    message = errorMessage,
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
                onRouteOpened = onRouteOpened
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    StateMessage(
        title = "No orders for today",
        body = "Pull to refresh or try again later."
    )
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StateMessage(
                title = "Could not load orders",
                body = message
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun StateMessage(
    title: String,
    body: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
