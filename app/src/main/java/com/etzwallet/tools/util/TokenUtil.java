/**
 * BreadWallet
 * <p/>
 * Created by Jade Byfield <jade@breadwallet.com> on 9/13/2018.
 * Copyright (c) 2018 breadwallet LLC
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

package com.etzwallet.tools.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.etzwallet.BreadApp;
import com.etzwallet.R;
import com.etzwallet.presenter.customviews.MyLog;
import com.etzwallet.presenter.entities.TokenItem;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.wallet.util.HttpUtils;
import com.etzwallet.wallet.util.JsonRpcHelper;
import com.etzwallet.wallet.wallets.ethereum.WalletEthManager;
import com.etzwallet.wallet.wallets.ethereum.WalletTokenManager;
import com.platform.APIClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class TokenUtil {

    private static final String TAG = TokenUtil.class.getSimpleName();

    private static final String ENDPOINT_CURRENCIES_SALE_ADDRESS = "/currencies?saleAddress=";
    private static final String FIELD_CODE = "symbol";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SCALE = "decimals";
    private static final String FIELD_CONTRACT_ADDRESS = "address";
    private static final String FIELD_IMAG = "logoUrl";
    private static final String FIELD_START_COLOR = "colorLeft";
    private static final String FIELD_END_COLOR = "colorRight";
    private static final String FIELD_CONTRACT_INITIAL_VALUE = "contract_initial_value";
    private static final String TOKENS_FILENAME = "tokens.json";
    private static  String tJson="";

    // TODO: In DROID-878 fix this so we don't have to store this mTokenItems... (Should be stored in appropriate wallet.)
    private static ArrayList<TokenItem> mTokenItems;

    private TokenUtil() {
    }

    /**
     * When the app first starts, fetch our local copy of tokens.json from the resource folder
     *
     * @param context The Context of the caller
     */
    public static void initialize(Context context) {
        String filePath = context.getFilesDir().getAbsolutePath() + File.separator + TOKENS_FILENAME;
        File tokensFile = new File(filePath);

        if (!tokensFile.exists()) {
            MyLog.i("++++++++++++++++++++++未获取");
            InputStream tokensInputStream = context.getResources().openRawResource(R.raw.tokens);
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(tokensInputStream, "UTF-8"));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append('\n');
                }
                bufferedReader.close();
                tokensInputStream.close();
                mTokenItems = parseJsonToTokenList(context, stringBuilder.toString());

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "Could not read from resource file at res/raw/tokens.json ", e);
            }

        }
    }

    /**
     * This method can either fetch the list of supported tokens, or fetch a specific token by saleAddress
     * Request the list of tokens we support from the /currencies endpoint
     *
     * @param context  The Context of the caller
     * @param tokenUrl The URL of the endpoint to get the token metadata from.
     */
    private static APIClient.BRResponse fetchTokensFromServer(Context context, String tokenUrl) {
        Request request = new Request.Builder()
                .url(tokenUrl)
                .header(BRConstants.HEADER_CONTENT_TYPE, BRConstants.HEADER_VALUE_ACCEPT)
                .header(BRConstants.HEADER_ACCEPT, BRConstants.HEADER_VALUE_ACCEPT).get().build();

        return APIClient.getInstance(context).sendRequest(request, true);
    }

    /**
     * This method fetches a specific token by saleAddress
     *
     * @param context     The Context of the caller
     * @param saleAddress Optional sale address value if we are looking for a specific token response.
     */
    public static TokenItem getTokenItem(Context context, String saleAddress) {
        if (mTokenItems != null && mTokenItems.size() > 0) {
            for (TokenItem item : mTokenItems) {
                if (item.address.equalsIgnoreCase(saleAddress)) {
                    return item;
                }
            }
        }
        return null;
    }

    public static synchronized ArrayList<TokenItem> getTokenItems(Context context) {
        if (mTokenItems == null) {
            mTokenItems = getTokensFromFile(context);
        }
        return mTokenItems;
    }

    public static void fetchTokensFromServer(Context context) {
        APIClient.BRResponse response = fetchTokensFromServer(context, JsonRpcHelper.getTokenListUrl());
        if (response != null && !response.getBodyText().isEmpty()) {
            // Synchronize on the class object since getTokenItems is static and also synchronizes on the class object
            // rather than on an instance of the class.
            synchronized (TokenItem.class) {
                String responseBody = response.getBodyText();
                mTokenItems = parseJsonToTokenList(context, responseBody);
                saveTokenListToFile(context, responseBody);
            }
        }
    }

    private static ArrayList<TokenItem> parseJsonToTokenList(Context context, String jsonString) {
        ArrayList<TokenItem> tokenItems = new ArrayList<>();

        // Iterate over the token list and announce each token to Core
        try {
            JSONObject boyJson = new JSONObject(jsonString);
            JSONArray tokenListArray = boyJson.optJSONArray("result");
            WalletEthManager ethWalletManager = WalletEthManager.getInstance(BreadApp.getBreadContext());

            for (int i = 0; i < tokenListArray.length(); i++) {
                JSONObject tokenObject = tokenListArray.getJSONObject(i);
                String address = "";
                String name = "";
                String symbol = "";
                String contractInitialValue = "";
                String image = "";
                String mStartColor = "";
                String mEndColor = "";
                int decimals = 0;

                if (tokenObject.has(FIELD_CONTRACT_ADDRESS)) {
                    address = tokenObject.optString(FIELD_CONTRACT_ADDRESS);
                }

                if (tokenObject.has(FIELD_NAME)) {
                    name = tokenObject.optString(FIELD_NAME);
                }

                if (tokenObject.has(FIELD_CODE)) {
                    symbol = tokenObject.optString(FIELD_CODE);
                }

                if (tokenObject.has(FIELD_SCALE)) {
                    decimals = tokenObject.optInt(FIELD_SCALE);
                }

                if (tokenObject.has(FIELD_CONTRACT_INITIAL_VALUE)) {
                    contractInitialValue = tokenObject.getString(FIELD_CONTRACT_INITIAL_VALUE);
                }

                if (!Utils.isNullOrEmpty(address) && !Utils.isNullOrEmpty(name) && !Utils.isNullOrEmpty(symbol)) {
                    if (ethWalletManager==null)ethWalletManager=WalletEthManager.getInstance(BreadApp.getBreadContext());
                    if (ethWalletManager != null) {
                        ethWalletManager.node.announceToken(address, symbol, name, "", decimals, null, null, 0);
                    }
                    // Keep a local reference to the token list, so that we can make token symbols to their
                    // gradient colors in WalletListAdapter
                    if (tokenObject.has(FIELD_IMAG)) {
                        image = tokenObject.optString(FIELD_IMAG);
                    }
                    if (tokenObject.has(FIELD_START_COLOR)) {
                        mStartColor = tokenObject.optString(FIELD_START_COLOR);
                    }
                    if (tokenObject.has(FIELD_END_COLOR)) {
                        mEndColor = tokenObject.optString(FIELD_END_COLOR);
                    }

                    TokenItem item = new TokenItem(address, symbol, name, image);

                    item.setStartColor(mStartColor);
                    item.setEndColor(mEndColor);
                    item.setContractInitialValue(contractInitialValue);
                    tokenItems.add(item);
                    WalletTokenManager.mTokenIsos.put(item.symbol.toLowerCase(), item.address.toLowerCase());
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing token list response from server:", e);
        }
        return tokenItems;
    }

    private static void saveTokenListToFile(Context context, String jsonResponse) {
        String filePath = BreadApp.getMyApp().getFilesDir().getAbsolutePath() + File.separator + TOKENS_FILENAME;
        try {
            BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath, true), "UTF-8"));
//            FileWriter fileWriter = new FileWriter(filePath);
            fileWriter.write(jsonResponse);
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing tokens JSON response to tokens.json:", e);
        }
    }

    private static ArrayList<TokenItem> getTokensFromFile(Context context) {
        try {
            File tokensFile = new File(context.getFilesDir().getPath() + File.separator + TOKENS_FILENAME);
            byte[] fileBytes;
            if (tokensFile.exists()) {
                FileInputStream fileInputStream = new FileInputStream(tokensFile);
                int size = fileInputStream.available();
                fileBytes = new byte[size];
                fileInputStream.read(fileBytes);
                fileInputStream.close();
            } else {
                InputStream json = context.getResources().openRawResource(R.raw.tokens);
                int size = json.available();
                fileBytes = new byte[size];
                json.read(fileBytes);
                json.close();
            }
//            return parseJsonToTokenList(context, new String(fileBytes,"UTF-8"));
            if (Utils.isNullOrEmpty(tJson)){
                return parseJsonToTokenList(context, new String(fileBytes));

            }else {

                saveTokenListToFile(BreadApp.getBreadContext(), tJson);
                return parseJsonToTokenList(context, tJson);
            }


        } catch (IOException e) {
            Log.e(TAG, "Error reading tokens.json file: ", e);
            return parseJsonToTokenList(context,tJson);
        }

    }

//    public static String getTokenIconPath(Context context, String currencyCode, boolean withBackground) {
//        String bundleResource = APIClient.getInstance(context)
//                .getExtractedPath(context, APIClient.TOKEN_ASSETS_BUNDLE_NAME, null);
//        String iconDirectoryName = withBackground
//                ? ICON_DIRECTORY_NAME_WHITE_SQUARE_BACKGROUND : ICON_DIRECTORY_NAME_WHITE_NO_BACKGROUND;
//        String iconFileName = String.format(ICON_FILE_NAME_FORMAT, currencyCode);
//        File bundleDirectory = new File(bundleResource);
//        if (bundleDirectory.exists() && bundleDirectory.isDirectory()) {
//            for (File iconDirectory : bundleDirectory.listFiles()) {
//                if (iconDirectory.getName().equalsIgnoreCase(iconDirectoryName) && iconDirectory.isDirectory()) {
//                    for (File iconFile : iconDirectory.listFiles()) {
//                        if (iconFile.getName().equalsIgnoreCase(iconFileName)) {
//                            return iconFile.getAbsolutePath();
//                        }
//                    }
//                }
//            }
//        }
//
//        return "";
//    }

    public static String getTokenStartColor(String currencyCode) {
        for (TokenItem token : mTokenItems) {
            if (token.symbol.equalsIgnoreCase(currencyCode)) {
                return token.getStartColor();
            }
        }

        return "#ffffff";
    }

    public static String getTokenEndColor(String currencyCode) {
        for (TokenItem token : mTokenItems) {
            if (token.symbol.equalsIgnoreCase(currencyCode)) {
                return token.getEndColor();
            }
        }

        return "#ffffff";
    }
    public  static void getTokenDatas(){
        HttpUtils.sendOkHttpRequest(JsonRpcHelper.getTokenListUrl(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, Response response) throws IOException {
                synchronized (TokenItem.class) {
                    assert response.body() != null;
                    String responseBody = response.body().string();
                    tJson=responseBody;
                    MyLog.i("-------------------------ADDRESS-" + "请求到数据");
//                    mTokenItems = parseJsonToTokenList(BreadApp.getBreadContext(), responseBody);
                    saveTokenListToFile(BreadApp.getBreadContext(), responseBody);
                }

            }
        });
    }
}