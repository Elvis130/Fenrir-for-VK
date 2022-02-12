package dev.ragnarok.fenrir.activity;

import android.graphics.Color;
import android.os.Bundle;

import com.google.android.material.snackbar.BaseTransientBottomBar;

import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.fragment.AccountsFragment;
import dev.ragnarok.fenrir.fragment.PreferencesFragment;
import dev.ragnarok.fenrir.fragment.ThemeFragment;
import dev.ragnarok.fenrir.place.Place;
import dev.ragnarok.fenrir.place.PlaceProvider;
import dev.ragnarok.fenrir.util.Utils;

public class AccountsActivity extends NoMainActivity implements PlaceProvider {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(getMainContainerViewId(), new AccountsFragment())
                    .addToBackStack("accounts")
                    .commit();
        }
    }

    @Override
    public void openPlace(Place place) {
        if (place.getType() == Place.PREFERENCES) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(getMainContainerViewId(), PreferencesFragment.newInstance(place.getArgs()))
                    .addToBackStack("preferences")
                    .commit();
        } else if (place.getType() == Place.SETTINGS_THEME) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(getMainContainerViewId(), ThemeFragment.newInstance())
                    .addToBackStack("preferences_themes")
                    .commit();
        } else {
            Utils.ColoredSnack(findViewById(getMainContainerViewId()), R.string.not_available, BaseTransientBottomBar.LENGTH_SHORT, Color.RED).show();
        }
    }

}