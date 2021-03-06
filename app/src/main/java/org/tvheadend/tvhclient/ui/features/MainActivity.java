package org.tvheadend.tvhclient.ui.features;

import android.app.SearchManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManagerListener;

import org.tvheadend.tvhclient.MainApplication;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.data.repository.AppRepository;
import org.tvheadend.tvhclient.data.service.SyncStateReceiver;
import org.tvheadend.tvhclient.ui.base.BaseActivity;
import org.tvheadend.tvhclient.ui.base.utils.SnackbarUtils;
import org.tvheadend.tvhclient.ui.features.download.DownloadPermissionGrantedInterface;
import org.tvheadend.tvhclient.ui.features.epg.ProgramGuideFragment;
import org.tvheadend.tvhclient.ui.features.navigation.NavigationDrawer;
import org.tvheadend.tvhclient.ui.features.navigation.NavigationDrawerCallback;
import org.tvheadend.tvhclient.ui.features.playback.external.CastSessionManagerListener;
import org.tvheadend.tvhclient.ui.features.search.SearchRequestInterface;
import org.tvheadend.tvhclient.util.MiscUtils;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

// TODO what happens when no connection to the server is active and the user presses an action in a notification?

public class MainActivity extends BaseActivity implements NavigationDrawerCallback, SearchView.OnQueryTextListener, SearchView.OnSuggestionListener, SyncStateReceiver.Listener {

    @BindView(R.id.sync_progress)
    ProgressBar syncProgress;

    private MenuItem searchMenuItem;
    private SearchView searchView;
    private MenuItem mediaRouteMenuItem;
    private IntroductoryOverlay introductoryOverlay;
    private CastSession castSession;
    private CastContext castContext;
    private CastStateListener castStateListener;
    private SessionManagerListener<CastSession> castSessionManagerListener;
    private SyncStateReceiver syncStateReceiver;

    private NavigationDrawer navigationDrawer;
    private int selectedNavigationMenuId;
    private boolean isUnlocked;
    private boolean isDualPane;

    @Inject
    protected SharedPreferences sharedPreferences;
    @Inject
    protected AppRepository appRepository;
    private boolean isSavedInstanceStateNull;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(MiscUtils.getThemeId(this));
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
        MiscUtils.setLanguage(this);
        ButterKnife.bind(this);

        MainApplication.getComponent().inject(this);

        syncStateReceiver = new SyncStateReceiver(this);
        isUnlocked = MainApplication.getInstance().isUnlocked();
        isDualPane = findViewById(R.id.details) != null;

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        navigationDrawer = new NavigationDrawer(this, savedInstanceState, toolbar, appRepository, this);
        navigationDrawer.createHeader();
        navigationDrawer.createMenu();

        // When the activity is created it got called by the main activity. Get the initial
        // navigation menu position and show the associated fragment with it. When the device
        // was rotated just restore the position from the saved instance.
        if (savedInstanceState == null) {
            isSavedInstanceStateNull = true;
            //noinspection ConstantConditions
            selectedNavigationMenuId = Integer.parseInt(sharedPreferences.getString("start_screen", getResources().getString(R.string.pref_default_start_screen)));
        } else {
            isSavedInstanceStateNull = false;
            selectedNavigationMenuId = savedInstanceState.getInt("navigationMenuId", NavigationDrawer.MENU_CHANNELS);
        }

        boolean showCastingMiniController = isUnlocked && sharedPreferences.getBoolean("casting_minicontroller_enabled",
                getResources().getBoolean(R.bool.pref_default_casting_minicontroller_enabled));
        View miniController = findViewById(R.id.cast_mini_controller);
        miniController.setVisibility(showCastingMiniController ? View.VISIBLE : View.GONE);

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
            navigationDrawer.handleSelection(fragment);
        });

        castContext = MiscUtils.getCastContext(this);
        if (castContext != null) {
            Timber.d("Casting is available");
            castSessionManagerListener = new CastSessionManagerListener(this, castSession);
            castStateListener = newState -> {
                Timber.d("Cast state changed to " + newState);
                if (newState != CastState.NO_DEVICES_AVAILABLE) {
                    showIntroductoryOverlay();
                }
            };
        } else {
            Timber.d("Casting is not available, casting will no be enabled");
        }

        // Update the drawer menu so that all available menu items are
        // shown in case the recording counts have changed or the user has
        // bought the unlocked version to enable all features
        navigationDrawer.showConnectionsInDrawerHeader();
        navigationDrawer.startObservingViewModels();
        handleDrawerItemSelected(selectedNavigationMenuId);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // add the values which need to be saved from the drawer and header to the bundle
        outState = navigationDrawer.saveInstanceState(outState);
        outState.putInt("navigationMenuId", selectedNavigationMenuId);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(syncStateReceiver, new IntentFilter(SyncStateReceiver.ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStateReceiver);
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (castContext != null) {
            return castContext.onDispatchVolumeKeyEventBeforeJellyBean(event) || super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onResume() {
        super.onResume();
        castSession = MiscUtils.getCastSession(this);
    }

    @Override
    public void onPause() {
        if (castContext != null) {
            try {
                castContext.removeCastStateListener(castStateListener);
                castContext.getSessionManager().removeSessionManagerListener(castSessionManagerListener, CastSession.class);
            } catch (IllegalStateException e) {
                Timber.e("Could not remove cast state listener or get cast session manager");
            }
        }
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_options_menu, menu);

        mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        try {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        } catch (Exception e) {
            Timber.e("Could not setup media route button", e);
        }

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            searchMenuItem = menu.findItem(R.id.menu_search);
            searchView = (SearchView) searchMenuItem.getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setIconifiedByDefault(true);
            searchView.setOnQueryTextListener(this);
            searchView.setOnSuggestionListener(this);

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
            if (fragment instanceof SearchRequestInterface
                    && fragment.isVisible()) {
                searchView.setQueryHint(((SearchRequestInterface) fragment).getQueryHint());
            }
        }
        return true;
    }

    private void showIntroductoryOverlay() {
        if (introductoryOverlay != null) {
            introductoryOverlay.remove();
        }
        if ((mediaRouteMenuItem != null) && mediaRouteMenuItem.isVisible()) {
            new Handler().post(() -> {
                introductoryOverlay = new IntroductoryOverlay.Builder(
                        MainActivity.this, mediaRouteMenuItem)
                        .setTitleText(getString(R.string.intro_overlay_text))
                        .setOverlayColor(R.color.primary)
                        .setSingleTime()
                        .setOnOverlayDismissedListener(() -> introductoryOverlay = null)
                        .build();
                introductoryOverlay.show();
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                && permissions[0].equals("android.permission.WRITE_EXTERNAL_STORAGE")) {
            Timber.d("Storage permission granted");
            Fragment fragment = getSupportFragmentManager().findFragmentById(isDualPane ? R.id.details : R.id.main);
            if (fragment instanceof DownloadPermissionGrantedInterface) {
                ((DownloadPermissionGrantedInterface) fragment).downloadRecording();
            }
        } else {
            Timber.d("Storage permission could not be granted");
        }
    }

    /**
     * Called when a menu item from the navigation drawer was selected. It loads
     * and shows the correct fragment or fragments depending on the selected
     * menu item.
     *
     * @param position Selected position within the menu array
     */
    private void handleDrawerItemSelected(int position) {
        Fragment fragment;
        boolean addFragmentToBackStack = sharedPreferences.getBoolean("navigation_history_enabled",
                getResources().getBoolean(R.bool.pref_default_navigation_history_enabled));

        // Get the already created fragment when the device orientation changes. In this
        // case the saved instance is not null. This avoids recreating fragments after
        // every orientation change which would reset any saved states in these fragments.
        if (isSavedInstanceStateNull || selectedNavigationMenuId != position) {
            Timber.d("Saved instance is null or selected id has changed, creating new fragment");
            fragment = navigationDrawer.getFragmentFromSelection(position);
        } else {
            Timber.d("Saved instance is not null, trying to existing fragment");
            fragment = getSupportFragmentManager().findFragmentById(R.id.main);
            addFragmentToBackStack = false;
        }

        if (fragment != null) {
            // Save the menu position so we know which one was selected
            selectedNavigationMenuId = position;

            // Remove the old details fragment if there is one so that it is not visible when
            // the new main fragment is loaded. It takes a while until the new details
            // fragment is visible. This prevents showing wrong data when switching screens.
            if (isDualPane) {
                Fragment detailsFragment = getSupportFragmentManager().findFragmentById(R.id.details);
                if (detailsFragment != null) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .remove(detailsFragment)
                            .commit();
                }
            }

            // Show the new fragment that represents the selected menu entry.
            FragmentTransaction ft = getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main, fragment);
            // Only add the fragment to the back stack if a new one has been created.
            // Existing fragments that were already available due to an orientation
            // change shall not be added to the back stack. This prevents having to
            // press the back key as often as the device was rotated.
            if (addFragmentToBackStack) {
                ft.addToBackStack(null);
            }
            ft.commit();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        switch (selectedNavigationMenuId) {
            case NavigationDrawer.MENU_STATUS:
            case NavigationDrawer.MENU_UNLOCKER:
            case NavigationDrawer.MENU_HELP:
                // Do not show these menus in one of those fragments
                mediaRouteMenuItem.setVisible(false);
                menu.findItem(R.id.menu_search).setVisible(false);
                menu.findItem(R.id.menu_refresh).setVisible(false);
                break;
            default:
                mediaRouteMenuItem.setVisible(isUnlocked);
                break;
        }
        return true;
    }

    @Override
    public void onNavigationMenuSelected(int id) {
        Timber.d("Newly selected menu id is " + id + ", current selection id is " + selectedNavigationMenuId);
        if (selectedNavigationMenuId != id) {
            handleDrawerItemSelected(id);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        searchMenuItem.collapseActionView();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
        if (fragment instanceof SearchRequestInterface
                && fragment.isVisible()) {
            ((SearchRequestInterface) fragment).onSearchRequested(query);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (newText.length() >= 3) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
            if (fragment instanceof SearchRequestInterface
                    && fragment.isVisible()
                    && !(fragment instanceof ProgramGuideFragment)) {
                ((SearchRequestInterface) fragment).onSearchRequested(newText);
            }
        }
        return true;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        searchMenuItem.collapseActionView();
        // Set the search query and return true so that the onQueryTextSubmit
        // is called. This is required to pass additional data to the search activity
        Cursor cursor = (Cursor) searchView.getSuggestionsAdapter().getItem(position);
        String suggestion = cursor.getString(cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1));
        searchView.setQuery(suggestion, true);
        return true;
    }

    @Override
    public void onBackPressed() {
        Timber.d("Back pressed");
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
        if (fragment instanceof SearchRequestInterface
                && fragment.isVisible()) {
            Timber.d("Found fragment");
            if (!((SearchRequestInterface) fragment).onSearchResultsCleared()) {
                Timber.d("Search results were not cleared");
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSyncStateChanged(SyncStateReceiver.State state, String message, String details) {
        switch (state) {
            case CLOSED:
            case FAILED:
                Timber.d("Connection failed or closed");
                SnackbarUtils.sendSnackbarMessage(this, message);
                onNetworkAvailabilityChanged(false);
                break;

            case CONNECTING:
                Timber.d("Connecting");
                SnackbarUtils.sendSnackbarMessage(this, message);
                break;

            case CONNECTED:
                Timber.d("Connected");
                SnackbarUtils.sendSnackbarMessage(this, message);
                break;

            case SYNC_STARTED:
                Timber.d("Sync started, showing progress bar");
                syncProgress.setVisibility(View.VISIBLE);
                SnackbarUtils.sendSnackbarMessage(this, message);
                break;

            case SYNC_IN_PROGRESS:
                Timber.d("Sync in progress, updating progress bar");
                syncProgress.setVisibility(View.VISIBLE);
                break;

            case SYNC_DONE:
                Timber.d("Sync done, hiding progress bar");
                syncProgress.setVisibility(View.GONE);
                SnackbarUtils.sendSnackbarMessage(this, message);
                break;
        }
    }
}
