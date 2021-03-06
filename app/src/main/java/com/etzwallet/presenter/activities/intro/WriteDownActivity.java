package com.etzwallet.presenter.activities.intro;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
//import android.widget.ImageButton;

import com.etzwallet.R;
import com.etzwallet.presenter.activities.util.BRActivity;
import com.etzwallet.presenter.interfaces.BRAuthCompletion;
import com.etzwallet.tools.animation.BRAnimator;
import com.etzwallet.tools.security.AuthManager;
import com.etzwallet.tools.security.PostAuth;

public class WriteDownActivity extends BRActivity {
    private static final String TAG = WriteDownActivity.class.getName();
    private static WriteDownActivity app;

    public static WriteDownActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_down);

        Button writeButton = findViewById(R.id.button_write_down);
//        ImageButton close = findViewById(R.id.close_button);
//        close.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                close();
//            }
//        });
//        ImageButton faq = findViewById(R.id.faq_button);
//        faq.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!BRAnimator.isClickAllowed()) return;
//                BaseWalletManager wm = WalletsMaster.getInstance(WriteDownActivity.this).getCurrentWallet(WriteDownActivity.this);
//                BRAnimator.showSupportFragment(WriteDownActivity.this, BRConstants.FAQ_PAPER_KEY, wm);
//
//            }
//        });
        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!BRAnimator.isClickAllowed()) return;
                AuthManager.getInstance().authPrompt(WriteDownActivity.this, null, getString(R.string.VerifyPin_continueBody), true, false, new BRAuthCompletion() {
                    @Override
                    public void onComplete() {
                        PostAuth.getInstance().onPhraseCheckAuth(WriteDownActivity.this, false);
                    }

                    @Override
                    public void onCancel() {

                    }
                });

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        app = this;
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
//            close();
        } else {
            getFragmentManager().popBackStack();
        }
    }

    private void close() {
        BRAnimator.startBreadActivity(this, false);
        overridePendingTransition(R.anim.fade_up, R.anim.exit_to_bottom);
        if (!isDestroyed()) {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }

}
