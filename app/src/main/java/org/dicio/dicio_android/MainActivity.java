package org.dicio.dicio_android;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import org.dicio.component.standard.StandardRecognizer;
import org.dicio.dicio_android.components.AssistanceComponent;
import org.dicio.dicio_android.components.ChainAssistanceComponent;
import org.dicio.dicio_android.components.fallback.TextFallbackComponent;
import org.dicio.dicio_android.components.output.LyricsOutput;
import org.dicio.dicio_android.components.output.OpenOutput;
import org.dicio.dicio_android.components.output.SearchOutput;
import org.dicio.dicio_android.components.output.WeatherOutput;
import org.dicio.dicio_android.components.processing.GeniusProcessor;
import org.dicio.dicio_android.components.processing.OpenWeatherMapProcessor;
import org.dicio.dicio_android.components.processing.QwantProcessor;
import org.dicio.dicio_android.eval.ComponentEvaluator;
import org.dicio.dicio_android.eval.ComponentRanker;
import org.dicio.dicio_android.input.AzureSpeechInputDevice;
import org.dicio.dicio_android.input.InputDevice;
import org.dicio.dicio_android.input.SpeechInputDevice;
import org.dicio.dicio_android.input.ToolbarInputDevice;
import org.dicio.dicio_android.output.graphical.MainScreenGraphicalDevice;
import org.dicio.dicio_android.output.speech.ToastSpeechDevice;
import org.dicio.dicio_android.settings.SettingsActivity;
import org.dicio.dicio_android.util.BaseActivity;

import java.util.ArrayList;
import java.util.List;

import static org.dicio.dicio_android.sentences.Sections.getSection;
import static org.dicio.dicio_android.sentences.SectionsGenerated.lyrics;
import static org.dicio.dicio_android.sentences.SectionsGenerated.open;
import static org.dicio.dicio_android.sentences.SectionsGenerated.search;
import static org.dicio.dicio_android.sentences.SectionsGenerated.weather;

public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private DrawerLayout drawer;
    private MenuItem textInputItem = null;

    private InputDevice inputDevice;
    private ComponentEvaluator componentEvaluator;
    private String currentInputDevicePreference;
    private boolean appJustOpened = false;
    private boolean textInputItemFocusJustChanged = false;

    ////////////////////////
    // Activity lifecycle //
    ////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);

        setSupportActionBar(toolbar);
        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        final ScrollView scrollView = findViewById(R.id.outputScrollView);
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            Log.e("MAINACTIVITY", top + " " + bottom + "   " + oldTop + " " + oldBottom + " " + textInputItemFocusJustChanged);
            if (textInputItemFocusJustChanged && (oldBottom != bottom || oldTop != top)) {
                textInputItemFocusJustChanged = false; // the keyboard was opened because of menu
                scrollView.postDelayed(() -> scrollView.scrollBy(0, oldBottom - bottom + top - oldTop), 10);
            }
        });

        currentInputDevicePreference = getInputDevicePreference();
        initializeComponentEvaluator();
        appJustOpened = true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (textInputItem != null && textInputItem.isActionViewExpanded()) {
            invalidateOptionsMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        textInputItem = menu.findItem(R.id.action_text_input);
        if (inputDevice instanceof ToolbarInputDevice) {
            textInputItem.setVisible(true);
            ((ToolbarInputDevice) inputDevice).setTextInputItem(textInputItem);

            textInputItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(final MenuItem item) {
                    hideAllItems(menu);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(final MenuItem item) {
                    // resets the whole menu, setting `item`'s visibility to true
                    invalidateOptionsMenu();
                    return true;
                }
            });

            final SearchView textInputView = (SearchView) textInputItem.getActionView();
            textInputView.setQueryHint(getResources().getString(R.string.text_input_hint));
            textInputView.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
            textInputView.setOnQueryTextFocusChangeListener(
                    (v, hasFocus) -> textInputItemFocusJustChanged = true);
        } else {
            textInputItem.setVisible(false);
        }

        final MenuItem voiceInputItem = menu.findItem(R.id.action_voice_input);
        if (inputDevice instanceof SpeechInputDevice) {
            voiceInputItem.setVisible(true);
            ((SpeechInputDevice) inputDevice).setVoiceInputItem(voiceInputItem,
                    ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mic_white, null),
                    ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mic_none_white, null));
        } else {
            voiceInputItem.setVisible(false);
        }

        if (appJustOpened) {
            inputDevice.tryToGetInput();
            appJustOpened = false;
        }
        return true;
    }

    private void hideAllItems(Menu menu) {
        for (int i = 0; i < menu.size(); ++i) {
            final MenuItem item = menu.getItem(i);
            item.setVisible(false);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else {
            final DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean reinitializeComponentEvaluator = false;

        final String inputDevicePreference = getInputDevicePreference();
        if (!inputDevicePreference.equals(currentInputDevicePreference)) {
            currentInputDevicePreference = inputDevicePreference;
            reinitializeComponentEvaluator = true;
        }

        if (reinitializeComponentEvaluator) {
            initializeComponentEvaluator();
            invalidateOptionsMenu();
        }
    }

    @NonNull
    private String getInputDevicePreference() {
        final String inputDevicePreference = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.settings_key_input_method), null);

        if (inputDevicePreference == null) {
            return getString(R.string.settings_value_input_method_text);
        } else {
            return inputDevicePreference;
        }
    }

    /////////////////////////////////////
    // Assistance components functions //
    /////////////////////////////////////

    public void initializeComponentEvaluator() {
        // Sections language is initialized in BaseActivity.setLocale

        final List<AssistanceComponent> standardComponentBatch = new ArrayList<AssistanceComponent>() {{
            add(new ChainAssistanceComponent.Builder()
                    .recognize(new StandardRecognizer(getSection(weather)))
                    .process(new OpenWeatherMapProcessor())
                    .output(new WeatherOutput()));
            add(new ChainAssistanceComponent.Builder()
                    .recognize(new StandardRecognizer(getSection(search)))
                    .process(new QwantProcessor())
                    .output(new SearchOutput()));
            add(new ChainAssistanceComponent.Builder()
                    .recognize(new StandardRecognizer(getSection(lyrics)))
                    .process(new GeniusProcessor())
                    .output(new LyricsOutput()));
            add(new ChainAssistanceComponent.Builder()
                    .recognize(new StandardRecognizer(getSection(open)))
                    .output(new OpenOutput()));
        }};

        if (currentInputDevicePreference.equals(getString(R.string.settings_value_input_method_azure))) {
            inputDevice = new AzureSpeechInputDevice(this);
        } else {
            inputDevice = new ToolbarInputDevice();
        }

        componentEvaluator = new ComponentEvaluator(
                new ComponentRanker(standardComponentBatch, new TextFallbackComponent()),
                inputDevice,
                new ToastSpeechDevice(this),
                new MainScreenGraphicalDevice(findViewById(R.id.outputViews)),
                this);
    }
}
