package org.stuartresearch.snapzuisfunner;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.etsy.android.grid.StaggeredGridView;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeader;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

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

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.grid_view) StaggeredGridView gridView;
    @Bind(R.id.pull_to_refresh) SwipeRefreshLayout refresh;

    public static Bus bus = new Bus(ThreadEnforcer.MAIN);

    Drawer drawer;
    GridAdapter mAdapter;
    @Icicle Tribe[] tribes;

    //ATTN: HAS TO BE STATIC - Don't ask me why.
    static ArrayList<Post> posts = new ArrayList<>(50);

    @Icicle String sorting = "/trending";
    @Icicle Tribe tribe = new Tribe("all", "http://snapzu.com/list");
    Post post;
    @Icicle int page = 1;
    @Icicle int drawerSelection = 5;
    @Icicle String profile = "";


    EndlessScrollListener endlessScrollListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        refresh.setOnRefreshListener(this);

        // Set titles
        updateTitle();

        // Build account header_back
        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.header)
                .withOnAccountHeaderListener(this)
                .build();

        drawer = new DrawerBuilder().withActivity(this).withTranslucentStatusBar(false)
                .withActionBarDrawerToggle(true)
                .withToolbar(toolbar)
                .withSelectedItem(drawerSelection)
                .addDrawerItems(
                        new PrimaryDrawerItem().withName("Profile").withIcon(R.drawable.ic_account_box_black_18dp).withCheckable(false),
                        new PrimaryDrawerItem().withName("Messages").withIcon(R.drawable.ic_message_black_18dp).withCheckable(false),
                        new PrimaryDrawerItem().withName("Open User").withIcon(R.drawable.ic_group_black_18dp).withCheckable(false),
                        new PrimaryDrawerItem().withName("Open Tribe").withIcon(R.drawable.ic_filter_tilt_shift_black_18dp).withCheckable(false),
                        new DividerDrawerItem()
                )
                .addStickyDrawerItems(
                        new PrimaryDrawerItem().withName("Settings").withIcon(R.drawable.ic_settings_black_18dp).withCheckable(false)
                ).withOnDrawerItemClickListener(this)
                .build();

        //Make hamburger appear and function
        drawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);



        // Fill tribes list
        if (tribes == null)
            downloadTribes();
        else
            showTribes(tribes);

        //Gridview business
        mAdapter = new GridAdapter(this, R.layout.grid_item, posts);
        gridView.setAdapter(mAdapter);


        refresh.setEnabled(false);

        if (savedInstanceState == null) {
            // Receive updates from other components
            bus.register(this);

            // bug 77712
            refresh.post(new Runnable() {
                @Override
                public void run() {
                    refresh.setRefreshing(true);
                }
            });

            // Fill posts
            downloadPosts();
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
            showSortingDialog();
            return true;
        } else if (id == R.id.action_compose) {
            Toast.makeText(this, "Compose not implemented.", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
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

    // DRAWER CLICKED
    @Override
    public boolean onItemClick(AdapterView<?> adapterView, View view, int i, long l, IDrawerItem iDrawerItem) {

        switch(i) {
            // Profile
            case 0:
                Toast.makeText(this, "Profile is not implemented", Toast.LENGTH_SHORT).show();
                break;
            // Messages
            case 1:
                Toast.makeText(this, "Messages is not implemented", Toast.LENGTH_SHORT).show();
                break;
            // Open User
            case 2:
                Toast.makeText(this, "Open User is not implemented", Toast.LENGTH_SHORT).show();
                break;
            // Open Tribe
            case 3:
                Toast.makeText(this, "Open Tribe not implemented", Toast.LENGTH_SHORT).show();
                break;
            // Settings
            case -1:
                Toast.makeText(this, "Settings is not implemented", Toast.LENGTH_SHORT).show();
                Intent login = new Intent(this, Login.class);
                startActivity(login);
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
    public boolean onProfileChanged(View view, IProfile iProfile, boolean b) {
        Toast.makeText(this, String.format("Almost logged in as %s", iProfile.getName()), Toast.LENGTH_SHORT).show();
        return false;
    }


    // POST IS SELECTED
    @OnItemClick(R.id.grid_view)
    public void grid_selected(int position) {
        this.post = posts.get(position);
        Intent i = new Intent(this, PostActivity.class);
        i.putExtra("url", this.post.getLink());
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

    private void downloadTribes() {
        new PopulateTribes(drawer).execute();
    }

    private void tribeSelected(Tribe tribe) {
        refresh.setRefreshing(true);
        this.tribe = tribe;
        this.sorting = "/trending";
        if (endlessScrollListener != null)
            endlessScrollListener.setLoading(true);
        hideCards();
        downloadPosts();

        updateTitle();
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

        mAdapter.notifyDataSetChanged();
        gridView.setVisibility(View.VISIBLE);

        refresh.setRefreshing(false);
        endlessScrollListener = new EndlessScrollListener();
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
        showTribes(tribesPackage.tribes);
    }

    public void showTribes(Tribe[] tribes) {
        this.tribes = tribes;

        for (int i = 5; i < drawer.getDrawerItems().size(); i++) {
            drawer.removeItem(i);
        }

        for (int i = 0; i < tribes.length; i++) {
            drawer.addItem(new SecondaryDrawerItem().withName(this.tribes[i].getName()));
        }
    }

    // SENT FROM PopulateTribes
    @Subscribe
    public void onTribesError(PopulateTribes.TribesError tribesError) {
        Toast.makeText(this, "Network errors not implemented", Toast.LENGTH_SHORT).show();
    }

    public void updateTitle() {
        getSupportActionBar().setTitle(tribe.getName().toUpperCase());
        getSupportActionBar().setSubtitle(this.sorting.substring(1));
    }

    public static class SinglePostPackage {
        public final Post post;

        public SinglePostPackage(Post post) {
            this.post = post;
        }
    }

    @Produce
    public SinglePostPackage produceSinglePostPackage() {
        return new SinglePostPackage(this.post);
    }

    @Subscribe
    public void onLoadMoreRequest(EndlessScrollListener.LoadMorePackage loadMorePackage) {
        refresh.setRefreshing(true);
        downloadPosts();
    }

    public void showSortingDialog() {
        if (tribe.getName().equals("all")) {
            new MaterialDialog.Builder(this).title("Sorting").items(R.array.all_sorting).itemsCallback(new MaterialDialog.ListCallback() {
                @Override
                public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                    if (i == 0) {
                        sorting = "/trending";
                    } else if (i == 1) {
                        sorting = "/new";
                    }
                    updateTitle();
                    refresh.setRefreshing(true);
                    endlessScrollListener.setLoading(true);
                    hideCards();
                    downloadPosts();
                }
            }).show();
        } else {
            new MaterialDialog.Builder(this).title("Sorting").items(R.array.sorting).itemsCallback(new MaterialDialog.ListCallback() {
                @Override
                public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                    if (i == 0) {
                        sorting = "/trending";
                    } else if (i == 1) {
                        sorting = "/newest";
                    } else {
                        sorting = "/topscores";
                    }
                    updateTitle();
                    refresh.setRefreshing(true);
                    endlessScrollListener.setLoading(true);
                    hideCards();
                    downloadPosts();
                }
            }).show();
        }
    }

    public void updateDrawer() {
        AccountHeaderBuilder headerBuilder = new AccountHeaderBuilder()
                    .withActivity(this)
                    .withHeaderBackground(R.drawable.header)
                    .withOnAccountHeaderListener(this);

        List<Profile> profiles;
        try {
            profiles = Profile.listAll(Profile.class);
        } catch (Exception e) {
            profiles = new ArrayList<>(0);
        }

        for (int i = 0; i < profiles.size(); i++) {
            headerBuilder.addProfiles(profiles.get(i).toProfileDrawerItem());
        }

        AccountHeader headerResult = headerBuilder.build();

        if (drawer == null) {

        }

        if (drawer.getHeader() != null) {
            drawer.removeHeader();
        }

        drawer.setHeader(headerResult.getView());

    }

    @Subscribe
    public void onProfile(AddPictureToProfile.ProfilePicturePackage profilePicturePackage) {
        updateDrawer();
    }

    @Subscribe
    public void onProfileError(AddPictureToProfile.ProfilePictureError profilePictureError) {

    }


    @Subscribe
    public void onPicturedAdded(AddPictureToProfile.ProfilePicturePackage profilePicturePackage) {
        profilePicturePackage.profile.save();
        updateDrawer();
    }

}
