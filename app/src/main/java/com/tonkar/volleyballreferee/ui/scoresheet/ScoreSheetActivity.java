package com.tonkar.volleyballreferee.ui.scoresheet;
import java.lang.reflect.Method;
import android.widget.Toast;


import com.tonkar.volleyballreferee.engine.game.IGame;
import com.tonkar.volleyballreferee.engine.service.StoredGamesService;
import com.tonkar.volleyballreferee.engine.service.StoredGamesManager;
import android.os.Handler;
import android.os.Looper;


import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.print.ScoreSheetPdfConverter;
import android.provider.MediaStore;
import android.util.*;
import android.view.MenuItem;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tonkar.volleyballreferee.R;
import com.tonkar.volleyballreferee.engine.Tags;
import com.tonkar.volleyballreferee.engine.game.UsageType;
import com.tonkar.volleyballreferee.engine.scoresheet.ScoreSheetBuilder;
import com.tonkar.volleyballreferee.engine.service.*;
import com.tonkar.volleyballreferee.ui.util.*;

import java.io.*;

public class ScoreSheetActivity extends ProgressIndicatorActivity {

    private IStoredGame       mStoredGame;
    private ScoreSheetBuilder mScoreSheetBuilder;
    private WebView           mWebView;
    private StoredGamesService storedGames;
    private IGame currentGame;
    private boolean preSignCoaches;

    private com.tonkar.volleyballreferee.engine.game.IGame mGame;
    private com.tonkar.volleyballreferee.engine.service.StoredGamesService mStoredGamesService;
    private boolean preSignMode = false;

    private ActivityResultLauncher<Intent> mSelectScoreSheetLogoResultLauncher;
    private ActivityResultLauncher<Intent> mCreatePdfScoreSheetResultLauncher;

   @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    
        // UI first
        setContentView(R.layout.activity_score_sheet);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    
        preSignMode = getIntent().getBooleanExtra("pre_sign_coaches", false);
    
        // ---- Get a game to render ----
        StoredGamesService s = new StoredGamesManager(this);
    
        // 1) Try an explicit stored-game id (used when opening from history)
        String gameId = getIntent().getStringExtra("game");
        mStoredGame = (gameId != null) ? s.getGame(gameId) : null;
    
        // 2) If not present, fall back to the CURRENT game (used when coming
        //    from "Sign coaches first" before the match actually starts)
        currentGame = (mStoredGame == null) ? s.loadCurrentGame() : null;
    
        if (mStoredGame != null) {
            mScoreSheetBuilder = new ScoreSheetBuilder(this, mStoredGame);
        } else if (currentGame != null) {
            // Your ScoreSheetBuilder must support IGame; if not, keep a small
            // builder overload or convert currentGame to your StoredGame type.
            mScoreSheetBuilder = new ScoreSheetBuilder(this, currentGame);
        } else {
            Toast.makeText(this, "No game available to show.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    
        // ---- Toolbar ----
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        // Use whichever source we actually have
        UsageType usage = UsageType.NORMAL;
        UiUtils.updateToolbarLogo(
                toolbar,
                (mStoredGame != null) ? mStoredGame.getKind() : currentGame.getKind(),
                usage
        );
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
    
        // ---- Views / actions ----
        mSyncLayout = findViewById(R.id.score_sheet_sync_layout);
        mSyncLayout.setEnabled(false);
    
        mWebView = findViewById(R.id.score_sheet);
        loadScoreSheet(false);
    
        FloatingActionButton logoButton = findViewById(R.id.score_sheet_logo_button);
        logoButton.setOnClickListener(v -> selectScoreSheetLogo());
    
        FloatingActionButton signatureButton = findViewById(R.id.sign_score_sheet_button);
        signatureButton.setOnClickListener(v -> showSignatureDialog());
    
        // Auto-open signature ONCE if we came from “Sign coaches first”
        if (preSignMode) {
            findViewById(android.R.id.content).post(this::showSignatureDialog);
            Toast.makeText(this, R.string.pre_sign_coaches_hint, Toast.LENGTH_LONG).show();
        }
    
        FloatingActionButton observationButton = findViewById(R.id.score_sheet_observation_button);
        observationButton.setOnClickListener(v -> showObservationDialog());
    
        FloatingActionButton saveButton = findViewById(R.id.save_score_sheet_button);
        saveButton.setOnClickListener(v -> createPdfScoreSheet());
    
        mSelectScoreSheetLogoResultLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            try {
                                Uri imageUri = data.getData();
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 20, stream);
                                String base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
                                mScoreSheetBuilder.setLogo(base64Image);
                                loadScoreSheet(false);
                            } catch (IOException e) {
                                Log.e(Tags.SCORE_SHEET, "Exception while opening the logo", e);
                            }
                        }
                    }
                });
    
        mCreatePdfScoreSheetResultLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            ScoreSheetPdfConverter conv =
                                    new ScoreSheetPdfConverter(ScoreSheetActivity.this, data.getData());
                            conv.convert(mScoreSheetBuilder.createScoreSheet());
                        }
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /////
    private boolean canCollectCoachSignatures() {
        // Allow signatures if the game is started OR we are in pre-sign mode.
        try {
            // If your IGame has isStarted(), use it; if not, this will just fall back to preSignMode.
            java.lang.reflect.Method isStarted = mGame.getClass().getMethod("isStarted");
            Object started = isStarted.invoke(mGame);
            if (started instanceof Boolean && ((Boolean) started)) {
                return true;
            }
        } catch (Throwable ignore) {}
        return preSignMode;
    }
    private void tryOpenCoachSignatureDialog() {
        boolean opened = false;
        String[] candidates = {
                "showCoachSignatureDialog",
                "openCoachSignatureDialog",
                "openCoachesSignature",
                "showSignaturesDialog"
        };
        for (String name : candidates) {
            try {
                Method m = getClass().getDeclaredMethod(name);
                m.setAccessible(true);
                m.invoke(this);
                opened = true;
                break;
            } catch (NoSuchMethodException ignore) {
                // try next
            } catch (Throwable t) {
                // If a dialog exists but throws because match isn't started,
                // we still fall back to the Toast below.
            }
        }
        if (!opened) {
            Toast.makeText(this,
                    "Go to the Signatures section to capture coaches’ signatures.",
                    Toast.LENGTH_LONG).show();
        }
    }
    ////////
    private void selectScoreSheetLogo() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("image/*");

        Intent intent = Intent.createChooser(chooseFile, "");
        mSelectScoreSheetLogoResultLauncher.launch(intent);
    }

    private void showSignatureDialog() {
        SignatureDialogFragment signatureDialogFragment = SignatureDialogFragment.newInstance(mStoredGame);
        signatureDialogFragment.show(getSupportFragmentManager(), "signature_dialog");
    }

    private void showObservationDialog() {
        RemarksDialogFragment remarksDialogFragment = RemarksDialogFragment.newInstance();
        remarksDialogFragment.show(getSupportFragmentManager(), "remarks_dialog");
    }

    private void createPdfScoreSheet() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, mScoreSheetBuilder.getFilename().replace(".html", ".pdf"));

        mCreatePdfScoreSheetResultLauncher.launch(intent);
    }

    void loadScoreSheet(boolean scrollBottom) {
        ScoreSheetBuilder.ScoreSheet scoreSheet = mScoreSheetBuilder.createScoreSheet();
        mWebView.loadDataWithBaseURL(null, scoreSheet.content(), "text/html", "UTF-8", null);
        if (scrollBottom) {
            mWebView.pageDown(true);
        }
    }

    ScoreSheetBuilder getScoreSheetBuilder() {
        return mScoreSheetBuilder;
    }
}
