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
        setContentView(R.layout.activity_score_sheet);
    
        preSignMode = getIntent().getBooleanExtra("pre_sign_coaches", false);
    
        // Always have the manager ready
        StoredGamesService storedGames = new StoredGamesManager(this);
    
        // 1) Try to resolve a stored game by id (preferred path)
        String gameId = getIntent().getStringExtra("game");
        if (gameId != null) {
            mStoredGame = storedGames.getGame(gameId);
        }
    
        // 2) Fallback: if no id or not found, try the current (in-progress) game’s id
        if (mStoredGame == null) {
            IGame current = storedGames.loadCurrentGame();
            if (current != null) {
                String currentId = current.getId();
                if (currentId != null) {
                    mStoredGame = storedGames.getGame(currentId);
                }
            }
        }
    
        // 3) If still nothing, bail out gracefully
        if (mStoredGame == null) {
            Toast.makeText(this, R.string.no_game_to_display, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    
        // From here on, your existing setup
        mScoreSheetBuilder = new ScoreSheetBuilder(this, mStoredGame);
        // … set toolbar, buttons, mWebView, loadScoreSheet(false), etc …
    
        // Auto-open the coach-sign dialog when we came from “Sign coaches first”
        if (preSignMode) {
            findViewById(android.R.id.content).post(this::showSignatureDialog);
            // Optional helper toast
            Toast.makeText(this, R.string.pre_sign_coaches_hint, Toast.LENGTH_LONG).show();
        }
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
