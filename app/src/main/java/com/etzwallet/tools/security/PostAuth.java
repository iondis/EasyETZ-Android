package com.etzwallet.tools.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.WorkerThread;

import com.etzwallet.BreadApp;
import com.etzwallet.R;
import com.etzwallet.core.BRCoreKey;
import com.etzwallet.core.BRCoreMasterPubKey;
import com.etzwallet.presenter.activities.InputWordsActivity;
import com.etzwallet.presenter.activities.SetPinActivity;
import com.etzwallet.presenter.activities.PaperKeyActivity;
import com.etzwallet.presenter.activities.intro.WriteDownActivity;
import com.etzwallet.presenter.customviews.BRDialogView;
import com.etzwallet.presenter.customviews.MyLog;
import com.etzwallet.presenter.entities.CryptoRequest;
import com.etzwallet.tools.animation.BRDialog;
import com.etzwallet.tools.exceptions.UserNotAuthenticatedException;
import com.etzwallet.tools.manager.BRReportsManager;
import com.etzwallet.tools.manager.BRSharedPrefs;
import com.etzwallet.tools.manager.SendManager;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.tools.util.BRConstants;
import com.etzwallet.tools.util.Utils;
import com.etzwallet.wallet.WalletsMaster;
import com.etzwallet.wallet.abstracts.BaseWalletManager;
import com.etzwallet.wallet.wallets.CryptoTransaction;
import com.platform.entities.TxMetaData;
import com.platform.tools.BRBitId;
import com.platform.tools.KVStoreManager;

import java.math.BigDecimal;
import java.util.Arrays;


/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 4/14/16.
 * Copyright (c) 2016 breadwallet LLC
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

public class PostAuth {
    public static final String TAG = PostAuth.class.getName();

    private String mCachedPaperKey;
    public CryptoRequest mCryptoRequest;
    //The user is stuck with endless authentication due to KeyStore bug.
    public static boolean mAuthLoopBugHappened;
    public static TxMetaData txMetaData;
    public SendManager.SendCompletion mSendCompletion;
    private BaseWalletManager mWalletManager;

    private CryptoTransaction mPaymentProtocolTx;
    private static PostAuth mInstance;

    private PostAuth() {
    }

    public static PostAuth getInstance() {
        if (mInstance == null) {
            mInstance = new PostAuth();
        }
        return mInstance;
    }

    public void onCreateWalletAuth(final Activity app, boolean authAsked) {
        MyLog.i("onCreateWalletAuth: " + authAsked);
        long start = System.currentTimeMillis();
        boolean success = WalletsMaster.getInstance(BreadApp.getBreadContext()).generateRandomSeed(BreadApp.getBreadContext());
        MyLog.i("创建钱包是否成功------- " + success);
        if (success) {
            Intent intent = new Intent(app, WriteDownActivity.class);
            app.startActivity(intent);
            app.overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            app.finish();
        } else {
            if (authAsked) {
                MyLog.e("onCreateWalletAuth: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
    }

    public void onPhraseCheckAuth(Activity app, boolean authAsked) {
        String cleanPhrase;
        try {
            byte[] raw = BRKeyStore.getPhrase(app, BRConstants.SHOW_PHRASE_REQUEST_CODE);
            if (raw == null) {
                BRReportsManager.reportBug(new NullPointerException("onPhraseCheckAuth: getPhrase = null"), true);
                return;
            }
            cleanPhrase = new String(raw);
        } catch (UserNotAuthenticatedException e) {
            if (authAsked) {
                MyLog.e("onPhraseCheckAuth: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
        Intent intent = new Intent(app, PaperKeyActivity.class);
        intent.putExtra("phrase", cleanPhrase);
        app.startActivity(intent);
        app.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
    }

    public void onPhraseProveAuth(Activity app, boolean authAsked) {
        String cleanPhrase;
        try {
            cleanPhrase = new String(BRKeyStore.getPhrase(app, BRConstants.PROVE_PHRASE_REQUEST));

        } catch (UserNotAuthenticatedException e) {
            if (authAsked) {
                MyLog.e("onPhraseProveAuth: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
//        Intent intent = new Intent(app, PaperKeyProveActivity.class);
        Intent intent = new Intent(app, InputWordsActivity.class);
        MyLog.i("onPhraseProveAuth: cleanPhrase==" + cleanPhrase);
        intent.putExtra("phrase", cleanPhrase);
        intent.putExtra("from", 1);
        app.startActivity(intent);
        app.overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
    }

    public void onBitIDAuth(Activity app, boolean authenticated) {
        BRBitId.completeBitID(app, authenticated);
    }

    public void onRecoverWalletAuth(Activity app, boolean authAsked, boolean ishuifu) {
        if (Utils.isNullOrEmpty(mCachedPaperKey)) {
            MyLog.e("onRecoverWalletAuth: phraseForKeyStore is null or empty");
            BRReportsManager.reportBug(new NullPointerException("onRecoverWalletAuth: phraseForKeyStore is or empty"));
            return;
        }
        try {
            boolean success = false;
            try {
                MyLog.i("phrase===-1= " + mCachedPaperKey);

                byte[] by = mCachedPaperKey.getBytes();
                for (int i = 0; i < by.length; i++) {
                    MyLog.i("phrase===0= " + by[i]);
                }

                success = BRKeyStore.putPhrase(mCachedPaperKey.getBytes(),
                        app, BRConstants.PUT_PHRASE_RECOVERY_WALLET_REQUEST_CODE);
            } catch (UserNotAuthenticatedException e) {
                if (authAsked) {
                    MyLog.e("onRecoverWalletAuth: WARNING!!!! LOOP");
                    mAuthLoopBugHappened = true;

                }
                return;
            }

            if (!success) {
                if (authAsked)
                    MyLog.e("onRecoverWalletAuth, !success && authAsked");
            } else {
                if (mCachedPaperKey.length() != 0) {
                    BRSharedPrefs.putPhraseWroteDown(app, true);
                    byte[] seed = BRCoreKey.getSeedFromPhrase(mCachedPaperKey.getBytes());
                    byte[] authKey = BRCoreKey.getAuthPrivKeyForAPI(seed);
                    BRKeyStore.putAuthKey(authKey, app);
                    BRCoreMasterPubKey mpk = new BRCoreMasterPubKey(mCachedPaperKey.getBytes(), true);
                    BRKeyStore.putMasterPublicKey(mpk.serialize(), app);
                    app.overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                    if (!ishuifu) {
                        Intent intent = new Intent(app, SetPinActivity.class);
                        intent.putExtra("noPin", true);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        app.startActivity(intent);
                        if (!app.isDestroyed()) app.finish();
                    }

                    mCachedPaperKey = null;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            BRReportsManager.reportBug(e);
        }

    }

    @WorkerThread
    public void onPublishTxAuth(final Context app, final BaseWalletManager wm, final boolean authAsked, final SendManager.SendCompletion completion, final String data, final boolean isErc20, final String gasL, final String gasP) {
        if (completion != null) {
            mSendCompletion = completion;
        }
        MyLog.i("onPublishTxAuth: request.data===" + data);

        if (wm != null) mWalletManager = wm;
        byte[] rawPhrase;
        try {
            rawPhrase = BRKeyStore.getPhrase(app, BRConstants.PAY_REQUEST_CODE);
        } catch (UserNotAuthenticatedException e) {
            if (authAsked) {
                MyLog.e("onPublishTxAuth: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
        try {
            if (rawPhrase.length > 0) {
                if (mCryptoRequest != null && mCryptoRequest.amount != null && mCryptoRequest.address != null) {
                    CryptoTransaction tx;
                    String newGasPrice;
                    try {
                        if (gasP.length() == 0) {
                            newGasPrice = "";
                        } else {
                            long newGasp = Integer.parseInt(gasP);
                            long b = (long) Math.pow(10, 9) * newGasp;
                            newGasPrice = String.valueOf(b);
                        }
                    } catch (Exception e) {
                        newGasPrice = "";
                    }
                    MyLog.i("onPublishTxAuth: newGasPrice==" + newGasPrice);
                    tx = mWalletManager.createTransaction(mCryptoRequest.amount, mCryptoRequest.address, data, gasL, newGasPrice);

//                    MyLog.i("createTransaction: token2===" + tx.getEtherTx().getHash());
                    if (tx == null) {
                        BRDialog.showCustomDialog(app, app.getString(R.string.Alert_error), app.getString(R.string.Send_insufficientFunds),
                                app.getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                                    @Override
                                    public void onClick(BRDialogView brDialogView) {
                                        brDialogView.dismiss();
                                    }
                                }, null, null, 0);
                        return;
                    }

                    final byte[] txHash = mWalletManager.signAndPublishTransaction(tx, rawPhrase);
                    MyLog.i("+++++++++++++" + new String(txHash));
                    txMetaData = new TxMetaData();
                    txMetaData.comment = mCryptoRequest.message;
                    txMetaData.exchangeCurrency = BRSharedPrefs.getPreferredFiatIso(app);
                    BigDecimal fiatExchangeRate = mWalletManager.getFiatExchangeRate(app);
                    txMetaData.exchangeRate = fiatExchangeRate == null ? 0 : fiatExchangeRate.doubleValue();
                    txMetaData.fee = mWalletManager.getTxFee(tx).toPlainString();
                    txMetaData.txSize = tx.getTxSize().intValue();
                    txMetaData.blockHeight = BRSharedPrefs.getLastBlockHeight(app, mWalletManager.getIso());
                    txMetaData.creationTime = (int) (System.currentTimeMillis() / 1000);//seconds
                    txMetaData.deviceId = BRSharedPrefs.getDeviceId(app);
                    txMetaData.classVersion = 1;
//                    BRSharedPrefs.setAddressNonce(app, mWalletManager.getAddress(), tx.getEtherTx().getNonce() + 1);

                    if (Utils.isNullOrEmpty(txHash)) {
                        if (tx.getEtherTx() != null) {
                            mWalletManager.watchTransactionForHash(tx, new BaseWalletManager.OnHashUpdated() {
                                @Override
                                public void onUpdated(String hash) {
                                    if (mSendCompletion != null) {
                                        mSendCompletion.onCompleted(hash, true);
                                        stampMetaData(app, txHash);
                                        mSendCompletion = null;
                                    }
                                }
                            });
                            return; // ignore ETH since txs do not have the hash right away
                        }
                        MyLog.e("onPublishTxAuth: signAndPublishTransaction returned an empty txHash");
                        BRDialog.showSimpleDialog(app, app.getString(R.string.Alerts_sendFailure), "Failed to create transaction");
                    } else {
                        if (mSendCompletion != null) {
                            mSendCompletion.onCompleted(tx.getHash(), true);
                            mSendCompletion = null;
                        }
                        stampMetaData(app, txHash);
                    }


                } else {
                    throw new NullPointerException("payment item is null");
                }
            } else {
                MyLog.e("onPublishTxAuth: paperKey length is 0!");
                BRReportsManager.reportBug(new NullPointerException("onPublishTxAuth: paperKey length is 0"));
                return;
            }
        } finally {
            Arrays.fill(rawPhrase, (byte) 0);
            mCryptoRequest = null;
        }

    }

    public static void stampMetaData(Context app, byte[] txHash) {
        if (txMetaData != null) {
            KVStoreManager.getInstance().putTxMetaData(app, txMetaData, txHash);
            txMetaData = null;
        } else MyLog.e("stampMetaData: txMetaData is null!");
    }

    public void onPaymentProtocolRequest(final Activity app, boolean authAsked) {
        final byte[] paperKey;
        try {
            paperKey = BRKeyStore.getPhrase(app, BRConstants.PAYMENT_PROTOCOL_REQUEST_CODE);
        } catch (UserNotAuthenticatedException e) {
            if (authAsked) {
                MyLog.e("onPaymentProtocolRequest: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
        if (paperKey == null || paperKey.length < 10 || mPaymentProtocolTx == null) {
            MyLog.d("onPaymentProtocolRequest() returned: rawSeed is malformed: " + (paperKey == null ? "" : paperKey.length));
            return;
        }

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                byte[] txHash = WalletsMaster.getInstance(app).getCurrentWallet(app).signAndPublishTransaction(mPaymentProtocolTx, paperKey);
                if (Utils.isNullOrEmpty(txHash)) {
                    MyLog.e("run: txHash is null");
                }
                Arrays.fill(paperKey, (byte) 0);
                mPaymentProtocolTx = null;
            }
        });

    }

    public void setCachedPaperKey(String paperKey) {
        this.mCachedPaperKey = paperKey;
    }

    public void setPaymentItem(CryptoRequest cryptoRequest) {
        this.mCryptoRequest = cryptoRequest;
    }

    public void setTmpPaymentRequestTx(CryptoTransaction tx) {
        this.mPaymentProtocolTx = tx;
    }

    public void onCanaryCheck(final Activity app, boolean authAsked) {
        String canary = null;
        try {
            canary = BRKeyStore.getCanary(app, BRConstants.CANARY_REQUEST_CODE);
        } catch (UserNotAuthenticatedException e) {
            if (authAsked) {
                MyLog.e("onCanaryCheck: WARNING!!!! LOOP");
                mAuthLoopBugHappened = true;
            }
            return;
        }
        if (canary == null || !canary.equalsIgnoreCase(BRConstants.CANARY_STRING)) {
            byte[] phrase;
            try {
                phrase = BRKeyStore.getPhrase(app, BRConstants.CANARY_REQUEST_CODE);
            } catch (UserNotAuthenticatedException e) {
                if (authAsked) {
                    MyLog.e("onCanaryCheck: WARNING!!!! LOOP");
                    mAuthLoopBugHappened = true;
                }
                return;
            }

            String strPhrase = new String((phrase == null) ? new byte[0] : phrase);
            if (strPhrase.isEmpty()) {
                WalletsMaster m = WalletsMaster.getInstance(app);
                m.wipeKeyStore(app);
                m.wipeWalletButKeystore(app);
            } else {
                MyLog.e("onCanaryCheck: Canary wasn't there, but the phrase persists, adding canary to keystore.");
                try {
                    BRKeyStore.putCanary(BRConstants.CANARY_STRING, app, 0);
                } catch (UserNotAuthenticatedException e) {
                    if (authAsked) {
                        MyLog.e("onCanaryCheck: WARNING!!!! LOOP");
                        mAuthLoopBugHappened = true;
                    }
                    return;
                }
            }
        }
        WalletsMaster.getInstance(app).startTheWalletIfExists(app);
    }

}
