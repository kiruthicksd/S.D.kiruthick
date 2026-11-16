package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.UserPhoto
import com.example.data.repository.FirestoreAppRepository
import com.example.data.repository.SettingsRepository
import com.example.firebase.configureAppCheck
import com.example.ui.components.FuturisticBackground
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.Esp32CameraScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SmartAiMapTheme
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.MainAppViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class AppNavDestination(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val showInMobileBottomBar: Boolean,
) {
  HOME("home", "Home", Icons.Filled.Home, true),
  MAP("map", "Map", Icons.Filled.Map, true),
  PHOTOS("photos", "Photos", Icons.Filled.PhotoLibrary, true),
  ESP32("esp32", "ESP32", Icons.Filled.Videocam, true),
  AI("ai", "AI Chat", Icons.Filled.AutoAwesome, true),
  NOTIFICATIONS("notifications", "Notifications", Icons.Filled.Notifications, false),
  PROFILE("profile", "Profile", Icons.Filled.Person, false),
  SETTINGS("settings", "Settings", Icons.Filled.Settings, false),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure App Check with launch intent before any Compose UI, ViewModels, or Firebase operations
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
      val settings by
        settingsRepo.settingsFlow.collectAsStateWithLifecycle(
          initialValue = com.example.data.repository.AppSettings()
        )
      SmartAiMapTheme(darkTheme = settings.isDarkTheme) {
        AppRootNavigation(settingsRepo = settingsRepo)
      }
    }
  }
}

@Composable
fun AppRootNavigation(settingsRepo: SettingsRepository) {
  val auth = remember { Firebase.auth }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainAuthenticatedShell(
        currentUserId = user.uid,
        displayName = user.displayName ?: "Explorer",
        email = user.email ?: "",
        settingsRepo = settingsRepo,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAuthenticatedShell(
  currentUserId: String,
  displayName: String,
  email: String,
  settingsRepo: SettingsRepository,
) {
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()
  val navController = rememberNavController()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val snackbarHostState = remember { SnackbarHostState() }

  val viewModel: MainAppViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            MainAppViewModel(
              repo = FirestoreAppRepository(db),
              settingsRepo = settingsRepo,
              currentUserId = currentUserId,
              initialDisplayName = displayName,
              initialEmail = email,
            )
          }
        },
    )

  val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
  val profile by viewModel.userProfile.collectAsStateWithLifecycle()
  val photosState by viewModel.photosState.collectAsStateWithLifecycle()
  val sharedPhotosState by viewModel.sharedPhotosState.collectAsStateWithLifecycle()
  val locationHistoryState by viewModel.locationHistoryState.collectAsStateWithLifecycle()
  val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
  val aiMessagesState by viewModel.aiMessagesState.collectAsStateWithLifecycle()
  val gpsState by viewModel.gpsState.collectAsStateWithLifecycle()
  val selectedMapPoint by viewModel.selectedMapPoint.collectAsStateWithLifecycle()
  val esp32State by viewModel.esp32State.collectAsStateWithLifecycle()
  val aiStreamState by viewModel.aiStreamState.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

  var preselectedAiPhoto by remember { mutableStateOf<UserPhoto?>(null) }

  LaunchedEffect(bannerMessage) {
    val msg = bannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: AppNavDestination.HOME.route
  val currentDest =
    AppNavDestination.entries.firstOrNull { it.route == currentRoute } ?: AppNavDestination.HOME

  val unreadNotifCount =
    ((notificationsState as? DataUiState.Success)?.data.orEmpty()).count { !it.isRead }

  fun navigateTo(dest: AppNavDestination) {
    navController.navigate(dest.route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  FuturisticBackground(isDarkTheme = appSettings.isDarkTheme) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 600.dp

      val scaffoldBody: @Composable () -> Unit = {
        Scaffold(
          containerColor = Color.Transparent,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          topBar = {
            TopAppBar(
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
              navigationIcon = {
                if (!useRail) {
                  IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.testTag("drawer_menu_button"),
                  ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                  }
                }
              },
              title = {
                Text(
                  text = currentDest.label,
                  style = MaterialTheme.typography.titleLarge,
                )
              },
              actions = {
                IconButton(
                  onClick = { navigateTo(AppNavDestination.NOTIFICATIONS) },
                  modifier = Modifier.testTag("topbar_notifications_button"),
                ) {
                  BadgedBox(
                    badge = {
                      if (unreadNotifCount > 0) {
                        Badge { Text("$unreadNotifCount") }
                      }
                    }
                  ) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                  }
                }
                IconButton(
                  onClick = { navigateTo(AppNavDestination.PROFILE) },
                  modifier = Modifier.testTag("topbar_profile_button"),
                ) {
                  Icon(Icons.Filled.Person, contentDescription = "Profile")
                }
                IconButton(
                  onClick = { navigateTo(AppNavDestination.SETTINGS) },
                  modifier = Modifier.testTag("topbar_settings_button"),
                ) {
                  Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
              },
            )
          },
          bottomBar = {
            if (!useRail) {
              NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier.navigationBarsPadding(),
              ) {
                AppNavDestination.entries
                  .filter { it.showInMobileBottomBar }
                  .forEach { dest ->
                    NavigationBarItem(
                      selected = currentRoute == dest.route,
                      onClick = { navigateTo(dest) },
                      icon = { Icon(dest.icon, contentDescription = dest.label) },
                      label = { Text(dest.label) },
                      modifier = Modifier.testTag("bottom_nav_${dest.route}"),
                    )
                  }
              }
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxHeight(),
              ) {
                AppNavDestination.entries.forEach { dest ->
                  NavigationRailItem(
                    selected = currentRoute == dest.route,
                    onClick = { navigateTo(dest) },
                    icon = {
                      if (dest == AppNavDestination.NOTIFICATIONS && unreadNotifCount > 0) {
                        BadgedBox(badge = { Badge { Text("$unreadNotifCount") } }) {
                          Icon(dest.icon, contentDescription = dest.label)
                        }
                      } else {
                        Icon(dest.icon, contentDescription = dest.label)
                      }
                    },
                    label = { Text(dest.label) },
                    modifier = Modifier.testTag("rail_nav_${dest.route}"),
                  )
                }
              }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
              NavHost(
                navController = navController,
                startDestination = AppNavDestination.HOME.route,
              ) {
                composable(AppNavDestination.HOME.route) {
                  HomeScreen(
                    profile = profile,
                    fallbackName = displayName,
                    gpsState = gpsState,
                    esp32State = esp32State,
                    photosState = photosState,
                    notificationsState = notificationsState,
                    onNavigateToMap = { navigateTo(AppNavDestination.MAP) },
                    onNavigateToPhotos = { navigateTo(AppNavDestination.PHOTOS) },
                    onNavigateToEsp32 = { navigateTo(AppNavDestination.ESP32) },
                    onNavigateToAi = { navigateTo(AppNavDestination.AI) },
                    onNavigateToNotifications = { navigateTo(AppNavDestination.NOTIFICATIONS) },
                    onNavigateToProfile = { navigateTo(AppNavDestination.PROFILE) },
                    onRefreshGps = { viewModel.refreshGpsLocation(context) },
                  )
                }
                composable(AppNavDestination.MAP.route) {
                  MapScreen(
                    isDarkTheme = appSettings.isDarkTheme,
                    gpsState = gpsState,
                    selectedMapPoint = selectedMapPoint,
                    photosState = photosState,
                    locationHistoryState = locationHistoryState,
                    onPermissionResult = { viewModel.setLocationPermissionGranted(it) },
                    onRefreshLocation = { viewModel.refreshGpsLocation(context) },
                    onSelectMapPoint = { lat, lng -> viewModel.selectMapPoint(context, lat, lng) },
                    onSaveLocationCheckIn = { lbl, lat, lng, acc ->
                      viewModel.saveLocationCheckIn(context, lbl, lat, lng, acc)
                    },
                    onDeleteLocationEntry = { viewModel.deleteLocationEntry(it) },
                    onClearLocationHistory = { viewModel.clearLocationHistory() },
                    onAskAiAboutPoint = { navigateTo(AppNavDestination.AI) },
                    onTagPhotoHere = { navigateTo(AppNavDestination.PHOTOS) },
                  )
                }
                composable(AppNavDestination.PHOTOS.route) {
                  PhotosScreen(
                    photosState = photosState,
                    sharedPhotosState = sharedPhotosState,
                    gpsState = gpsState,
                    selectedMapPoint = selectedMapPoint,
                    defaultSharePublic = appSettings.defaultSharePublic,
                    onRefreshGps = { viewModel.refreshGpsLocation(context) },
                    onUploadPhoto = { cap, desc, b64, src, gps, customPin, pub, done ->
                      viewModel.uploadPhoto(
                        context = context,
                        caption = cap,
                        description = desc,
                        imageBase64 = b64,
                        source = src,
                        attachCurrentGps = gps,
                        customLatLng = customPin,
                        isPublic = pub,
                        onDone = done,
                      )
                    },
                    onUpdatePhoto = { photo, cap, desc, pub ->
                      viewModel.updatePhotoMetadata(photo, cap, desc, pub)
                    },
                    onDeletePhoto = { viewModel.deletePhoto(it) },
                    onSuggestCaption = { bmp, cb -> viewModel.suggestCaptionForBitmap(bmp, cb) },
                    onAnalyzeWithAi = { photo -> viewModel.analyzePhotoWithAi(context, photo) },
                    onAskAiAboutPhoto = { photo ->
                      preselectedAiPhoto = photo
                      navigateTo(AppNavDestination.AI)
                    },
                    onOpenLocationOnMap = { lat, lng ->
                      viewModel.selectMapPoint(context, lat, lng)
                      navigateTo(AppNavDestination.MAP)
                    },
                  )
                }
                composable(AppNavDestination.ESP32.route) {
                  Esp32CameraScreen(
                    esp32State = esp32State,
                    onAddressChanged = { viewModel.updateEsp32AddressInput(it) },
                    onConnect = { viewModel.connectEsp32(context, it) },
                    onDisconnect = { viewModel.disconnectEsp32(context) },
                    onCaptureSnapshot = { viewModel.captureEsp32Snapshot(context) },
                    onSaveSnapshotToGallery = { bmp, cap, gps ->
                      viewModel.saveEsp32SnapshotToGallery(context, bmp, cap, gps)
                    },
                  )
                }
                composable(AppNavDestination.AI.route) {
                  AiAssistantScreen(
                    messagesState = aiMessagesState,
                    aiStreamState = aiStreamState,
                    photosState = photosState,
                    preselectedPhoto = preselectedAiPhoto,
                    hasLocationContext = gpsState.reading != null || selectedMapPoint != null,
                    onClearPreselectedPhoto = { preselectedAiPhoto = null },
                    onSendMessage = { prompt, bmp, photo, loc ->
                      viewModel.sendAiChatMessage(context, prompt, bmp, photo, loc)
                    },
                    onClearHistory = { viewModel.clearAiHistory() },
                    onClearError = { viewModel.clearAiError() },
                  )
                }
                composable(AppNavDestination.NOTIFICATIONS.route) {
                  NotificationsScreen(
                    notificationsState = notificationsState,
                    systemNotificationsEnabled = appSettings.systemNotificationsEnabled,
                    onToggleSystemNotifications = { viewModel.setSystemNotificationsEnabled(it) },
                    onToggleRead = { viewModel.toggleNotificationRead(it) },
                    onMarkAllRead = { viewModel.markAllNotificationsRead() },
                    onClearAll = { viewModel.clearAllNotifications() },
                  )
                }
                composable(AppNavDestination.PROFILE.route) {
                  val photoCount = (photosState as? DataUiState.Success)?.data?.size ?: 0
                  val locCount = (locationHistoryState as? DataUiState.Success)?.data?.size ?: 0
                  ProfileScreen(
                    profile = profile,
                    fallbackName = displayName,
                    fallbackEmail = email,
                    photoCount = photoCount,
                    locationPinCount = locCount,
                    onUpdateProfile = { name, bio, avatar ->
                      viewModel.updateUserProfile(context, name, bio, avatar)
                    },
                    onSignOut = { signOut(credentialManager, scope) },
                  )
                }
                composable(AppNavDestination.SETTINGS.route) {
                  SettingsScreen(
                    settings = appSettings,
                    onSetDarkTheme = { viewModel.setDarkTheme(it) },
                    onSetAiTone = { viewModel.setAiTone(it) },
                    onSetAutoSuggestCaptions = { viewModel.setAutoSuggestCaptions(it) },
                    onSetSystemNotifications = { viewModel.setSystemNotificationsEnabled(it) },
                    onSetDefaultSharePublic = { viewModel.setDefaultSharePublic(it) },
                    onClearLocationHistory = { viewModel.clearLocationHistory() },
                    onOpenEsp32Settings = { navigateTo(AppNavDestination.ESP32) },
                    onDeleteAccountData = {
                      viewModel.deleteAccountDataAndSignOut { signOut(credentialManager, scope) }
                    },
                  )
                }
              }
            }
          }
        }
      }

      if (useRail) {
        scaffoldBody()
      } else {
        ModalNavigationDrawer(
          drawerState = drawerState,
          drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
              )
              Text(
                text = stringResource(R.string.developer_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
              )
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              AppNavDestination.entries.forEach { dest ->
                NavigationDrawerItem(
                  label = { Text(dest.label) },
                  selected = currentRoute == dest.route,
                  icon = { Icon(dest.icon, contentDescription = null) },
                  onClick = {
                    scope.launch { drawerState.close() }
                    navigateTo(dest)
                  },
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
              }
              Spacer(modifier = Modifier.weight(1f))
            }
          },
        ) {
          scaffoldBody()
        }
      }
    }
  }
}
