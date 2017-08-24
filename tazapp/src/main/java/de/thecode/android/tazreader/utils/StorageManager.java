package de.thecode.android.tazreader.utils;

import android.content.Context;
import android.os.Environment;
import android.support.v4.content.ContextCompat;

import de.thecode.android.tazreader.data.FileCachePDFThumbHelper;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.Resource;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.secure.HashHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import timber.log.Timber;


public class StorageManager {

    public static final String TEMP     = "temp";
    public static final String PAPER    = "paper";
    public static final String RESOURCE = "resource";

    private static final String DOWNLOAD = "download";
    private static final String IMPORT   = "import";

    private static StorageManager instance;

    public static StorageManager getInstance(Context context) {
        if (instance == null) instance = new StorageManager(context.getApplicationContext());
        return instance;
    }

    private Context     mContext;
    private TazSettings preferences;

    private StorageManager(Context context) {
        mContext = context;
        preferences = TazSettings.getInstance(context);
    }

    public File get(String type) {
        File[] newResults = ContextCompat.getExternalFilesDirs(mContext, type);
        //Fallback for Emulators
        if (newResults[0] == null) newResults[0] = mContext.getFilesDir();


        File result = mContext.getExternalFilesDir(type);


        if (result != null) //noinspection ResultOfMethodCallIgnored
            result.mkdirs();
        return result;
    }


    public File getCache(String subDir) {
        File[] test = getDataLocationDirs();

        File newResults[] = ContextCompat.getExternalCacheDirs(mContext);
        //Fallback for Emulators
        if (newResults[0] == null) newResults[0] = mContext.getCacheDir();

        File result = mContext.getExternalCacheDir();
        if (result != null) {
            if (subDir != null) result = new File(result, subDir);
            result.mkdirs();
        }
        return result;
    }

    public File[] getDataLocationDirs()  {
        File[] results = ContextCompat.getExternalFilesDirs(mContext,null);
        //Fallback emulator
        if (results[0] == null) results[0] = mContext.getFilesDir();
        for (int i = 0; i < results.length; i++) {
            results[i] = results[i].getParentFile();
        }
        return results;
    }

    public File getDataLocationDefaultDir()  {
        return getDataLocationDirs()[0];
    }

    public boolean dataLocationDirExists(){
        File dataLocation = new File(preferences.getPrefString(TazSettings.PREFKEY.DATA_LOCATION,null));
        return dataLocation.exists();
    }


    public File getDownloadCache() {
        return getCache(DOWNLOAD);
    }

    public File getImportCache() {
        return getCache(IMPORT);
    }


    public File getDownloadFile(Paper paper) {
        try {
            return getDownloadFile(HashHelper.getHash(paper.getBookId(), HashHelper.UTF_8, HashHelper.SHA_1));
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            Timber.e(e, "Error");
        }
        return null;
    }

    public File getDownloadFile(Resource resource) {
        return getDownloadFile(resource.getKey());
    }

    private File getDownloadFile(String key) {
        File downloadCache = getDownloadCache();
        if (downloadCache != null) return new File(getDownloadCache(), key);
        return null;
    }

    public File getPaperDirectory(Paper paper) {
        return new File(get(PAPER), paper.getBookId());
    }

    public File getResourceDirectory(String key) {
        return new File(get(RESOURCE), key);
    }

    public void deletePaperDir(Paper paper) {
        if (getPaperDirectory(paper).exists()) FileUtils.deleteQuietly(getPaperDirectory(paper));
//        Utils.deleteDir(getPaperDirectory(paper));
        new FileCachePDFThumbHelper(this, paper.getFileHash()).deleteDir();
    }

    public void deleteResourceDir(String key) {
        File dir = getResourceDirectory(key);
        if (dir.exists()) FileUtils.deleteQuietly(getResourceDirectory(key));
        //Utils.deleteDir(getResourceDirectory(key));
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

}
