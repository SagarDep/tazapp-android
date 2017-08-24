package de.thecode.android.tazreader;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.evernote.android.job.JobManager;

import de.thecode.android.tazreader.analytics.AnalyticsWrapper;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.eventbus.EventBusIndex;
import de.thecode.android.tazreader.picasso.PicassoHelper;
import de.thecode.android.tazreader.reader.ReaderActivity;
import de.thecode.android.tazreader.timber.TazTimberTree;
import de.thecode.android.tazreader.utils.BuildTypeProvider;
import de.thecode.android.tazreader.utils.StorageManager;

import org.acra.ACRA;
import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

import java.io.File;

import timber.log.Timber;


public class TazReaderApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Timber.plant(new TazTimberTree(BuildConfig.DEBUG ? Log.VERBOSE : Log.INFO));
        AnalyticsWrapper.initialize(this);
    }

    @Override
    public void onCreate() {

        super.onCreate();

        BuildTypeProvider.installStetho(this);

        if (ACRA.isACRASenderServiceProcess()) return;

        EventBus.builder().addIndex(new EventBusIndex()).installDefaultEventBus();

        JobManager.create(this).addJobCreator(new TazJobCreator());

        PicassoHelper.initPicasso(this);

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder().setDefaultFontPath(getString(R.string.fontRegular))
                                                                     .setFontAttrId(R.attr.fontPath)
                                                                     .build());


        // Migration von alter Version
        int lastVersionCode = TazSettings.getInstance(this)
                                         .getPrefInt(TazSettings.PREFKEY.LASTVERSION, Integer.parseInt(
                                                 String.valueOf(BuildConfig.VERSION_CODE)
                                                       .substring(1)));
        if (lastVersionCode < 16) {
            if (TazSettings.getInstance(this)
                           .getPrefString(TazSettings.PREFKEY.COLSIZE, "0")
                           .equals("4")) TazSettings.getInstance(this)
                                                    .setPref(TazSettings.PREFKEY.COLSIZE, "3");
        }
        if (lastVersionCode < 32) {
            TazSettings.getInstance(this)
                       .removePref(TazSettings.PREFKEY.FONTSIZE);
            TazSettings.getInstance(this)
                       .removePref(TazSettings.PREFKEY.COLSIZE);
            TazSettings.getInstance(this)
                       .setPref(TazSettings.PREFKEY.PAPERMIGRATEFROM, lastVersionCode);
        }
        if (lastVersionCode < 52) {
            //Remvoing all dead prefs from crashlytics
            File dir = new File(getFilesDir().getParent() + "/shared_prefs/");
            String[] children = dir.list();
            for (String aChildren : children) {
                if (aChildren.startsWith("com.crashlytics") || aChildren.startsWith("Twitter") || aChildren.startsWith(
                        "io.fabric")) FileUtils.deleteQuietly(new File(dir, aChildren));
            }
            File oldLibImageDir = StorageManager.getInstance(this)
                                                .getCache("library");
            FileUtils.deleteQuietly(oldLibImageDir);
        }

        // MIGRATION BEENDET, setzten der aktuellen Version
        TazSettings.getInstance(this)
                   .setPref(TazSettings.PREFKEY.LASTVERSION, BuildConfig.VERSION_CODE);

        TazSettings.getInstance(this)
                   .setPref(TazSettings.PREFKEY.ISFOOT, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.FONTSIZE, "10");
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.AUTOLOAD, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.AUTOLOAD_WIFI, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ISSCROLL, !getResources().getBoolean(R.bool.isTablet));
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.COLSIZE, "0.5");
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.THEME, ReaderActivity.THEMES.normal.name());
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.FULLSCREEN, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.CONTENTVERBOSE, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.KEEPSCREEN, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ORIENTATION, "auto");
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.AUTODELETE, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.AUTODELETE_VALUE, 14);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.VIBRATE, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ISSOCIAL, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.PAGEINDEXBUTTON, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.TEXTTOSPEACH, false);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ISCHANGEARTICLE, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ISPAGING, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.ISSCROLLTONEXT, getResources().getBoolean(R.bool.isTablet));
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.PAGETAPTOARTICLE, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.PAGEDOUBLETAPZOOM, true);
        TazSettings.getInstance(this)
                   .setDefaultPref(TazSettings.PREFKEY.DATA_LOCATION, StorageManager.getInstance(this).getDataLocationDefaultDir().getAbsolutePath());


        //Timber.d("Token: %s", FirebaseInstanceId.getInstance().getToken());

    }

}
