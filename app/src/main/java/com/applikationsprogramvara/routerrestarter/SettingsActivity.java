package com.applikationsprogramvara.routerrestarter;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportFragmentManager().findFragmentByTag(MyPreferenceFragment.TAG) == null)
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new MyPreferenceFragment(), MyPreferenceFragment.TAG)
                    .commit();

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {
        public static final String TAG = "MyPreferenceFragmentTAG";

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            init();

        }


        public void init() {
            setPreferencesFromResource(R.xml.settings, null);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

            for(String value: new String[]{
                    "RouterAddress",
                    "ExternalWebsite"
            }) {
                Preference preference = findPreference(value);
                if (preference != null) {
                    preference.setSummary(prefs.getString(preference.getKey(), ""));

                    preference.setOnPreferenceChangeListener((p, newValue) -> {
                        p.setSummary(newValue.toString());
                        return true;
                    });
                }

            }

            EditTextPreference routerPasswordPreference = findPreference("RouterPassword");
            routerPasswordPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            });
            routerPasswordPreference.setSummary("".equals(prefs.getString("RouterPassword", "")) ? "not set" : "set");
            routerPasswordPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary("".equals(newValue) ? "not set" : "set");
                return true;
            });

        }

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                saveAndClose();
                break;
        }
        return true;
    }


    private void saveAndClose() {
        Intent resultIntent = new Intent();
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

}
