package com.n3k0chan.spotter.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.TemplateWithExercises
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.IconButtonTone
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TemplatesViewModel : ViewModel() {
    val templates: StateFlow<List<TemplateWithExercises>> =
        ServiceLocator.templates.observeAll().stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
        )

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TemplatesViewModel() as T
            }
        }
    }
}

@Composable
fun TemplatesScreen(
    onCreateNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    vm: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val list by vm.templates.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Plantillas",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) },
                trailing = { SpotterIconButton(Icons.Filled.MoreVert, tone = IconButtonTone.Muted) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) {
                Text(
                    "Aún no tienes plantillas.",
                    style = SpotterText.body,
                    color = c.textMuted,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.template.id }) { tpl ->
                        TemplateCard(tpl, onClick = { onEdit(tpl.template.id) })
                    }
                }
            }
            Fab(onClick = onCreateNew, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun TemplateCard(tpl: TemplateWithExercises, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    SpotterCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tpl.template.name, style = SpotterText.title3, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${tpl.items.size} ejercicios",
                    style = SpotterText.small,
                    color = c.textMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Nuevo",
            tint = c.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}
