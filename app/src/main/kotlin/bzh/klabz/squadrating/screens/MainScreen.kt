package bzh.klabz.squadrating.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bzh.klabz.squadrating.calcYard
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datastores.userPreferencesDataStore
import io.hammerhead.karooext.KarooSystemService
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SquadratDrawRangeEnum(val radius: Int){
    LOAD_2(2),
    LOAD_3(3),
    LOAD_4(4),
    LOAD_5(5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    val exploredSquadratsStore by ctx.exploredSquadratsDataStore.data.collectAsStateWithLifecycle(null)
    val settingsStore by ctx.userPreferencesDataStore.data.collectAsStateWithLifecycle(null)

    var statshuntersDialogVisible by remember { mutableStateOf(false) }

    var downloadedActivities by remember { mutableIntStateOf(0) }
    var exploredSquadratsCount by remember { mutableIntStateOf(0) }
    var ubersquadratSize by remember { mutableIntStateOf(0) }
    var yard by remember { mutableIntStateOf(0) }
    var recentSquadratsCount by remember { mutableIntStateOf(0) }
    var recentNewSquadratsCount by remember { mutableIntStateOf(0) }

    var savedDialogVisible by remember { mutableStateOf(false) }
    var exitDialogVisible by remember { mutableStateOf(false) }
    var clearedRecentExploredSquadratsDialogVisible by remember { mutableStateOf(false) }
    var squadratLoadRange by remember { mutableStateOf("3") }
    var hideGrid by remember { mutableStateOf(false) }
    var isDisabled by remember { mutableStateOf(false) }
    var showActivityLines by remember { mutableStateOf(false) }

    LaunchedEffect(exploredSquadratsStore) {
        coroutineScope.launch {
            downloadedActivities = exploredSquadratsStore?.downloadedActivities ?: 0
            val exploredSquadrats = exploredSquadratsStore?.exploredSquadratsList?.map { Squadrat(it.x, it.y) }?.toSet()
            recentSquadratsCount = exploredSquadratsStore?.recentlyExploredSquadratsCount ?: 0
            recentNewSquadratsCount = exploredSquadratsStore?.recentlyExploredNewSquadratsCount ?: 0
            exploredSquadratsCount = exploredSquadrats?.size ?: 0
            // TODO: probably want to store yard in store, too..
            yard = if (exploredSquadrats == null) 0 else calcYard(exploredSquadrats)
            ubersquadratSize = exploredSquadratsStore?.biggestUbersquadratSize ?: 0
            hideGrid = settingsStore?.hideGridLines ?: false
            isDisabled = settingsStore?.isDisabled ?: false
            showActivityLines = settingsStore?.showActivityLines ?: false
        }
    }

    LaunchedEffect(settingsStore){
        coroutineScope.launch {
            val squadratDrawRange = settingsStore?.squadratDrawRange?.let { if(it == 0) 3 else it } ?: 3
            squadratLoadRange = "${squadratDrawRange.coerceIn(2..5)}"
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text("Squadrating") })
        Column(
            modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(4f)) {
                    Text("Activities:")
                    if (downloadedActivities > 0) {
                        Text("Squadrats:")
                        Text("Squadratinhos:")
                        Text("Yard:")
                        Text("Yardinho:")
                        Text("Ubersquadrat:")
                        Text("Ubersquadratinho:")
                        Text("Recent squadrats:")
                        Text("New squadrats:")
                        Text("New squadratinhos:")
                    }
                }
                Column(modifier = Modifier.weight(2f)) {
                    Text("$downloadedActivities")
                    if (downloadedActivities > 0) {
                        Text("$exploredSquadratsCount")
                        Text("0") // TODO squadratinhos
                        Text("$yard")
                        Text("0") // TODO yardinho
                        Text("${ubersquadratSize}x${ubersquadratSize}")
                        Text("0x0") // TODO ubersquadratinho
                        Text("$recentSquadratsCount")
                        Text("$recentNewSquadratsCount")
                        Text("0") // TODO squadratinhos
                    }
                }
            }

            if (exploredSquadratsStore?.isDownloading == true){
                Text("Loaded ${exploredSquadratsStore?.downloadedActivities ?: 0} activities...")
                LinearProgressIndicator()
            } else {
                val lastDownloadedAtTimestamp = exploredSquadratsStore?.lastDownloadedAt ?: 0
                val lastDownloadedAt = DateFormat.getDateTimeInstance().format(Date(lastDownloadedAtTimestamp))

                if (!exploredSquadratsStore?.lastDownloadError.isNullOrBlank()){
                    val atString = if (lastDownloadedAtTimestamp > 0) " at $lastDownloadedAt" else ""
                    Text("Error downloading activities: ${exploredSquadratsStore?.lastDownloadError}${atString}.")
                } else if ((exploredSquadratsStore?.downloadedActivities ?: 0) > 0 && lastDownloadedAtTimestamp > 0){
                    Text("Last successful download at ${lastDownloadedAt}.")
                } else {
                    Text("No activities downloaded yet.")
                }
            }

            if (exploredSquadratsStore?.isDownloading != true && !settingsStore?.statshuntersSharecode.isNullOrBlank()){
                FilledTonalButton(modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                    onClick = {
                        coroutineScope.launch {
                            ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                exploredSquadrats.toBuilder()
                                .setLastDownloadedAt(0)
                                .clearExploredSquadrats()
                                .clearRecentlyExploredSquadrats()
                                .clearRecentlyExploredNewSquadrats()
                                .setBiggestUbersquadratX(0)
                                .setBiggestUbersquadratY(0)
                                .setBiggestUbersquadratSize(0)
                                .build()
                            }
                        }
                    }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Update Squadrats")
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Force reload")
                }
            }

            if (recentSquadratsCount > 0 || recentNewSquadratsCount > 0) {
                FilledTonalButton(modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp), onClick = {

                    coroutineScope.launch {
                        ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                            exploredSquadrats.toBuilder()
                                .clearRecentlyExploredSquadrats()
                                .clearRecentlyExploredNewSquadrats()
                                .build()
                        }
                        clearedRecentExploredSquadratsDialogVisible = true
                    }
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "Reset recent squadrats")
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Reset recent squadrats")
                }
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
                onClick = {
                    statshuntersDialogVisible = true
                }) {
                Icon(Icons.Default.Person, contentDescription = "Connect StatsHunters")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Connect StatsHunters")
            }

            // TODO: same for squadratinhos
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = !isDisabled, onCheckedChange = { isDisabled = !it})
                Spacer(modifier = Modifier.width(10.dp))
                Text("Enable squadrat drawing")
            }

            if (!isDisabled){
                // TODO: same for squadratinhos
                apply {
                    val dropdownOptions = SquadratDrawRangeEnum.entries.toList().map { unit -> DropdownOption("${unit.radius}", "${unit.radius}") }
                    val dropdownInitialSelection by remember(squadratLoadRange) {
                        mutableStateOf(dropdownOptions.find { option -> option.id == squadratLoadRange } ?: dropdownOptions[0])
                    }
                    Dropdown(label = "Squadrat Draw Range", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                        squadratLoadRange = selectedOption.id
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = !hideGrid, onCheckedChange = { hideGrid = !it})
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Show grid")
                }

                // TODO: remove this feature?
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showActivityLines, onCheckedChange = { showActivityLines = it})
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Show activity lines")
                }
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {

                coroutineScope.launch {
                    ctx.userPreferencesDataStore.updateData { preferences ->
                        preferences.toBuilder()
                            .setSquadratDrawRange(squadratLoadRange.toInt())
                            .setHideGridLines(hideGrid)
                            .setIsDisabled(isDisabled)
                            .setShowActivityLines(showActivityLines)
                            .build()
                    }
                    savedDialogVisible = true
                }
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {
                exitDialogVisible = true
            }) {
                Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = "Exit")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Exit")
            }

            if (exitDialogVisible) {
                AlertDialog(onDismissRequest = { exitDialogVisible = false },
                    confirmButton = { Button(onClick = {
                        onFinish()
                    }) { Text("Yes") } },
                    dismissButton = { Button(onClick = {
                        exitDialogVisible = false
                    }) { Text("No") } },
                    text = { Text("Do you really want to exit?") }
                )
            }

            if (savedDialogVisible){
                AlertDialog(onDismissRequest = { savedDialogVisible = false },
                    confirmButton = { Button(onClick = {
                        savedDialogVisible = false
                    }) { Text("OK") } },
                    text = { Text("Settings saved successfully.") }
                )
            }

            if (clearedRecentExploredSquadratsDialogVisible){
                AlertDialog(onDismissRequest = { savedDialogVisible = false },
                    confirmButton = { Button(onClick = {
                        clearedRecentExploredSquadratsDialogVisible = false
                    }) { Text("OK") } },
                    text = { Text("Recent squadrats cleared.") }
                )
            }

            if (statshuntersDialogVisible){
                Dialog(onDismissRequest = { statshuntersDialogVisible = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            var dialogEnteredSharecode by remember { mutableStateOf("") }
                            LaunchedEffect(Unit) {
                                coroutineScope.launch {
                                    dialogEnteredSharecode = ctx.userPreferencesDataStore.data.first().statshuntersSharecode
                                }
                            }

                            Text("Go to statshunters.com/share and create a link that shares your heatmap. Enter its sharing code below.")

                            Text(buildAnnotatedString {
                                append("Example: statshunters.com/share/")
                                withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                    append("010433475e27")
                                }
                            })

                            OutlinedTextField(
                                value = dialogEnteredSharecode,
                                onValueChange = { dialogEnteredSharecode = it },
                                label = { Text("Code") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            FilledTonalButton(modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp), onClick = {
                                    statshuntersDialogVisible = false

                                    coroutineScope.launch {
                                        var changedCode = false
                                        ctx.userPreferencesDataStore.updateData { preferences ->
                                            changedCode = preferences.statshuntersSharecode != dialogEnteredSharecode
                                            preferences.toBuilder().setStatshuntersSharecode(dialogEnteredSharecode).build()
                                        }
                                        if (changedCode) {
                                            ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                                // TODO: also clear data
                                                exploredSquadrats.toBuilder().setLastDownloadedAt(0).build()
                                            }
                                        }
                                    }
                            }) {
                                Icon(Icons.Default.Done, contentDescription = "OK")
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}
