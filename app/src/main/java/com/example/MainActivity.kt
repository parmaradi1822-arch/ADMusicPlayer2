package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.audio.AudioPlayerManager
import com.example.data.models.PlaylistEntity
import com.example.data.models.SongEntity
import com.example.ui.MusicViewModel
import com.example.ui.theme.ADMusicPlayerTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()

            ADMusicPlayerTheme(themeMode = themeMode, themeColor = themeColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Graceful lock when system shifts background
        viewModel.forceLock()
    }
}

@Composable
fun AppContent(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

    // 1. Splash Welcome State
    var showWelcome by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000L) // Show welcome screen for 3 seconds
        showWelcome = false
    }

    // 2. Playback Success Messages
    val studioMessage by viewModel.studioExportSuccessMessage.collectAsStateWithLifecycle()
    LaunchedEffect(studioMessage) {
        if (studioMessage != null) {
            Toast.makeText(context, studioMessage, Toast.LENGTH_LONG).show()
            viewModel.clearStudioMessage()
        }
    }

    Crossfade(targetState = showWelcome, label = "welcome_fade") { welcomeActive ->
        if (welcomeActive) {
            WelcomeScreen()
        } else {
            Crossfade(targetState = isAppLocked, label = "lock_fade") { locked ->
                if (locked) {
                    AppLockScreen(viewModel)
                } else {
                    AppDashboard(viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. WELCOME SCREEN
// ==========================================
@Composable
fun WelcomeScreen() {
    val infiniteTransition = rememberInfiniteTransition("disc_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0B21),
                        Color(0xFF070510)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(bounceScale)
                    .drawBehind {
                        drawCircle(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2575FC), Color(0xFFD946EF))
                            ),
                            radius = size.width / 2f,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(80.dp)
                        .rotate(angle)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "AD MUSIC",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "PURE ACOUSTIC SPACES",
                style = MaterialTheme.typography.labelMedium,
                color = com.example.ui.theme.PremiumLavender,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}




// ==========================================
// 12. APP LOCK SCREEN (PIN & FINGERPRINT SELECTS)
// ==========================================
@Composable
fun AppLockScreen(viewModel: MusicViewModel) {
    var pinEntry by remember { mutableStateOf("") }
    var showErrorMessage by remember { mutableStateOf(false) }
    val correctPin by viewModel.appLockPin.collectAsStateWithLifecycle()
    val isFingerprintEnabled by viewModel.isFingerprintEnabled.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App Locked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "App Safe Locked",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Text(
                text = "Enter safety PIN to open AD Player",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..3) {
                    val filled = i < pinEntry.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (filled) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }

            if (showErrorMessage) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Incorrect Safety PIN. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Number Grid Pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "🔓")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                numbers.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { char ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16161F))
                                    .clickable {
                                        showErrorMessage = false
                                        when (char) {
                                            "C" -> pinEntry = ""
                                            "🔓" -> {
                                                if (viewModel.tryUnlock(pinEntry)) {
                                                    pinEntry = ""
                                                } else {
                                                    showErrorMessage = true
                                                    pinEntry = ""
                                                }
                                            }
                                            else -> {
                                                if (pinEntry.length < 4) {
                                                    pinEntry += char
                                                    if (pinEntry.length == 4) {
                                                        // Auto verify when 4 digits met
                                                        if (viewModel.tryUnlock(pinEntry)) {
                                                            pinEntry = ""
                                                        } else {
                                                            showErrorMessage = true
                                                            pinEntry = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .testTag("pin_key_$char"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (char == "🔓") MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (isFingerprintEnabled) {
                Spacer(modifier = Modifier.height(24.dp))
                IconButton(
                    onClick = {
                        // Simulated safe fingerprint verification
                        viewModel.tryUnlock(correctPin.ifEmpty { "1234" })
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .testTag("fingerprint_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint Unlock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// APP CORE DASHBOARD
// ==========================================
@Composable
fun AppDashboard(viewModel: MusicViewModel) {
    var activeTab by remember { mutableIntStateOf(0) }
    var isPlayerSheetExpanded by remember { mutableStateOf(false) }
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                // Stick bottom Mini-Player
                currentSong?.let { song ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayer(
                            song = song,
                            viewModel = viewModel,
                            onExpand = { isPlayerSheetExpanded = true }
                        )
                    }
                }

                NavigationBar(
                    containerColor = Color(0xFF0A0A0A),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = Color(0xFF1F1F1F),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    val tabs = listOf(
                        Triple("Library", Icons.Default.List, 0),
                        Triple("Equalizer", Icons.Default.GraphicEq, 1),
                        Triple("Studio", Icons.Default.Audiotrack, 2),
                        Triple("Settings", Icons.Default.Settings, 3)
                    )

                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            modifier = Modifier.testTag("nav_tab_$index"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = com.example.ui.theme.PremiumLavender,
                                selectedTextColor = com.example.ui.theme.PremiumLavender,
                                indicatorColor = com.example.ui.theme.PremiumDeepPurple,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> MusicLibraryScreen(viewModel)
                1 -> EqualizerScreen(viewModel)
                2 -> AudioStudioScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }
        }
    }

    // 3. Slide up Full Music Player Sheet
    if (isPlayerSheetExpanded && currentSong != null) {
        FullPlayerSheet(
            song = currentSong!!,
            viewModel = viewModel,
            onCollapse = { isPlayerSheetExpanded = false }
        )
    }
}

// ==========================================
// 2. MUSIC LIBRARY SCREEN
// ==========================================
@Composable
fun MusicLibraryScreen(viewModel: MusicViewModel) {
    val songs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("All") } // "All", "Playlists", "Favorites", "Recents"
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Premium Bold Typography Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "PREMIUM EXPERIENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.PremiumLavender.copy(alpha = 0.8f)
                )
                Text(
                    text = "AD Music",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.theme.DarkSurface)
                        .border(1.dp, com.example.ui.theme.PremiumBorder, CircleShape)
                        .clickable { viewModel.searchQuery.value = "" }
                        .testTag("header_search_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.theme.PremiumLavender)
                        .clickable { viewModel.updateThemeColor("Cosmic Blue") }
                        .testTag("header_settings_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = com.example.ui.theme.PremiumDeepPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search System
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.LightGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("song_search_field"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = com.example.ui.theme.DarkSurface,
                unfocusedContainerColor = com.example.ui.theme.DarkSurface,
                focusedBorderColor = com.example.ui.theme.PremiumLavender,
                unfocusedBorderColor = com.example.ui.theme.PremiumBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.LightGray,
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray
            ),
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Library Horizontal Categories/Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf("All", "Playlists", "Favorites", "Recents")
            items(chips) { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (activeSubTab == chip) com.example.ui.theme.PremiumLavender else com.example.ui.theme.DarkSurface)
                        .border(1.dp, if (activeSubTab == chip) com.example.ui.theme.PremiumLavender else com.example.ui.theme.PremiumBorder, RoundedCornerShape(20.dp))
                        .clickable { activeSubTab = chip }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("library_chip_$chip")
                ) {
                    Text(
                        text = chip.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeSubTab == chip) com.example.ui.theme.PremiumDeepPurple else Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (activeSubTab) {
                    "Playlists" -> "My Playlists"
                    "Favorites" -> "Favorite Tracks"
                    "Recents" -> "Recently Played"
                    else -> "All Songs (${songs.size})"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Row {
                if (activeSubTab == "Playlists") {
                    IconButton(
                        onClick = { showAddPlaylistDialog = true },
                        modifier = Modifier.testTag("add_playlist_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                    }
                }

                IconButton(
                    onClick = { viewModel.scanLibrary() },
                    modifier = Modifier.testTag("refresh_scan_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Body lists
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (activeSubTab) {
                "Playlists" -> {
                    if (playlists.isEmpty()) {
                        EmptyStatePrompt(message = "No playlists created yet. Create one now!")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(playlists) { pl ->
                                PlaylistCardItem(pl, viewModel)
                            }
                        }
                    }
                }
                "Favorites" -> {
                    if (favorites.isEmpty()) {
                        EmptyStatePrompt(message = "Worship your tracks here. Tap Heart in player!")
                    } else {
                        SongListView(songsList = favorites, viewModel = viewModel)
                    }
                }
                "Recents" -> {
                    if (recents.isEmpty()) {
                        EmptyStatePrompt(message = "Play logs empty. Start listening to tracks!")
                    } else {
                        SongListView(songsList = recents, viewModel = viewModel)
                    }
                }
                else -> {
                    if (songs.isEmpty()) {
                        EmptyStatePrompt(message = "Parsing audio files... Or tap scanned fallback!")
                    } else {
                        SongListView(songsList = songs, viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Dialog for creating playlist
    if (showAddPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddPlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Enter playlist name...") },
                    singleLine = true,
                    modifier = Modifier.testTag("playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(playlistNameInput)
                        playlistNameInput = ""
                        showAddPlaylistDialog = false
                    },
                    modifier = Modifier.testTag("playlist_confirm_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SongListView(songsList: List<SongEntity>, viewModel: MusicViewModel) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(songsList) { song ->
            SongListItem(song, viewModel)
        }
    }
}

@Composable
fun SongListItem(song: SongEntity, viewModel: MusicViewModel) {
    val currentPlayingSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isSelected = currentPlayingSong?.id == song.id

    var showPlaylistAssignor by remember { mutableStateOf(false) }

    val backgroundBrush = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                com.example.ui.theme.PremiumDeepPurple.copy(alpha = 0.35f),
                com.example.ui.theme.PremiumRoyalPurple.copy(alpha = 0.35f)
            )
        )
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.playSingle(song)
            }
            .testTag("song_item_${song.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.Transparent else com.example.ui.theme.DarkSurface
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) com.example.ui.theme.PremiumLavender else com.example.ui.theme.PremiumBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (backgroundBrush != null) it.background(backgroundBrush) else it }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon representing album art with visual gradient
            val gradientColors = remember(song.id) {
                when (song.id.hashCode() % 3) {
                    0 -> listOf(Color(0xFFFB923C), Color(0xFFF43F5E))
                    1 -> listOf(Color(0xFF60A5FA), Color(0xFF6366F1))
                    else -> listOf(Color(0xFF34D399), Color(0xFF06B6D4))
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(colors = gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected && isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.folder} | ${formatDuration(song.duration)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray
                )
            }

            // Quick Fav Toggle
            IconButton(
                onClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) },
                modifier = Modifier.testTag("fav_btn_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) com.example.ui.theme.PremiumLavender else Color.LightGray
                )
            }

            // Options launcher (add to playlist)
            IconButton(
                onClick = { showPlaylistAssignor = true },
                modifier = Modifier.testTag("option_btn_${song.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = Color.LightGray
                )
            }
        }
    }

    if (showPlaylistAssignor) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showPlaylistAssignor = false },
            title = { Text("Assign to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Create a playlist first under the Playlist tab!")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addSongToPlaylist(pl.id, song.id)
                                        showPlaylistAssignor = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pl.name, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistAssignor = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun PlaylistCardItem(playlist: PlaylistEntity, viewModel: MusicViewModel) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(playlist.name) }
    var isExpanded by remember { mutableStateOf(false) }

    val songsInPlaylist by viewModel.songsInSelectedPlaylist.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()

    val detailsActive = selectedId == playlist.id

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            viewModel.selectPlaylist(playlist.id)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("playlist_card_${playlist.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Folder Playlist",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Row {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (detailsActive && songsInPlaylist.isNotEmpty()) {
                        songsInPlaylist.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.playSingle(song) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(song.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { viewModel.removeSongFromPlaylist(playlist.id, song.id) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color.Red)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No songs in this playlist. Find songs in library and tap Assign!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renamePlaylist(playlist.id, renameInput)
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EmptyStatePrompt(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// ==========================================
// 8. EQUALIZER SCREEN
// ==========================================
@Composable
fun EqualizerScreen(viewModel: MusicViewModel) {
    val bassBoost by viewModel.bassBoost.collectAsStateWithLifecycle()
    val trebleBoost by viewModel.trebleBoost.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Preset Section
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                Text(
                    text = "STUDIO AUDIO EQUALIZER",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.PremiumLavender.copy(alpha = 0.8f)
                )
                Text(
                    text = "Advanced EQ",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets row scroll
            val presets = listOf("Normal", "Rock", "Pop", "Jazz", "Classical", "Lofi")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presets) { preset ->
                    val isActive = currentPreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isActive) com.example.ui.theme.PremiumLavender else com.example.ui.theme.DarkSurface)
                            .border(1.dp, if (isActive) com.example.ui.theme.PremiumLavender else com.example.ui.theme.PremiumBorder, RoundedCornerShape(20.dp))
                            .clickable { viewModel.applyPreset(preset) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("preset_$preset")
                    ) {
                        Text(
                            text = preset.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) com.example.ui.theme.PremiumDeepPurple else Color.LightGray
                        )
                    }
                }
            }
        }

        // Live Equalizer wave Canvas Visualizer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dynamic Acoustic Wave",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val infiniteTransition = rememberInfiniteTransition("wave_visuals")
                    val phase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 2f * Math.PI.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "phase"
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        val numPoints = 80
                        val points = mutableListOf<Offset>()

                        // Draw glowing neon backdrop sound waves
                        for (i in 0..numPoints) {
                            val x = (i.toFloat() / numPoints) * width
                            // Pulse factor based on if music is playing
                            val pulse = if (isPlaying) 1.5f else 0.2f
                            // Composite harmonic wave: combining lofi & bass state elements in sine
                            val y = midY + ((height * 0.35f * pulse) * (
                                    0.5f * sin(2f * Math.PI.toFloat() * (i.toFloat() / numPoints) * 2f + phase) +
                                            0.3f * sin(2f * Math.PI.toFloat() * (i.toFloat() / numPoints) * 5f - phase * 0.5f) +
                                            0.2f * cos(2f * Math.PI.toFloat() * (i.toFloat() / numPoints) * 8f)
                                    ))

                            points.add(Offset(x, y))
                        }

                        for (i in 0 until points.size - 1) {
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF2575FC), Color(0xFFD946EF))
                                ),
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Interactive Boost Knobs (Bass & Treble)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bass boost knob cardioide
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Bass Boost", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "${bassBoost.toInt()}%", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = bassBoost,
                            onValueChange = { viewModel.setBassBoost(it) },
                            valueRange = 0f..100f,
                            modifier = Modifier.testTag("slider_bass")
                        )
                    }
                }

                // Treble boost knob cardioide
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Treble Boost", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "${trebleBoost.toInt()}%", fontSize = 20.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.ExtraBold)

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = trebleBoost,
                            onValueChange = { viewModel.setTrebleBoost(it) },
                            valueRange = 0f..100f,
                            modifier = Modifier.testTag("slider_treble")
                        )
                    }
                }
            }
        }

        // 5 Band Adjuster sliders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "5-Bands Sound Board",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val bands = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
                    bands.forEachIndexed { index, name ->
                        val bandVal = eqBands.getOrElse(index) { 50f }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(text = name, modifier = Modifier.width(60.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Slider(
                                value = bandVal,
                                onValueChange = { viewModel.setEQBand(index, it) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f).testTag("eq_band_$index")
                            )
                            Text(
                                text = "${bandVal.toInt()}",
                                modifier = Modifier.width(40.dp),
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9 & 10. AUDIO STUDIO SCREEN
// ==========================================
@Composable
fun AudioStudioScreen(viewModel: MusicViewModel) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val studioIsLoading by viewModel.studioIsLoading.collectAsStateWithLifecycle()

    var activeStudioMode by remember { mutableStateOf("Trim") } // "Trim", "Merge", "Lofi"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
            Text(
                text = "ACOUSTIC CREATIVE STUDIO",
                style = MaterialTheme.typography.labelSmall,
                color = com.example.ui.theme.PremiumLavender.copy(alpha = 0.8f)
            )
            Text(
                text = "Sound Studio",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Styled Custom Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(com.example.ui.theme.DarkSurface)
                .border(1.dp, com.example.ui.theme.PremiumBorder, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val modes = listOf("Trim", "Merge", "Lofi")
            modes.forEach { mode ->
                val isActive = activeStudioMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isActive) com.example.ui.theme.PremiumLavender else Color.Transparent)
                        .clickable { activeStudioMode = mode }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when(mode) {
                            "Trim" -> "TRIM & CUT"
                            "Merge" -> "MERGE DIRECT"
                            else -> "LOFI CREATOR"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) com.example.ui.theme.PremiumDeepPurple else Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (studioIsLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Rendering waves PCM bytes...")
                }
            }
        } else {
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty music vault. Add or scan tracks to build audio creations!")
                }
            } else {
                when (activeStudioMode) {
                    "Trim" -> TrimStudioTab(songs, viewModel)
                    "Merge" -> MergeStudioTab(songs, viewModel)
                    "Lofi" -> LofiCreatorTab(songs, viewModel)
                }
            }
        }
    }
}

@Composable
fun TrimStudioTab(songs: List<SongEntity>, viewModel: MusicViewModel) {
    var selectedSongIndex by remember { mutableIntStateOf(0) }
    val song = songs.getOrElse(selectedSongIndex) { songs.first() }

    var startSec by remember { mutableFloatStateOf(0f) }
    var endSec by remember { mutableFloatStateOf(10f) }
    var fadeInChecked by remember { mutableStateOf(true) }
    var fadeOutChecked by remember { mutableStateOf(true) }
    var exportName by remember { mutableStateOf("My Trimmed Sound") }

    var showDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Select song to Trim & Cut:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDropdown = true }
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = song.title, fontWeight = FontWeight.SemiBold)
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    songs.forEachIndexed { idx, s ->
                        DropdownMenuItem(
                            text = { Text(s.title) },
                            onClick = {
                                selectedSongIndex = idx
                                showDropdown = false
                                // reset sliders on track change
                                startSec = 0f
                                endSec = (s.duration / 1000f).coerceAtMost(15f)
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Segment Range Controllers", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Start Mark: ${startSec.toInt()}s", fontSize = 13.sp)
                    Slider(
                        value = startSec,
                        onValueChange = { startSec = it.coerceAtMost(endSec - 2f) },
                        valueRange = 0f..(song.duration / 1000f),
                        modifier = Modifier.testTag("trim_start_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("End Mark: ${endSec.toInt()}s", fontSize = 13.sp)
                    Slider(
                        value = endSec,
                        onValueChange = { endSec = it.coerceAtLeast(startSec + 2f) },
                        valueRange = 0f..(song.duration / 1000f),
                        modifier = Modifier.testTag("trim_end_slider")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(checked = fadeInChecked, onCheckedChange = { fadeInChecked = it })
                    Text("Acoustic Fade-in", fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(checked = fadeOutChecked, onCheckedChange = { fadeOutChecked = it })
                    Text("Acoustic Fade-out", fontSize = 13.sp)
                }
            }
        }

        item {
            OutlinedTextField(
                value = exportName,
                onValueChange = { exportName = it },
                label = { Text("Export Sound Filename") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trim_export_name")
            )
        }

        item {
            Button(
                onClick = {
                    viewModel.processTrim(
                        sourceSong = song,
                        startMs = (startSec * 1000L).toLong(),
                        endMs = (endSec * 1000L).toLong(),
                        fadeIn = fadeInChecked,
                        fadeOut = fadeOutChecked,
                        title = exportName
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trim_export_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Process Wave Cut & Save")
            }
        }
    }
}

@Composable
fun MergeStudioTab(songs: List<SongEntity>, viewModel: MusicViewModel) {
    val selectedSongs = remember { mutableStateListOf<SongEntity>() }
    var exportName by remember { mutableStateOf("My Combined Audio Master") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Select songs to concat / merge in sequential order:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        items(songs) { s ->
            val isChecked = selectedSongs.any { it.id == s.id }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isChecked) {
                            selectedSongs.removeAll { it.id == s.id }
                        } else {
                            selectedSongs.add(s)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = {
                            if (isChecked) {
                                selectedSongs.removeAll { it.id == s.id }
                            } else {
                                selectedSongs.add(s)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(s.title, fontWeight = FontWeight.SemiBold)
                        Text(s.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = exportName,
                onValueChange = { exportName = it },
                label = { Text("Merged Export Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merge_export_name")
            )
        }

        item {
            Button(
                onClick = {
                    if (selectedSongs.isEmpty()) {
                        Toast.makeText(viewModel.getApplication(), "Select at least 1 song to merge!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.processMerge(selectedSongs.toList(), exportName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merge_export_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MergeType, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Process Concat Merge")
            }
        }
    }
}

@Composable
fun LofiCreatorTab(songs: List<SongEntity>, viewModel: MusicViewModel) {
    var selectedSongIndex by remember { mutableIntStateOf(0) }
    val song = songs.getOrElse(selectedSongIndex) { songs.first() }

    var speedFactor by remember { mutableFloatStateOf(0.8f) } // classic slow lofi
    var pitchFactor by remember { mutableFloatStateOf(0.75f) } // classic deep reverb lofi
    var customBassEnhancer by remember { mutableFloatStateOf(80f) } // classic fat bass lofi
    var staticNoiseLevel by remember { mutableFloatStateOf(30f) } // vinyl static hiss
    var lowPassCutoffLevel by remember { mutableFloatStateOf(40f) } // muffled lowpass filter
    var exportName by remember { mutableStateOf("My Chillwave Lofi Track") }

    var showDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "SELECT SONG TO GENERATE LOFI MIX:",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDropdown = true }
                    .background(com.example.ui.theme.DarkSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, com.example.ui.theme.PremiumBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = song.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.LightGray)
                }

                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    songs.forEachIndexed { idx, s ->
                        DropdownMenuItem(
                            text = { Text(s.title) },
                            onClick = {
                                selectedSongIndex = idx
                                showDropdown = false
                            }
                        )
                    }
                }
            }
        }

        // Quick Presets Selector
        item {
            Text(
                text = "LOFI ALCHEMY PRESETS:",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(com.example.ui.theme.DarkSurface)
                        .border(1.dp, com.example.ui.theme.PremiumBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            speedFactor = 0.72f
                            pitchFactor = 0.65f
                            customBassEnhancer = 90f
                            staticNoiseLevel = 45f
                            lowPassCutoffLevel = 60f
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "SLOWED + REVERB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(com.example.ui.theme.DarkSurface)
                        .border(1.dp, com.example.ui.theme.PremiumBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            speedFactor = 0.82f
                            pitchFactor = 0.85f
                            customBassEnhancer = 70f
                            staticNoiseLevel = 25f
                            lowPassCutoffLevel = 35f
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CHILLWAVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, com.example.ui.theme.PremiumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACOUSTIC LOFI PARAMETERS",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SPEED FACTOR: ${String.format("%.2f", speedFactor)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Slider(
                        value = speedFactor,
                        onValueChange = { speedFactor = it },
                        valueRange = 0.5f..1.2f,
                        modifier = Modifier.testTag("lofi_speed_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.PremiumLavender,
                            activeTrackColor = com.example.ui.theme.PremiumLavender,
                            inactiveTrackColor = com.example.ui.theme.PremiumBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "PITCH FACTOR: ${String.format("%.2f", pitchFactor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Slider(
                        value = pitchFactor,
                        onValueChange = { pitchFactor = it },
                        valueRange = 0.5f..1.2f,
                        modifier = Modifier.testTag("lofi_pitch_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.PremiumLavender,
                            activeTrackColor = com.example.ui.theme.PremiumLavender,
                            inactiveTrackColor = com.example.ui.theme.PremiumBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "FAT SUB-BASS OVERLAY: ${customBassEnhancer.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Slider(
                        value = customBassEnhancer,
                        onValueChange = { customBassEnhancer = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("lofi_bass_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.PremiumLavender,
                            activeTrackColor = com.example.ui.theme.PremiumLavender,
                            inactiveTrackColor = com.example.ui.theme.PremiumBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "STATIC NOISE (TAPEHISS & CRACKLE): ${staticNoiseLevel.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Slider(
                        value = staticNoiseLevel,
                        onValueChange = { staticNoiseLevel = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("lofi_noise_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.PremiumLavender,
                            activeTrackColor = com.example.ui.theme.PremiumLavender,
                            inactiveTrackColor = com.example.ui.theme.PremiumBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "MUFFLED LOW-PASS FILTER: ${lowPassCutoffLevel.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Slider(
                        value = lowPassCutoffLevel,
                        onValueChange = { lowPassCutoffLevel = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("lofi_lpf_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.PremiumLavender,
                            activeTrackColor = com.example.ui.theme.PremiumLavender,
                            inactiveTrackColor = com.example.ui.theme.PremiumBorder
                        )
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = exportName,
                onValueChange = { exportName = it },
                label = { Text("Lofi Sound Tag Export") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lofi_export_name"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = com.example.ui.theme.DarkSurface,
                    unfocusedContainerColor = com.example.ui.theme.DarkSurface,
                    focusedBorderColor = com.example.ui.theme.PremiumLavender,
                    unfocusedBorderColor = com.example.ui.theme.PremiumBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray
                ),
                shape = RoundedCornerShape(24.dp)
            )
        }

        item {
            Button(
                onClick = {
                    viewModel.processLofiCreator(
                        sourceSong = song,
                        speed = speedFactor,
                        pitch = pitchFactor,
                        bass = customBassEnhancer,
                        noise = staticNoiseLevel,
                        lpf = lowPassCutoffLevel,
                        title = exportName
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lofi_export_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.ui.theme.PremiumLavender,
                    contentColor = com.example.ui.theme.PremiumDeepPurple
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("PROCESS & EXPORT LOFI MASTER", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ==========================================
// 13 & 14. SETTINGS, DOWNLOADS, & VAULT SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()

    val isLockEnabled by viewModel.isAppLockEnabled.collectAsStateWithLifecycle()
    val isFingerprintEnabled by viewModel.isFingerprintEnabled.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }

    val downloadsSongs by viewModel.downloads.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Vault Downloads Folders Organizing
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                Text(
                    text = "VAULT SECURED LOCAL DIRECTORIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.PremiumLavender.copy(alpha = 0.8f)
                )
                Text(
                    text = "Local Downloads",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action to download mock instrumental to folder
            Button(
                onClick = {
                    viewModel.downloadSong(
                        title = "Ambient Horizon",
                        artist = "Ethereal Drone",
                        album = "Atmospheres",
                        style = "ambient",
                        folderName = "Ambient Master"
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("add_download_trigger")
            ) {
                Icon(Icons.Default.DownloadForOffline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Ambient Horizon to 'Ambient Master' Folder")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (downloadsSongs.isEmpty()) {
                Text(
                    "No downloads stored locally in safety vault folders.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                // Group downloads by folder
                val grouped = downloadsSongs.groupBy { it.folder }
                grouped.forEach { (folderName, list) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folderName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            list.forEach { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.playSingle(song) },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(song.title, fontSize = 13.sp)
                                    Text(formatDuration(song.duration), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Visual Customization Theme Selection
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                Text(
                    text = "PREMIUM EXPERIENCE USER SCHEMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.PremiumLavender.copy(alpha = 0.8f)
                )
                Text(
                    text = "Styling & Themes",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Dark / Light toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Acoustic Pure Dark Mode", fontWeight = FontWeight.Bold)
                            Text("Render deep charcoal background space", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = themeMode == "Dark",
                            onCheckedChange = { viewModel.updateThemeMode(if (it) "Dark" else "Light") },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme Colors grid selector
                    Text("Select Primary Theme Vibe", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    val options = listOf("Cosmic Blue", "Deep Emerald", "Cyberpunk Purple", "Neon Red")
                    options.forEach { colorName ->
                        val selected = colorName == themeColor
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.updateThemeColor(colorName) }
                                .testTag("theme_color_$colorName"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(colorName, fontWeight = FontWeight.SemiBold)
                                if (selected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Security Configuration App Safe Lock
        item {
            Text(
                text = "Privacy App Lock",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // PIN Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Safe Passcode PIN lock", fontWeight = FontWeight.Bold)
                            Text(
                                if (isLockEnabled) "Secure gated app at entry (Active)"
                                else "Unlock on startup configuration",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = isLockEnabled,
                            onCheckedChange = { active ->
                                if (active) {
                                    showPinDialog = true
                                } else {
                                    viewModel.updatePin("") // clears pin and disables lock
                                }
                            },
                            modifier = Modifier.testTag("pin_lock_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Fingerprint biometric simulation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Biometric Fingerprint simulator", fontWeight = FontWeight.Bold)
                            Text("Unlock using quick fingerprint sensors", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = isFingerprintEnabled,
                            onCheckedChange = { viewModel.toggleFingerprint(it) },
                            modifier = Modifier.testTag("fingerprint_switch")
                        )
                    }
                }
            }
        }

        // Sound Quality Specs settings
        item {
            Text(
                text = "Audio Engineering Quality",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val qualities = listOf("Standard (128kbps)", "High (192kbps)", "Ultra (320kbps)")
                    qualities.forEach { quality ->
                        val selected = quality == audioQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateAudioQuality(quality) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(quality, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            RadioButton(
                                selected = selected,
                                onClick = { viewModel.updateAudioQuality(quality) },
                                modifier = Modifier.testTag("radio_quality_$quality")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set 4-Digit Passcode") },
            text = {
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Enter 4 digits (e.g. 1234)") },
                    singleLine = true,
                    modifier = Modifier.testTag("pin_set_textfield")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinText.length == 4) {
                            viewModel.updatePin(pinText)
                            pinText = ""
                            showPinDialog = false
                        } else {
                            Toast.makeText(viewModel.getApplication(), "Length must be exactly 4!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("pin_set_confirm")
                ) {
                    Text("Enable Safe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ==========================================
// STICKY BOTTOM MINI PLAYER
// ==========================================
@Composable
fun MiniPlayer(song: SongEntity, viewModel: MusicViewModel, onExpand: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .testTag("mini_player_click")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Disk indicator with rotation relative to state
                val infiniteTransition = rememberInfiniteTransition("mini_disc_trans")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "mini_angle"
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .rotate(if (isPlaying) angle else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row {
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.testTag("mini_next")
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

// ==========================================
// 3 & 11. FULL EXPANDED MUSIC PLAYER SHEET
// ==========================================
@Composable
fun FullPlayerSheet(song: SongEntity, viewModel: MusicViewModel, onCollapse: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    val shuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatMode.collectAsStateWithLifecycle()

    val slowFactor by viewModel.slowFactor.collectAsStateWithLifecycle()
    val pitchFactor by viewModel.pitchFactor.collectAsStateWithLifecycle()

    val timerSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()

    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Disk Rotation animation
    val infiniteTransition = rememberInfiniteTransition("hero_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07050E))
            .clickable(enabled = false) {} // block click propagation
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Blur background ambience
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(200f, 400f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("collapse_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "Acoustic Pure Playback",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )

                // Sleep Timer Trigger Indicator
                IconButton(
                    onClick = { showSleepTimerDialog = true },
                    modifier = Modifier.testTag("sleep_timer_full_btn")
                ) {
                    Icon(
                        imageVector = if (timerSeconds > 0) Icons.Default.Timer10 else Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (timerSeconds > 0) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }

            // Big Vinyl Record disc with revolving motion
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141416))
                    .rotate(if (isPlaying) angle else 0f)
                    .border(4.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // inner tracks lines of vinyl disc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width / 2f, style = Stroke(2f))
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width / 2.5f, style = Stroke(2f))
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width / 3f, style = Stroke(2f))
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Sleep Timer countdown label
            if (timerSeconds > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sleep Timer: ${formatDuration((timerSeconds * 1000).toLong())}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Live visualizer bars peak canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val barWidth = 4.dp.toPx()
                val gap = 4.dp.toPx()
                val count = (size.width / (barWidth + gap)).toInt()

                for (i in 0 until count) {
                    // pseudo rhythmic height
                    val baseFactor = sin((i * 0.15).toFloat()) * 0.5f + 0.5f
                    val multiplier = if (isPlaying) (0.23 + 0.77 * Math.sin(System.currentTimeMillis() * 0.003 + i * 0.2)) else 0.1
                    val h = size.height * baseFactor * multiplier

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2575FC), Color(0xFFD946EF))
                        ),
                        topLeft = Offset(i * (barWidth + gap), (size.height - h).toFloat()),
                        size = Size(barWidth, h.toFloat())
                    )
                }
            }

            // Metas title, artist
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                // Playback speed details tag if modified
                if (slowFactor != 1.0f || pitchFactor != 1.0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mod Speed: ${String.format("%.2f", slowFactor)}x | Pitch: ${String.format("%.2f", pitchFactor)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Seek Bar controller
            Column {
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toInt()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(100f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seekbar")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(progress.toLong()), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(text = formatDuration(duration.toLong()), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }

            // Master player controls panel
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                }

                // Previous
                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.testTag("prev_btn")
                ) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // High accent Play/Pause FAB
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("play_pause_fab")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.testTag("next_btn")
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // Repeat Mode Button
                IconButton(
                    onClick = { viewModel.advanceRepeatMode() },
                    modifier = Modifier.testTag("repeat_btn")
                ) {
                    Icon(
                        imageVector = when (repeat) {
                            AudioPlayerManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            AudioPlayerManager.RepeatMode.ALL -> Icons.Default.Repeat
                            else -> Icons.Default.TrendingFlat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeat != AudioPlayerManager.RepeatMode.NONE) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    // Sleep Timer Trigger Dialog
    if (showSleepTimerDialog) {
        var customSleepMinutes by remember { mutableFloatStateOf(15f) }
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sleep Timer Engine")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Pause playing dynamic audio when the countdown completes.")

                    // Preset options
                    val timerOptions = listOf(5, 15, 30, 60)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        timerOptions.forEach { min ->
                            Button(
                                onClick = {
                                    viewModel.startSleepTimer(min)
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("timer_preset_$min"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${min}m", fontSize = 12.sp)
                            }
                        }
                    }

                    Divider()

                    // Custom slider
                    Text("Custom Countdown: ${customSleepMinutes.toInt()} Minutes")
                    Slider(
                        value = customSleepMinutes,
                        onValueChange = { customSleepMinutes = it },
                        valueRange = 1f..120f,
                        modifier = Modifier.testTag("custom_timer_slider")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startSleepTimer(customSleepMinutes.toInt())
                        showSleepTimerDialog = false
                    },
                    modifier = Modifier.testTag("timer_set_confirm")
                ) {
                    Text("Apply Timer")
                }
            },
            dismissButton = {
                Row {
                    if (timerSeconds > 0) {
                        TextButton(
                            onClick = {
                                viewModel.stopSleepTimer()
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.testTag("timer_cancel_btn")
                        ) {
                            Text("Stop Active")
                        }
                    }
                    TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
                }
            }
        )
    }
}

// ==========================================
// UTILITY FUNCTIONS
// ==========================================
fun formatDuration(ms: Long): String {
    val secTotal = ms / 1000
    val min = secTotal / 60
    val sec = secTotal % 60
    return String.format("%02d:%02d", min, sec)
}
