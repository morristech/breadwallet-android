package com.breadwallet.presenter.activities;

import android.app.Activity;
import android.content.Intent;
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

import com.breadwallet.R;
import com.breadwallet.presenter.activities.settings.SecurityCenterActivity;
import com.breadwallet.presenter.activities.settings.SettingsActivity;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRButton;
import com.breadwallet.presenter.customviews.BRNotificationBar;
import com.breadwallet.presenter.customviews.BRText;
import com.breadwallet.tools.adapter.WalletListAdapter;
import com.breadwallet.tools.animation.BRAnimator;
import com.breadwallet.tools.listeners.RecyclerItemClickListener;
import com.breadwallet.tools.manager.BREventManager;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.manager.InternetManager;
import com.breadwallet.tools.manager.PromptManager;
import com.breadwallet.tools.sqlite.RatesDataSource;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;

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
    private RelativeLayout mSupport;
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
        Log.e(TAG, "onCreate: 1");

        mWalletRecycler = findViewById(R.id.rv_wallet_list);
        mFiatTotal = findViewById(R.id.total_assets_usd);

        mSettings = findViewById(R.id.settings_row);
        mSecurity = findViewById(R.id.security_row);
        mSupport = findViewById(R.id.support_row);
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
                    BRSharedPrefs.putCurrentWalletIso(HomeActivity.this, mAdapter.getItemAt(position).getIso());
                    Intent newIntent = new Intent(HomeActivity.this, WalletActivity.class);
                    startActivity(newIntent);
                    overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                } else {
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
        mSupport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!BRAnimator.isClickAllowed()) return;
                BaseWalletManager wm = WalletsMaster.getInstance(HomeActivity.this).getCurrentWallet(HomeActivity.this);

                BRAnimator.showSupportFragment(HomeActivity.this, null, wm);
            }
        });


        mPromptDismiss.setColor(Color.parseColor("#b3c0c8"));
        mPromptDismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePrompt();
            }
        });

        mPromptContinue.setColor(Color.parseColor("#4b77f3"));
        mPromptContinue.setOnClickListener(new View.OnClickListener()

        {
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

    public void hidePrompt() {
        mPromptCard.setVisibility(View.GONE);
        Log.e(TAG, "hidePrompt: " + mCurrentPrompt);
        if (mCurrentPrompt == PromptManager.PromptItem.SHARE_DATA) {
            BRSharedPrefs.putPromptDismissed(app, "shareData", true);
        } else if (mCurrentPrompt == PromptManager.PromptItem.FINGER_PRINT) {
            BRSharedPrefs.putPromptDismissed(app, "fingerprint", true);
        }
        if (mCurrentPrompt != null)
            BREventManager.getInstance().pushEvent("prompt." + PromptManager.getInstance().getPromptName(mCurrentPrompt) + ".dismissed");
        mCurrentPrompt = null;

    }

    private void showNextPromptIfNeeded() {
        PromptManager.PromptItem toShow = PromptManager.getInstance().nextPrompt(this);
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