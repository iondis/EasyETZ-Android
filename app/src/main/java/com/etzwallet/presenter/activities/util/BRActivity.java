package com.etzwallet.presenter.activities.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.etzwallet.BreadApp;
import com.etzwallet.R;
import com.etzwallet.presenter.activities.DisabledActivity;
import com.etzwallet.presenter.activities.HomeActivity;
import com.etzwallet.presenter.activities.WalletActivity;
import com.etzwallet.presenter.activities.intro.IntroActivity;
import com.etzwallet.presenter.activities.intro.RecoverActivity;
import com.etzwallet.presenter.activities.intro.WriteDownActivity;
import com.etzwallet.presenter.customviews.MyLog;
import com.etzwallet.tools.animation.BRAnimator;
import com.etzwallet.tools.animation.BRDialog;
import com.etzwallet.tools.manager.BRApiManager;
import com.etzwallet.tools.manager.BRSharedPrefs;
import com.etzwallet.tools.manager.InternetManager;
import com.etzwallet.tools.security.AuthManager;
import com.etzwallet.tools.security.BRKeyStore;
import com.etzwallet.tools.security.PostAuth;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.tools.util.BRConstants;
import com.etzwallet.wallet.WalletsMaster;
import com.etzwallet.wallet.util.CryptoUriParser;
import com.platform.HTTPServer;
import com.platform.tools.BRBitId;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 5/23/17.
 * Copyright (c) 2017 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class BRActivity extends Activity implements BreadApp.OnAppBackgrounded {
    private static final String TAG = BRActivity.class.getName();
    public static final Point screenParametersPoint = new Point();
    private static final String PACKAGE_NAME = BreadApp.getBreadContext() == null ? null : BreadApp.getBreadContext().getApplicationContext().getPackageName();

    static {
        try {
            System.loadLibrary(BRConstants.NATIVE_LIB_NAME);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            MyLog.d("Native code library failed to load.\\n\" + " + e);
            MyLog.d("Installer Package Name -> " + (PACKAGE_NAME == null ? "null" : BreadApp.getBreadContext().getPackageManager().getInstallerPackageName(PACKAGE_NAME)));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }
        BreadApp.getMyApp().addActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        BreadApp.activityCounter.decrementAndGet();
        BreadApp.onStop(this);

        //open back to HomeActivity if needed
        if (this instanceof WalletActivity)
            BRSharedPrefs.putAppBackgroundedFromHome(this, false);
        else if (this instanceof HomeActivity)
            BRSharedPrefs.putAppBackgroundedFromHome(this, true);

    }

    @Override
    protected void onResume() {
        init(this);
        super.onResume();
        BreadApp.backgroundedTime = 0;

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //No call for super(). Bug on API Level > 11.
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BRConstants.CAMERA_REQUEST_ID) {
            // BEGIN_INCLUDE(permission_result)
            // Received permission result for camera permission.
            MyLog.i("Received response for CAMERA_REQUEST_ID permission request.");

            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission has been granted, preview can be displayed
                MyLog.i("CAMERA permission has now been granted. Showing preview.");
                BRAnimator.openScanner(this, BRConstants.SCANNER_REQUEST);
            } else {
                MyLog.i("CAMERA permission was NOT granted.");
                BRDialog.showSimpleDialog(this, getString(R.string.Send_cameraUnavailabeTitle_android), getString(R.string.Send_cameraUnavailabeMessage_android));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        // 123 is the qrCode result
        switch (requestCode) {

            case BRConstants.PAY_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPublishTxAuth(BRActivity.this, null, true, null, null, false, null, null);
                        }
                    });
                }
                break;
            case BRConstants.REQUEST_PHRASE_BITID:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onBitIDAuth(BRActivity.this, true);
                        }
                    });

                }
                break;

            case BRConstants.PAYMENT_PROTOCOL_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPaymentProtocolRequest(BRActivity.this, true);
                        }
                    });

                }
                break;

            case BRConstants.CANARY_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onCanaryCheck(BRActivity.this, true);
                        }
                    });
                } else {
                    finish();
                }
                break;

            case BRConstants.SHOW_PHRASE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPhraseCheckAuth(BRActivity.this, true);
                        }
                    });
                }
                break;
            case BRConstants.PROVE_PHRASE_REQUEST:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPhraseProveAuth(BRActivity.this, true);
                        }
                    });
                }
                break;
            case BRConstants.PUT_PHRASE_RECOVERY_WALLET_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onRecoverWalletAuth(BRActivity.this, true, false);
                        }
                    });
                } else {
                    finish();
                }
                break;

            case BRConstants.SCANNER_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            String result = data.getStringExtra("result");
                            MyLog.i("**************" + result);
                            if (CryptoUriParser.isCryptoUrl(BRActivity.this, result))
                                CryptoUriParser.processRequest(BRActivity.this, result,
                                        WalletsMaster.getInstance(BRActivity.this).getCurrentWallet(BRActivity.this));
                            else if (BRBitId.isBitId(result))
                                BRBitId.signBitID(BRActivity.this, result, null);
                            else
                                MyLog.e("onActivityResult: not bitcoin address NOR bitID");
                        }
                    });

                }
                break;

            case BRConstants.PUT_PHRASE_NEW_WALLET_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onCreateWalletAuth(BRActivity.this, true);
                        }
                    });

                } else {
                    MyLog.e("WARNING: resultCode != RESULT_OK");
                    WalletsMaster m = WalletsMaster.getInstance(BRActivity.this);
                    m.wipeWalletButKeystore(this);
                    finish();
                }
                break;

        }
    }

    public void init(Activity app) {
        //set status bar color
//        ActivityUTILS.setStatusBarColor(app, android.R.color.transparent);
        InternetManager.getInstance();
        if (!(app instanceof IntroActivity || app instanceof RecoverActivity || app instanceof WriteDownActivity))
            BRApiManager.getInstance().startTimer(app);
        //show wallet locked if it is
        if (!ActivityUTILS.isAppSafe(app))
            if (AuthManager.getInstance().isWalletDisabled(app))
                AuthManager.getInstance().setWalletDisabled(app);
        BreadApp.setBreadContext(app);


        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                if (!HTTPServer.isStarted()) {
                    HTTPServer.startServer();
                    BreadApp.addOnBackgroundedListener(BRActivity.this);
                }
            }
        });
        lockIfNeeded(this);
    }

    private void lockIfNeeded(Activity app) {
        long start = System.currentTimeMillis();
        //lock wallet if 3 minutes passed
        if (BreadApp.backgroundedTime != 0
                && ((System.currentTimeMillis() - BreadApp.backgroundedTime) >= 180 * 1000)
                && !(app instanceof DisabledActivity)) {
            if (!BRKeyStore.getPinCode(app).isEmpty()) {
                BRAnimator.startBreadActivity(app, true);
            }
        }

    }

    @Override
    public void onBackgrounded() {
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                HTTPServer.stopServer();
            }
        });
    }
}
