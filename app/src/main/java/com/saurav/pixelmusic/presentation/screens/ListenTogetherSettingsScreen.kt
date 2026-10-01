package com.saurav.pixelmusic.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListenTogetherSettingsViewModel @Inject constructor(private val dataStore: DataStore<Preferences>) : ViewModel() {
    private object Keys {
        val COMPACT = booleanPreferencesKey("listen_together_compact_members")
        val ANIMATIONS = booleanPreferencesKey("listen_together_animated_reactions")
        val SOCIAL = booleanPreferencesKey("listen_together_show_social")
        val KEEP_ALIVE = booleanPreferencesKey("listen_together_keep_alive")
    }
    val compact = dataStore.data.map { it[Keys.COMPACT] ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val animations = dataStore.data.map { it[Keys.ANIMATIONS] ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val social = dataStore.data.map { it[Keys.SOCIAL] ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val keepAlive = dataStore.data.map { it[Keys.KEEP_ALIVE] ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun set(key: Preferences.Key<Boolean>, value: Boolean) { viewModelScope.launch { dataStore.edit { it[key] = value } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSettingsScreen(navController: NavController, vm: ListenTogetherSettingsViewModel = hiltViewModel()) {
    val compact by vm.compact.collectAsStateWithLifecycle()
    val animations by vm.animations.collectAsStateWithLifecycle()
    val social by vm.social.collectAsStateWithLifecycle()
    val keepAlive by vm.keepAlive.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Listen Together") }, navigationIcon = { IconButton({ navController.popBackStack() }) { Icon(Icons.Rounded.ArrowBack, "Back") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("Session experience", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 16.dp)) }
            item { Toggle("Compact member cards", "Use denser participant rows.", compact) { vm.set(booleanPreferencesKey("listen_together_compact_members"), it) } }
            item { Toggle("Animated reactions", "Animate reaction feedback.", animations) { vm.set(booleanPreferencesKey("listen_together_animated_reactions"), it) } }
            item { Toggle("Show chat & reactions", "Show the social section in the session sheet.", social) { vm.set(booleanPreferencesKey("listen_together_show_social"), it) } }
            item { Text("Background & connection", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 16.dp)) }
            item { Toggle("Keep session alive", "Keep the session connection active while Pixel Music is backgrounded.", keepAlive) { vm.set(booleanPreferencesKey("listen_together_keep_alive"), it) } }
            item { ListItem(headlineContent = { Text("Sync protection") }, supportingContent = { Text("Buffering, revision ordering, server-time alignment and host handoff are handled automatically.") }, leadingContent = { Icon(Icons.Rounded.Groups, null) }) }
        }
    }
}

@Composable private fun Toggle(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) }, trailingContent = { Switch(checked, onChange) })
}