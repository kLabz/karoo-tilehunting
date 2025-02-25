package bzh.klabz.squadrating.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.Squadratinho
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datastores.userPreferencesDataStore
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
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exploredSquadratsStore by ctx.exploredSquadratsDataStore.data.collectAsStateWithLifecycle(null)
    val settingsStore by ctx.userPreferencesDataStore.data.collectAsStateWithLifecycle(null)

    var statshuntersDialogVisible by remember { mutableStateOf(false) }

    var downloadedActivities by remember { mutableIntStateOf(0) }
    var exploredSquadratsCount by remember { mutableIntStateOf(0) }
    var exploredSquadratinhosCount by remember { mutableIntStateOf(0) }
    var ubersquadratSize by remember { mutableIntStateOf(0) }
    var ubersquadratinhoSize by remember { mutableIntStateOf(0) }
    var yard by remember { mutableIntStateOf(0) }
    var yardinho by remember { mutableIntStateOf(0) }
    var recentSquadratsCount by remember { mutableIntStateOf(0) }
    var recentNewSquadratsCount by remember { mutableIntStateOf(0) }
    var recentNewSquadratinhosCount by remember { mutableIntStateOf(0) }

    var savedDialogVisible by remember { mutableStateOf(false) }
    var clearedRecentExploredSquadratsDialogVisible by remember { mutableStateOf(false) }
    var squadratLoadRange by remember { mutableStateOf("3") }
    var hideSquadratGrid by remember { mutableStateOf(false) }
    var areSquadratsDisabled by remember { mutableStateOf(false) }
    var areSquadratinhosDisabled by remember { mutableStateOf(false) }

    var pageIndex by remember { mutableStateOf(0) }
    var squadratsTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(exploredSquadratsStore) {
        coroutineScope.launch {
            downloadedActivities = exploredSquadratsStore?.downloadedActivities ?: 0
            val exploredSquadrats = exploredSquadratsStore?.exploredSquadratsList?.map { Squadrat(it.x, it.y) }?.toSet()
            val exploredSquadratinhos = exploredSquadratsStore?.exploredSquadratinhosList?.map { Squadratinho(it.x, it.y) }?.toSet()
            recentSquadratsCount = exploredSquadratsStore?.recentlyExploredSquadratsCount ?: 0
            recentNewSquadratsCount = exploredSquadratsStore?.recentlyExploredNewSquadratsCount ?: 0
            exploredSquadratsCount = exploredSquadrats?.size ?: 0
            exploredSquadratinhosCount = exploredSquadratinhos?.size ?: 0
            recentNewSquadratinhosCount = exploredSquadratsStore?.recentlyExploredNewSquadratinhosCount ?: 0
            yard = exploredSquadratsStore?.yard ?: 0
            yardinho = exploredSquadratsStore?.yardinho ?: 0
            ubersquadratSize = exploredSquadratsStore?.biggestUbersquadratSize ?: 0
            ubersquadratinhoSize = exploredSquadratsStore?.biggestUbersquadratinhoSize ?: 0
            hideSquadratGrid = settingsStore?.hideSquadratGridLines ?: false
            areSquadratsDisabled = settingsStore?.areSquadratsDisabled ?: false
            areSquadratinhosDisabled = settingsStore?.areSquadratinhosDisabled ?: false
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
        TopAppBar(
            title = {
                Row(modifier = Modifier.padding(0.dp)) {
                    Image(
                        painterResource(id = R.drawable.logo_squadrats),
                        contentDescription = "Squadrating",
                        modifier = Modifier.width(30.dp).height(30.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SQUADRATING",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .padding(5.dp)
                .height(270.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (pageIndex) {
                // ===================================================
                // Data page
                // ===================================================
                0 -> {
                    Card(
                        // TODO: surely there's a shape that just doesn't have rounded corners..?
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp,
                        ),
                        colors = CardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = Color.White
                        )
                    ) {
                        TabRow(
                            selectedTabIndex = squadratsTabIndex,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Tab(
                                text = { Text(text = "Squadrats", fontSize = 12.sp) },
                                selectedContentColor = Color.White,
                                unselectedContentColor = MaterialTheme.colorScheme.primary,
                                selected = squadratsTabIndex == 0,
                                onClick = { squadratsTabIndex = 0 },
                                modifier = Modifier.background(if (squadratsTabIndex == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            )
                            Tab(
                                text = { Text(text = "Squadratinhos", fontSize = 12.sp) },
                                selectedContentColor = Color.White,
                                unselectedContentColor = MaterialTheme.colorScheme.primary,
                                selected = squadratsTabIndex == 1,
                                onClick = { squadratsTabIndex = 1 },
                                modifier = Modifier.background(if (squadratsTabIndex == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                            )
                        }

                        val lineHeight = 0.3.sp
                        @Composable fun spacer() { return Spacer(modifier = Modifier.width(4.dp)) }

                        when (squadratsTabIndex) {
                            0 -> {
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.squadrat), contentDescription = "Squadrat")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(8f)) {
                                        Text(text = "$exploredSquadratsCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "All", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(5f)) {
                                        Text("$recentSquadratsCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "Recent", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(5f)) {
                                        Text(text = "$recentNewSquadratsCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "New", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.yard), contentDescription = "Yard")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(8f)) {
                                        Text(text = "$yard", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "Yard", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(10f)) {
                                        if (recentSquadratsCount > 0 || recentNewSquadratsCount > 0 || recentNewSquadratinhosCount > 0) {
                                            FilledTonalButton(
                                                modifier = Modifier.height(20.dp).fillMaxWidth(),
                                                contentPadding = PaddingValues(vertical = 0.dp),
                                                onClick = {
                                                    coroutineScope.launch {
                                                        ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                                            exploredSquadrats.toBuilder()
                                                                .clearRecentlyExploredSquadrats()
                                                                .clearRecentlyExploredNewSquadrats()
                                                                .clearRecentlyExploredSquadratinhos()
                                                                .clearRecentlyExploredNewSquadratinhos()
                                                                .build()
                                                        }
                                                        clearedRecentExploredSquadratsDialogVisible = true
                                                    }
                                                }
                                            ) {
                                                Text("Reset recent", fontSize = 10.sp, lineHeight = 0.6.sp)
                                            }
                                        }
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.ubersquadrat), contentDescription = "Ubersquadrat")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(18f)) {
                                        Text(text = "${ubersquadratSize}x${ubersquadratSize}", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "Übersquadrat", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            1 -> {
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.squadratinho), contentDescription = "Squadratinho")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(8f)) {
                                        Text(text = "$exploredSquadratinhosCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "All", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(5f)) {
                                    //     Text("$recentSquadratinhosCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                    //     Text(text = "Recent", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(5f)) {
                                        Text(text = "$recentNewSquadratinhosCount", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "New", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.yardinho), contentDescription = "Yardinho")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(8f)) {
                                        Text(text = "$yardinho", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "Yardinho", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    Column(modifier = Modifier.weight(10f)) {
                                        if (recentSquadratsCount > 0 || recentNewSquadratsCount > 0 || recentNewSquadratinhosCount > 0) {
                                            FilledTonalButton(
                                                modifier = Modifier.height(20.dp).fillMaxWidth(),
                                                contentPadding = PaddingValues(vertical = 0.dp),
                                                onClick = {
                                                    coroutineScope.launch {
                                                        ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                                            exploredSquadrats.toBuilder()
                                                                .clearRecentlyExploredSquadrats()
                                                                .clearRecentlyExploredNewSquadrats()
                                                                .clearRecentlyExploredSquadratinhos()
                                                                .clearRecentlyExploredNewSquadratinhos()
                                                                .build()
                                                        }
                                                        clearedRecentExploredSquadratsDialogVisible = true
                                                    }
                                                }
                                            ) {
                                                Text("Reset recent", fontSize = 10.sp, lineHeight = 0.6.sp)
                                            }
                                        }
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    spacer()
                                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Center) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(painterResource(id = R.drawable.ubersquadratinho), contentDescription = "Ubersquadratinho")
                                    }
                                    spacer()
                                    Column(modifier = Modifier.weight(18f)) {
                                        Text(text = "${ubersquadratinhoSize}x${ubersquadratinhoSize}", fontWeight = FontWeight.Bold, lineHeight = lineHeight)
                                        Text(text = "Übersquadratinho", fontSize = 10.sp, lineHeight = lineHeight)
                                    }
                                    spacer()
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    // ===================================================
                    // Account handling
                    // ===================================================

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (exploredSquadratsStore?.isDownloading == true){
                            Column() {
                                // TODO: downloaded vs processed
                                Text("Downloaded ${exploredSquadratsStore?.downloadedActivities ?: 0} activities...", fontSize = 14.sp)
                                LinearProgressIndicator()
                            }
                        } else {
                            val lastDownloadedAtTimestamp = exploredSquadratsStore?.lastDownloadedAt ?: 0
                            val lastDownloadedAt = DateFormat.getDateTimeInstance().format(Date(lastDownloadedAtTimestamp))

                            if (!exploredSquadratsStore?.lastDownloadError.isNullOrBlank()){
                                val atString = if (lastDownloadedAtTimestamp > 0) " at $lastDownloadedAt" else ""
                                Text(
                                    text = "Error downloading activities: ${exploredSquadratsStore?.lastDownloadError}${atString}.",
                                    fontSize = 12.sp
                                )
                            } else if ((exploredSquadratsStore?.downloadedActivities ?: 0) > 0 && lastDownloadedAtTimestamp > 0) {
                                Column() {
                                    Text(text = "$downloadedActivities Activities", lineHeight = 0.2.sp)
                                    Text(
                                        text = "Last refresh ${lastDownloadedAt}",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        lineHeight = 0.2.sp
                                    )
                                }
                            } else {
                                Text("No activities downloaded yet.")
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        var connectModifier = Modifier.height(30.dp).weight(2f)
                        if (exploredSquadratsStore?.isDownloading != true && !settingsStore?.statshuntersSharecode.isNullOrBlank()){
                            connectModifier = connectModifier.fillMaxWidth()

                            FilledTonalButton(modifier = Modifier.height(30.dp).weight(2f), onClick = {
                                coroutineScope.launch {
                                    ctx.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                        exploredSquadrats.toBuilder()
                                        .setLastDownloadedAt(0)
                                        .setDownloadedActivities(0)
                                        .clearExploredSquadrats()
                                        .clearExploredSquadratinhos()
                                        .clearRecentlyExploredSquadrats()
                                        .clearRecentlyExploredNewSquadrats()
                                        .clearRecentlyExploredNewSquadratinhos()
                                        .setBiggestUbersquadratX(0)
                                        .setBiggestUbersquadratY(0)
                                        .setBiggestUbersquadratSize(0)
                                        .setBiggestUbersquadratinhoX(0)
                                        .setBiggestUbersquadratinhoY(0)
                                        .setBiggestUbersquadratinhoSize(0)
                                        .setYard(0)
                                        .setYardinho(0)
                                        .build()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Update Squadrats")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Reload", fontSize = 12.sp, lineHeight = 0.6.sp)
                            }

                            Spacer(modifier = Modifier.width(5.dp))
                        }

                        if (exploredSquadratsStore?.isDownloading != true){
                            FilledTonalButton(modifier = connectModifier, onClick = {
                                statshuntersDialogVisible = true
                            }) {
                                Icon(Icons.Default.Person, contentDescription = "Connect StatsHunters")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Connect", fontSize = 12.sp, lineHeight = 0.6.sp)
                            }
                        }
                    }
                }
                // ===================================================
                // Settings
                // ===================================================
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = !areSquadratsDisabled, onCheckedChange = { areSquadratsDisabled = !it})
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Enable squadrat drawing")
                    }

                    if (!areSquadratsDisabled) {
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
                            Switch(checked = !hideSquadratGrid, onCheckedChange = { hideSquadratGrid = !it})
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Show squadrat grid")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = !areSquadratinhosDisabled, onCheckedChange = { areSquadratinhosDisabled = !it})
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Draw missing/new squadratinhos")
                    }
                }
            }
        }

        val fontSize = 10.sp
        NavigationBar(
            modifier = Modifier.height(60.dp)
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { onFinish() },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Exit",
                        tint = Color(0xFFFF0077)
                    )
                },
                label = {
                    Text(
                        text = "Exit",
                        color = Color(0xFFFF0077),
                        fontSize = fontSize
                    )
                }
            )

            NavigationBarItem(
                selected = pageIndex == 0,
                onClick = { pageIndex = 0 },
                icon = {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Exit",
                    )
                },
                label = {
                    Text(
                        text = "Data",
                        fontSize = fontSize
                    )
                }
            )

            NavigationBarItem(
                selected = pageIndex == 1,
                onClick = { pageIndex = 1 },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                    )
                },
                label = {
                    Text(
                        text = "Settings",
                        fontSize = fontSize
                    )
                }
            )

            NavigationBarItem(
                selected = false,
                onClick = {
                    coroutineScope.launch {
                        ctx.userPreferencesDataStore.updateData { preferences ->
                            preferences.toBuilder()
                                .setSquadratDrawRange(squadratLoadRange.toInt())
                                .setHideSquadratGridLines(hideSquadratGrid)
                                .setAreSquadratsDisabled(areSquadratsDisabled)
                                .setAreSquadratinhosDisabled(areSquadratinhosDisabled)
                                .build()
                        }
                        savedDialogVisible = true
                    }
                },
                icon = {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = {
                    Text(
                        text = "Save",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = fontSize
                    )
                }
            )
        }

        // ===================================================
        // Dialogs
        // ===================================================

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
                                            exploredSquadrats.toBuilder()
                                                .setLastDownloadedAt(0)
                                                .clearExploredSquadrats()
                                                .clearExploredSquadratinhos()
                                                .clearRecentlyExploredSquadrats()
                                                .clearRecentlyExploredNewSquadrats()
                                                .clearRecentlyExploredNewSquadratinhos()
                                                .setBiggestUbersquadratX(0)
                                                .setBiggestUbersquadratY(0)
                                                .setBiggestUbersquadratSize(0)
                                                .setBiggestUbersquadratinhoX(0)
                                                .setBiggestUbersquadratinhoY(0)
                                                .setBiggestUbersquadratinhoSize(0)
                                                .setYard(0)
                                                .setYardinho(0)
                                                .build()
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
