package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.f7z.olas.ui.theme.OlasColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    // NMP-GAP(#11): Raw event decoding must be replaced by a typed Rust search projection.
    // NMP-GAP(#9): PhotoPostParser decodes kind:20 events in Kotlin. Must be replaced by a typed Rust snapshot projection.
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        SearchBar(
            query        = query,
            onQueryChange = { query = it },
            onSearch     = { active = false },
            active       = active,
            onActiveChange = { active = it },
            modifier     = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder  = { Text("Search people, photos, tags", color = OlasColors.Text3) },
            colors       = SearchBarDefaults.colors(
                containerColor = OlasColors.Surface,
                dividerColor   = OlasColors.Border,
            ),
        ) {
            // Search results placeholder
            if (query.isNotBlank()) {
                Text(
                    text     = "No results for \"$query\"",
                    color    = OlasColors.Text2,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (!active) {
            DiscoverScreen()
        }
    }
}
