package org.stuartresearch.snapzuisfunner;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.etsy.android.grid.StaggeredGridView;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeader;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import org.parceler.Parcels;
import org.stuartresearch.SnapzuAPI.Post;
import org.stuartresearch.SnapzuAPI.Tribe;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import icepick.Icepick;
import icepick.Icicle;


public class MainActivity extends AppCompatActivity implements Drawer.OnDrawerItemClickListener,
        AccountHeader.OnAccountHeaderListener, SwipeRefreshLayout.OnRefreshListener {

    public static final String address = "http://snapzu.com/";
    public static final String PREF_NAME = "preferences";
    public static final String PREF_PROFILE_ID = "profile_id";

    public static final int LOGIN_REQUEST = 1;
    public static final String SORTING_TRENDING = "/trending";
    public static final String SORTING_NEW = "/new";
    public static final String SORTING_NEWEST = "/newest";
    public static final String SORTING_TOPSCORES = "/topscores";

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.grid_view) StaggeredGridView gridView;
    @Bind(R.id.pull_to_refresh) SwipeRefreshLayout refresh;

    public static Bus bus = new Bus(ThreadEnforcer.MAIN);

    Drawer drawer;
    AccountHeader accountHeader;
    GridAdapter mAdapter;
    @Icicle Tribe[] tribes;
    List<Profile> profiles;

    //ATTN: HAS TO BE STATIC - Don't ask me why.
    static ArrayList<Post> posts = new ArrayList<>(50);

    @Icicle String sorting = SORTING_TRENDING;
    @Icicle Tribe tribe = new Tribe("all", "http://snapzu.com/list");
    Post post;
    @Icicle int page = 1;
    @Icicle int drawerSelection = 5;
    @Icicle Profile profile;

    EndlessScrollListener endlessScrollListener;

    PrimaryDrawerItem drawerProfile;
    PrimaryDrawerItem drawerMessages;
    PrimaryDrawerItem drawerOpenUser;
    PrimaryDrawerItem drawerOpenTribe;
    DividerDrawerItem drawerDivider;
    PrimaryDrawerItem drawerSettings;

    ProfileSettingDrawerItem profileSettingsAdd;
    ProfileSettingDrawerItem profileSettingsManage;
    ProfileDrawerItem profileLoggedOut;

    IProfile iProfile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        bus.register(this);

        setSupportActionBar(toolbar);

        refresh.setOnRefreshListener(this);

        // Set titles
        presentTitle();

        // Load avatars with Picasso in header
        DrawerImageLoader.init(new DrawerImageLoader.IDrawerImageLoader() {
            @Override
            public void set(ImageView imageView, Uri uri, Drawable drawable) {
                Glide.with(imageView.getContext()).load(uri).placeholder(R.drawable.profile).into(imageView);
            }

            @Override
            public void cancel(ImageView imageView) {
            }

            @Override
            public Drawable placeholder(Context context) {
                return null;
            }
        });

        //get profile
        if (profile == null) {
            profile = getSavedProfile();
        }

        buildDrawerItems();

        // Build account header_back
        accountHeader = generateAccounterHeader(savedInstanceState, profile == null ? -1 : profile.getId().intValue());
        drawer = generateDrawer(savedInstanceState);

        //Make hamburger appear and function
        drawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);

        // Fill tribes list
        if (tribes != null) {
            showTribes(tribes);
        }
        downloadTribes();
        downloadMessagesAndLevel();

        // fill grid with posts
        if (posts.isEmpty()) {
            downloadPosts();
        }

        //Gridview business
        mAdapter = new GridAdapter(this, R.layout.grid_item, posts);
        gridView.setAdapter(mAdapter);




        refresh.setEnabled(false);

        if (savedInstanceState == null) {
            // Receive updates from other components

            // bug 77712
            refresh.post(() -> refresh.setRefreshing(true));

            // Fill posts
        } else {
            // or else will not loading on orientation change
            endlessScrollListener = new EndlessScrollListener();
            gridView.setOnScrollListener(endlessScrollListener);

        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_sort) {
            presentSortingDialog();
            return true;
        } else if (id == R.id.action_compose) {
            if (profile == null) {
                Toast.makeText(this, "You are logged out", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, ActionActivity.class);
                intent.putExtra("url", tribe.getLink());
                intent.putExtra("cookies", profile.cookies);
                startActivity(intent);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
        outState = accountHeader.saveInstanceState(outState);
        outState = drawer.saveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bus.unregister(this);
    }

    // CLOSE DRAWER ON BACK
    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST) {
            if (resultCode == RESULT_OK) {
                addProfile((Profile) Parcels.unwrap(data.getParcelableExtra("profile")));
            }
        }
    }

    public void buildDrawerItems() {
        drawerProfile = new PrimaryDrawerItem().withName("Profile").withIcon(R.drawable.ic_account_box_black_18dp).withCheckable(false);
        drawerMessages = new PrimaryDrawerItem().withName("Messages").withIcon(R.drawable.ic_message_black_18dp).withCheckable(false);
        drawerOpenUser = new PrimaryDrawerItem().withName("Open User").withIcon(R.drawable.ic_group_black_18dp).withCheckable(false);
        drawerOpenTribe = new PrimaryDrawerItem().withName("Open Tribe").withIcon(R.drawable.ic_filter_tilt_shift_black_18dp).withCheckable(false);
        drawerDivider = new DividerDrawerItem();
        drawerSettings = new PrimaryDrawerItem().withName("Settings").withIcon(R.drawable.ic_settings_black_18dp).withCheckable(false);


        profileSettingsAdd = new ProfileSettingDrawerItem().withName("Add Account").withIcon(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_add).actionBarSize().paddingDp(5).colorRes(R.color.material_drawer_primary_text)).withIdentifier(-2);
        profileSettingsManage = new ProfileSettingDrawerItem().withName("Manage Accounts").withIcon(GoogleMaterial.Icon.gmd_settings).withIdentifier(-3);
        profileLoggedOut = new ProfileDrawerItem().withName("Logged Out").withIdentifier(-1);

    }

    // DRAWER CLICKED
    @Override
    public boolean onItemClick(AdapterView<?> adapterView, View view, int i, long l, IDrawerItem iDrawerItem) {

        Intent intent;
        switch(i) {
            // Profile
            case 0:
                if (profile == null) {
                    Toast.makeText(this, "You are logged out", Toast.LENGTH_SHORT).show();
                    break;
                }
                intent = new Intent(this, ActionActivity.class);
                intent.putExtra("url", address + profile.getName());
                intent.putExtra("cookies", profile.cookies);
                startActivity(intent);
                break;
            // Messages
            case 1:
                if (profile == null) {
                    Toast.makeText(this, "You are logged out", Toast.LENGTH_SHORT).show();
                    break;
                }
                intent = new Intent(this, ActionActivity.class);
                intent.putExtra("url", address + profile.getName() + "/message");
                intent.putExtra("cookies", profile.cookies);
                startActivity(intent);
                break;
            // Open User
            case 2:
                presentOpenUserDialog();
                break;
            // Open Tribe
            case 3:
                presentOpenTribeDialog();
                break;
            // Settings
            case -1:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            // Tribe Selected
            default:
                drawerSelection = i;
                tribeSelected(tribes[i - 5]);
                break;

        }
        return false;
    }

    // ACCOUNT SELECTED
    @Override
    public boolean onProfileChanged(View view, IProfile iProfile, boolean currentProfile) {
        if(currentProfile) {
            return false;
        }

        switch (iProfile.getIdentifier()) {
            case -1:
                //LOGGED OUT
                onLoggedOut();
                break;
            case -2:
                // ADD ACCOUNT
                Intent i = new Intent(this, Login.class);
                startActivityForResult(i, 1);
                break;
            case -3:
                // MANAGE ACCOUNTS
                new AlertDialog.Builder(this).setTitle("Delete All Accounts?")
                        .setPositiveButton("DELETE", (dialog, which) -> {
                            MainActivity.bus.post(new MainActivity.DeleteProfiles());
                        })
                        .setNegativeButton("CANCEL", null).show();
                break;
            default:
                // ACCOUNT SELECTED
                this.iProfile = iProfile;
                this.profile = profiles.get(iProfile.getIdentifier());
                Toast.makeText(this, String.format("Logged in as %s", profile.getName()), Toast.LENGTH_SHORT).show();
                clearTribes();
                downloadTribes();
                downloadMessagesAndLevel();
                presentMessageCount("");
                presentProfileLevel("");
                break;
        }


        //drawer.closeDrawer();

        //false if you have not consumed the event and it should close the drawer
        return false;
    }

    public void onLoggedOut() {
        this.profile = null;
        this.iProfile = null;
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        clearTribes();
        downloadTribes();
        presentMessageCount("");
        presentProfileLevel("");
    }


    // POST IS SELECTED
    @OnItemClick(R.id.grid_view)
    public void grid_selected(int position) {
        this.post = posts.get(position);
        Intent i = new Intent(this, PostActivity.class);
        i.putExtra("url", this.post.getLink());
        if (profile != null) {
            i.putExtra("cookies", profile.cookies);
        }
        i.putExtra("post", Parcels.wrap(new PostActivity.SinglePost(post)));
        startActivity(i);
    }

    private void downloadPosts() {
        if (tribe.getName().equals("all")) {
            if (page == 1) {
                new PopulatePosts(tribe, sorting, "").execute();
                page++;
            } else {
                new PopulatePosts(tribe, "", Integer.toString(page++)).execute();
            }
        } else {
            new PopulatePosts(tribe, sorting, Integer.toString(page++)).execute();
        }
    }

    private void downloadMessagesAndLevel() {
        if (profile != null) {
            new PopulateMessagesAndLevel(profile.getCookies()).execute();
        } else {
            presentMessageCount("");
            presentProfileLevel("");
        }
    }

    private void downloadTribes() {
        new PopulateTribes(profile).execute();
    }

    private void clearTribes() {
        if (accountHeader.isSelectionListShown())
            accountHeader.toggleSelectionList(this);

        drawer.removeAllItems();
        drawer.addItems(drawerProfile, drawerMessages, drawerOpenUser, drawerOpenTribe, drawerDivider);
    }

    private void tribeSelected(Tribe tribe) {
        refresh.setRefreshing(true);
        this.tribe = tribe;
        this.sorting = SORTING_TRENDING;
        if (endlessScrollListener != null)
            endlessScrollListener.setLoading(true);
        hideCards();
        downloadPosts();

        presentTitle();
    }

    public void hideCards() {
        posts.clear();
        mAdapter.notifyDataSetInvalidated();
        page = 1;
        gridView.setVisibility(View.GONE);
    }


    // PULLED TO REFRESH
    @Override
    public void onRefresh() {
        tribeSelected(tribe);
    }

    // SENT FROM PopulatePosts
    @Subscribe
    public void onPostsReady(PopulatePosts.PostsPackage postsPackage) {
        showPosts(postsPackage.posts);
    }

    public void showPosts (Post[] posts) {
        for (int i = 0; i < posts.length; i++) {
            this.posts.add(posts[i]);
        }



        if (tribe.getName().equals("all")) {
            mAdapter.removeTribe();
        } else {
            mAdapter.setTribe(tribe.getName());
        }

        mAdapter.notifyDataSetChanged();
        gridView.setVisibility(View.VISIBLE);

        refresh.setRefreshing(false);
        if (posts.length > 5) {
            endlessScrollListener = new EndlessScrollListener();

        }
        gridView.setOnScrollListener(endlessScrollListener);
    }

    // SENT FROM PopulatePosts
    @Subscribe
    public void onPostsError(PopulatePosts.PostsError postsError) {
        Toast.makeText(this, "Network errors not implemented", Toast.LENGTH_SHORT).show();
        refresh.setRefreshing(false);
    }

    // SENT FROM PopulateTribes
    @Subscribe
    public void onTribesReady(PopulateTribes.TribesPackage tribesPackage) {
        clearTribes();
        showTribes(tribesPackage.tribes);
    }

    @Subscribe
    public void onMessagesAndLevelReady(PopulateMessagesAndLevel.MessagesAndLevelPackage messagesAndLevelPackage) {
        presentProfileLevel(messagesAndLevelPackage.data[0]);
        presentMessageCount(messagesAndLevelPackage.data[1]);
    }

    @Subscribe
    public void onMessagesAndLevelError(PopulateMessagesAndLevel.MessagesAndLevelError messagesAndLevelError) {
        Toast.makeText(this, "Network errors not implemented", Toast.LENGTH_SHORT).show();
    }

    public void showTribes(Tribe[] tribes) {

        for (int i = 5; i < drawer.getDrawerItems().size(); i++) {
            drawer.removeItem(i);
        }


        this.tribes = tribes;


        for (int i = 0; i < tribes.length; i++) {
            drawer.addItem(new SecondaryDrawerItem().withName(this.tribes[i].getName()));
        }

        drawer.getAdapter().notifyDataSetChanged();

    }

    // SENT FROM PopulateTribes
    @Subscribe
    public void onTribesError(PopulateTribes.TribesError tribesError) {
        Toast.makeText(this, "Network errors not implemented", Toast.LENGTH_SHORT).show();
    }

    public void presentTitle() {
        getSupportActionBar().setTitle(tribe.getName().toUpperCase());
        getSupportActionBar().setSubtitle(this.sorting.substring(1));
    }

    public void presentMessageCount(String count) {
        drawerMessages.setBadge(count);
        drawer.removeItem(1);
        drawer.addItem(drawerMessages, 1);
    }

    public void presentProfileLevel(String level) {
        drawerProfile.setBadge(level);
        drawer.removeItem(0);
        drawer.addItem(drawerProfile, 0);
    }


    @Subscribe
    public void onLoadMoreRequest(EndlessScrollListener.LoadMorePackage loadMorePackage) {
        refresh.setRefreshing(true);
        downloadPosts();
    }

    public void presentSortingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sorting");
        final int itemsId;
        if (tribe.getName().equals("all")) {
            itemsId = R.array.sorting_all;
        } else {
            itemsId = R.array.sorting_other;
        }

        int checkedItem;
        switch (sorting) {
            default:
            case SORTING_TRENDING:
                checkedItem = 0;
                break;
            case SORTING_NEWEST:
            case SORTING_NEW:
                checkedItem = 1;
                break;
            case SORTING_TOPSCORES:
                checkedItem = 2;
                break;
        }
        builder.setSingleChoiceItems(itemsId, checkedItem, (dialog, which) -> {
            if (itemsId == R.array.sorting_all) {
                switch (which) {
                    case 0:
                        sorting = SORTING_TRENDING;
                        break;
                    case 1:
                        sorting = SORTING_NEW;
                        break;
                }

            } else {
                switch (which) {
                    case 0:
                        sorting = SORTING_TRENDING;
                        break;
                    case 1:
                        sorting = SORTING_NEWEST;
                        break;
                    case 2:
                        sorting = SORTING_TOPSCORES;
                        break;
                }
            }

            dialog.dismiss();

            presentTitle();
            refresh.setRefreshing(true);
            endlessScrollListener.setLoading(true);
            hideCards();
            downloadPosts();
        });

        builder.show();

    }

    public void presentOpenUserDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_open_user, null);
        final TextView userText = (TextView) view.findViewById(R.id.open_user_text);

        builder.setView(view);

        builder.setTitle("Open User");

        builder.setPositiveButton("VIEW", (dialog, which) -> {
            String user = userText.getText().toString();
            if (user.isEmpty()) {
                user = getString(R.string.example_user);
            }
            onOpenUser(user);
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }

    public void onOpenUser(String user) {
        Intent intent = new Intent(getApplicationContext(), ActionActivity.class);
        intent.putExtra("url", address + user);
        startActivity(intent);
    }

    public void presentOpenTribeDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_open_tribe, null);
        final TextView tribeText = (TextView) view.findViewById(R.id.open_tribe_text);

        builder.setView(view);

        builder.setTitle("Open Tribe");

        builder.setPositiveButton("VIEW", (dialog, which) -> {
            String tribe1 = tribeText.getText().toString();
            if (tribe1.isEmpty()) {
                tribe1 = getString(R.string.example_tribe);
            }
            tribeSelected(new Tribe(tribe1, address + "/t/" + tribe1));
            drawer.setSelectionByIdentifier(-12, false);
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }



    public AccountHeader generateAccounterHeader(Bundle savedInstanceState, int selectedID) {
        AccountHeaderBuilder headerBuilder = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.header)
                .withSelectionListEnabled(true)
                .withSelectionListEnabledForSingleProfile(true)
                .withSavedInstance(savedInstanceState)
                .withOnAccountHeaderListener(this);

        try {
            profiles = Profile.listAll(Profile.class);
        } catch (Exception e) {
            profiles = new ArrayList<>(5);
        }

        int identifier = -1;

        for (int i = 0; i < profiles.size(); i++) {
            Profile cursor = profiles.get(i);
            if (selectedID == cursor.getId()) {
                identifier = i;
            }
            headerBuilder.addProfiles(cursor.toProfileDrawerItem(i).withIdentifier(i));
        }

        headerBuilder.addProfiles(profileLoggedOut, profileSettingsAdd, profileSettingsManage);

        AccountHeader accountHeader = headerBuilder.build();

        accountHeader.setActiveProfile(identifier);

        return accountHeader;
    }

    public Drawer generateDrawer(Bundle savedInstanceState) {
        return new DrawerBuilder().withActivity(this).withTranslucentStatusBar(false)
                .withActionBarDrawerToggle(true)
                .withToolbar(toolbar)
                .withTranslucentStatusBarProgrammatically(true)
                .withAccountHeader(accountHeader)
                .withSelectedItem(drawerSelection)
                .addDrawerItems(drawerProfile, drawerMessages, drawerOpenUser, drawerOpenTribe, drawerDivider)
                .addStickyDrawerItems(
                        new PrimaryDrawerItem().withName("Settings").withIcon(R.drawable.ic_settings_black_18dp).withCheckable(false)
                ).withOnDrawerItemClickListener(this)
                .withSavedInstance(savedInstanceState)
                .build();
    }



    public void addProfile(Profile profile) {
        removeProfile(profile);
        this.profile = profile;
        int pos = profiles.size();
        profiles.add(profile);
        profile.save();
        saveProfileToPreferences();
        iProfile = profile.toProfileDrawerItem(pos);
        accountHeader.addProfile(iProfile, 0);
    }

    public void removeProfile(Profile profile) {
        for (int i = 0; i < profiles.size(); i++) {
            Profile pos = profiles.get(i);
            if (profile.equals(pos)) {
                pos.delete();
                profiles.remove(i);
            }
        }
    }

    public void saveProfileToPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putLong(PREF_PROFILE_ID, profile.getId());
        editor.commit();

    }

    public Profile getSavedProfile() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return Profile.findById(Profile.class, sharedPreferences.getLong(PREF_PROFILE_ID, -1));
    }

    @Subscribe
    public void onProfilePicture(AddPictureToProfile.ProfilePicturePackage profilePicturePackage) {
        addProfile(profilePicturePackage.profile);
    }

    @Subscribe
    public void onProfilePictureError(AddPictureToProfile.ProfilePictureError profilePictureError) {
        Toast.makeText(this, "Profile picture errors not implemented", Toast.LENGTH_SHORT).show();
    }

    public class DeleteProfiles {}

    @Subscribe
    public void onDeleteProfiles(DeleteProfiles deleteProfiles) {
        int numUserProfiles = accountHeader.getProfiles().size() - 3;
        for (int i = 0; i < numUserProfiles; i++) {
            accountHeader.removeProfile(0);
        }

        profiles = new ArrayList<>(5);

        Profile.deleteAll(Profile.class);

        accountHeader.setActiveProfile(-1);

        if (accountHeader.isSelectionListShown()) {
            accountHeader.toggleSelectionList(this);
        }

        onLoggedOut();
    }

}
