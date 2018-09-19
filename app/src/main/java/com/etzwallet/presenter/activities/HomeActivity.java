package com.etzwallet.presenter.activities;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.allenliu.versionchecklib.core.http.HttpRequestMethod;
import com.allenliu.versionchecklib.v2.AllenVersionChecker;
import com.allenliu.versionchecklib.v2.builder.UIData;
import com.allenliu.versionchecklib.v2.callback.RequestVersionListener;
import com.etzwallet.R;
import com.etzwallet.presenter.activities.settings.SecurityCenterActivity;
import com.etzwallet.presenter.activities.settings.SettingsActivity;
import com.etzwallet.presenter.activities.util.BRActivity;
import com.etzwallet.presenter.customviews.BRButton;
import com.etzwallet.presenter.customviews.BRDialogView;
import com.etzwallet.presenter.customviews.BRNotificationBar;
import com.etzwallet.presenter.customviews.BRText;
import com.etzwallet.tools.adapter.WalletListAdapter;
import com.etzwallet.tools.animation.BRDialog;
import com.etzwallet.tools.listeners.RecyclerItemClickListener;
import com.etzwallet.tools.manager.BREventManager;
import com.etzwallet.tools.manager.BRSharedPrefs;
import com.etzwallet.tools.manager.InternetManager;
import com.etzwallet.tools.manager.PromptManager;
import com.etzwallet.tools.sqlite.RatesDataSource;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.tools.util.CurrencyUtils;
import com.etzwallet.tools.util.Utils;
import com.etzwallet.wallet.WalletsMaster;
import com.etzwallet.wallet.abstracts.BaseWalletManager;
import com.etzwallet.wallet.util.JsonRpcHelper;
import com.tencent.bugly.crashreport.CrashReport;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * Created by byfieldj on 1/17/18.
 * <p>
 * Home activity that will show a list of a user's wallets
 */

public class HomeActivity extends BRActivity implements InternetManager.ConnectionReceiverListener , RatesDataSource.OnDataChanged{

    private static final String TAG = HomeActivity.class.getSimpleName();

    private RecyclerView mWalletRecycler;
    private WalletListAdapter mAdapter;
    private BRText mFiatTotal;
    private RelativeLayout mSettings;
    private RelativeLayout mSecurity;
//    private RelativeLayout mSupport;
    private PromptManager.PromptItem mCurrentPrompt;
    public BRNotificationBar mNotificationBar;

    private BRText mPromptTitle;
    private BRText mPromptDescription;
    private BRButton mPromptContinue;
    private BRButton mPromptDismiss;
    private CardView mPromptCard;

    private static HomeActivity app;

    public static HomeActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
//        getTokenList();
        //检查新版本
        checkVersionUpdate();
//        alertDialog();
        mWalletRecycler = findViewById(R.id.rv_wallet_list);
        mFiatTotal = findViewById(R.id.total_assets_usd);

        mSettings = findViewById(R.id.settings_row);
        mSecurity = findViewById(R.id.security_row);
//        mSupport = findViewById(R.id.support_row);
        mNotificationBar = findViewById(R.id.notification_bar);

        mPromptCard = findViewById(R.id.prompt_card);
        mPromptTitle = findViewById(R.id.prompt_title);
        mPromptDescription = findViewById(R.id.prompt_description);
        mPromptContinue = findViewById(R.id.continue_button);
        mPromptDismiss = findViewById(R.id.dismiss_button);

        mWalletRecycler.setLayoutManager(new LinearLayoutManager(this));

        mWalletRecycler.addOnItemTouchListener(new RecyclerItemClickListener(this, mWalletRecycler, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position, float x, float y) {
                if (position >= mAdapter.getItemCount() || position < 0) return;

                if (mAdapter.getItemViewType(position) == 0) {
                    //首页的钱包item 比特币 etz 代币等
                    BRSharedPrefs.putCurrentWalletIso(HomeActivity.this, mAdapter.getItemAt(position).getIso());
                    Intent newIntent = new Intent(HomeActivity.this, WalletActivity.class);
                    startActivity(newIntent);
                    overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                } else {
                    //添加钱包
                    Intent intent = new Intent(HomeActivity.this, AddWalletsActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                }
            }

            @Override
            public void onLongItemClick(View view, int position) {

            }
        }));

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            }
        });
        mSecurity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, SecurityCenterActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
//        mSupport.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!BRAnimator.isClickAllowed()) return;
//                BaseWalletManager wm = WalletsMaster.getInstance(HomeActivity.this).getCurrentWallet(HomeActivity.this);
//
//                BRAnimator.showSupportFragment(HomeActivity.this, null, wm);
//            }
//        });


        mPromptDismiss.setColor(Color.parseColor("#b3c0c8"));
        mPromptDismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePrompt();
            }
        });

        mPromptContinue.setColor(Color.parseColor("#4b77f3"));
        mPromptContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PromptManager.PromptInfo info = PromptManager.getInstance().promptInfo(app, mCurrentPrompt);
                if (info.listener != null)
                    info.listener.onClick(mPromptContinue);
                else
                    Log.e(TAG, "Continue :" + info.title + " (FAILED)");
            }
        });
        Log.e(TAG, "onCreate: 2");

    }


    public String getVersionCode() {
        Context ctx = this.getApplicationContext();
        PackageManager packageManager = ctx.getPackageManager();
        PackageInfo packageInfo;
        String versionCode = "";
        try {
            packageInfo = packageManager.getPackageInfo(ctx.getPackageName(), 0);
            versionCode = packageInfo.versionCode + "";
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }
//    public void alertDialog(){
//        Boolean addErr = BRSharedPrefs.getAddressError(app);
//        Log.i(TAG, "onRecoverWalletAuth: adderr==="+addErr);
//        if(addErr){
//
//        }else{
//            BRDialog.showCustomDialog(app, app.getString(R.string.Alert_error),
//                    app.getString(R.string.Alert_keystore_generic_android_bug),
//                    app.getString(R.string.Button_ok),
//                    null,
//                    new BRDialogView.BROnClickListener() {
//                        @Override
//                        public void onClick(BRDialogView brDialogView) {
//                            app.finish();
//                        }
//                    }, null, new DialogInterface.OnDismissListener(){
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                            app.finish();
//                        }
//                    }, 0);
//            BRSharedPrefs.putAddressError(app, false);
//        }
//    }
    public void checkVersionUpdate(){
        Context ctx = this.getApplicationContext();
        AllenVersionChecker
                .getInstance()
                .requestVersion()
                .setRequestMethod(HttpRequestMethod.GET)
                .setRequestUrl(JsonRpcHelper.versionCheekUrl())
                .request(new RequestVersionListener() {
                    @Nullable
                    @Override
                    public UIData onRequestVersionSuccess(String result) {

                        try{
                            if (Utils.isNullOrEmpty(result)) {
                                Log.i(TAG, "onRequestVersionSuccess: 获取新版本失败1");
                            }
                            JSONObject json = new JSONObject(result);
                            Log.i(TAG, "onRequestVersionSuccess: json=="+json);
                            JSONObject json1 = new JSONObject(json.getString("result"));

                            String dlUrl = json1.getString("url");
                            String dlContent = json1.getString("content");
                            String versionCode = json1.getString("versionCode");
                            String versionName = json1.getString("version");


                            StringBuilder stringBuilder = new StringBuilder();

                            stringBuilder.append(app.getString(R.string.current_version));
                            stringBuilder.append(versionName);
                            stringBuilder.append("\n");
                            stringBuilder.append(app.getString(R.string.update_content));
                            stringBuilder.append(dlContent);

                            String finalString = stringBuilder.toString();

                            if(Integer.parseInt(versionCode) > Integer.parseInt(getVersionCode())){
                                UIData uiData = UIData
                                        .create()
                                        .setDownloadUrl(dlUrl)
                                        .setTitle(app.getString(R.string.download_latest_version))
                                        .setContent(finalString);
                                return uiData;
                            }else{
                                return null;
                            }

                        }catch (Exception e){
                            Log.i(TAG, "onRequestVersionSuccess: 获取新版本失败2");
                        }

                        return null;
                    }

                    @Override
                    public void onRequestVersionFailure(String message) {

                    }
                })
                .excuteMission(ctx);

    }

//    public void getTokenList(){
//        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
//            @Override
//            public void run(){
//                final String listUrl = JsonRpcHelper.getTokenListUrl();
//
//                Log.i(TAG, "run: listUrl=="+listUrl);
//                final JSONObject payload = new JSONObject();
//                JsonRpcHelper.makeRpcRequest1(BreadApp.getBreadContext(), listUrl, payload, new JsonRpcHelper.JsonRpcRequestListener() {
//                    @Override
//                    public void onRpcRequestCompleted(String jsonResult) {
//
//                        Log.i(TAG, "gettokenlist=="+jsonResult);
//
//                    }
//                });
//            }
//        });
//    }
    private  static Context context;



    public void hidePrompt() {
        mPromptCard.setVisibility(View.GONE);
        Log.e(TAG, "hidePrompt: " + mCurrentPrompt);
//        if (mCurrentPrompt == PromptManager.PromptItem.SHARE_DATA) {
//            BRSharedPrefs.putPromptDismissed(app, "shareData", true);
//        }else
         if (mCurrentPrompt == PromptManager.PromptItem.FINGER_PRINT) {
            BRSharedPrefs.putPromptDismissed(app, "fingerprint", true);
        }
        if (mCurrentPrompt != null)
            BREventManager.getInstance().pushEvent("prompt." + PromptManager.getInstance().getPromptName(mCurrentPrompt) + ".dismissed");
        mCurrentPrompt = null;

    }

    private void showNextPromptIfNeeded() {
        PromptManager.PromptItem toShow = PromptManager.getInstance().nextPrompt(this);
        Log.i(TAG, "showNextPromptIfNeeded: toShow==="+toShow);
        if (toShow != null) {
            mCurrentPrompt = toShow;
//            Log.d(TAG, "showNextPrompt: " + toShow);
            PromptManager.PromptInfo promptInfo = PromptManager.getInstance().promptInfo(this, toShow);
            mPromptCard.setVisibility(View.VISIBLE);
            mPromptTitle.setText(promptInfo.title);
            mPromptDescription.setText(promptInfo.description);
            mPromptContinue.setOnClickListener(promptInfo.listener);

        } else {
            Log.i(TAG, "showNextPrompt: nothing to show");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long start = System.currentTimeMillis();
        app = this;

        showNextPromptIfNeeded();

        populateWallets();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mAdapter.startObserving();
            }
        }, 500);

        InternetManager.registerConnectionReceiver(this, this);

        updateUi();
        RatesDataSource.getInstance(this).addOnDataChangedListener(this);
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("BG:" + TAG + ":refreshBalances and address");
                Activity app = HomeActivity.this;
                WalletsMaster.getInstance(app).refreshBalances(app);
                WalletsMaster.getInstance(app).getCurrentWallet(app).refreshAddress(app);
            }
        });

        onConnectionChanged(InternetManager.getInstance().isConnected(this));
        Log.e(TAG, "onResume: took: " + (System.currentTimeMillis() - start));
    }

    private void populateWallets() {
        ArrayList<BaseWalletManager> list = new ArrayList<>(WalletsMaster.getInstance(this).getAllWallets(this));
        mAdapter = new WalletListAdapter(this, list);
        mWalletRecycler.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        InternetManager.unregisterConnectionReceiver(this, this);
        mAdapter.stopObserving();
    }

    private void updateUi() {
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                final BigDecimal fiatTotalAmount = WalletsMaster.getInstance(HomeActivity.this).getAggregatedFiatBalance(HomeActivity.this);
                if (fiatTotalAmount == null) {
                    Log.e(TAG, "updateUi: fiatTotalAmount is null");
                    return;
                }
                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        mFiatTotal.setText(CurrencyUtils.getFormattedAmount(HomeActivity.this,
                                BRSharedPrefs.getPreferredFiatIso(HomeActivity.this), fiatTotalAmount));
                        mAdapter.notifyDataSetChanged();
                    }
                });

            }
        });
    }

    @Override
    public void onConnectionChanged(boolean isConnected) {
        Log.d(TAG, "onConnectionChanged: isConnected: " + isConnected);
        if (isConnected) {
            if (mNotificationBar != null) {
                mNotificationBar.setVisibility(View.INVISIBLE);
            }

            if (mAdapter != null) {
                mAdapter.startObserving();
            }
        } else {
            if (mNotificationBar != null) {
                mNotificationBar.setVisibility(View.VISIBLE);
            }
        }

    }

    public void closeNotificationBar() {
        mNotificationBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onChanged() {
        updateUi();
    }
}