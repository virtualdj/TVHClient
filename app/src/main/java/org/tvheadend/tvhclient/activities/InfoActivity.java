package org.tvheadend.tvhclient.activities;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.fragments.InfoFragment;
import org.tvheadend.tvhclient.utils.MiscUtils;
import org.tvheadend.tvhclient.utils.Utils;

public class InfoActivity extends AppCompatActivity {

    @SuppressWarnings("unused")
    private final static String TAG = InfoActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(MiscUtils.getThemeId(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        Utils.setLanguage(this);

        // Setup the action bar and show the title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        TextView actionBarTitle = (TextView) toolbar.findViewById(R.id.actionbar_title);
        TextView actionBarSubtitle = (TextView) toolbar.findViewById(R.id.actionbar_subtitle);
        actionBarTitle.setText(getString(R.string.pref_information));
        actionBarTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        actionBarSubtitle.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            InfoFragment fragment = new InfoFragment();
            fragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(R.id.main_fragment, fragment).commit();
        }
    }
}
