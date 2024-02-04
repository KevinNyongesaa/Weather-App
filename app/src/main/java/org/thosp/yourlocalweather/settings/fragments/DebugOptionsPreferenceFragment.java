package org.thosp.yourlocalweather.settings.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.thosp.yourlocalweather.R;
import org.thosp.yourlocalweather.utils.Constants;
import org.thosp.yourlocalweather.utils.LogToFile;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_FILE;
import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_FILE_LASTING_HOURS;
import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_TO_FILE;
import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_URI_AUTHORITY;
import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_URI_PATH;
import static org.thosp.yourlocalweather.utils.Constants.KEY_DEBUG_URI_SCHEME;

public class DebugOptionsPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_debug);
        initLogFileChooser();
        initLogFileLasting();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

        if (view != null) {
            view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Preference preference = findPreference(KEY_DEBUG_FILE);
        String uriAuthority = preferences.getString(KEY_DEBUG_URI_AUTHORITY, null);
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && uriAuthority != null) {
            try {
                String decodedUriAuthority = URLDecoder.decode(uriAuthority, StandardCharsets.UTF_8.name());
                String path = URLDecoder.decode(preferences.getString(Constants.KEY_DEBUG_URI_PATH, ""), StandardCharsets.UTF_8.name());
                String scheme = preferences.getString(Constants.KEY_DEBUG_URI_SCHEME, "");
                preference.setSummary(scheme + "://" + decodedUriAuthority + "/" + path);
            } catch (UnsupportedEncodingException ex) {
                Log.e("DebugOptionsPreference", ex.getMessage());
            }
        } else {
            preference.setSummary(preferences.getString(KEY_DEBUG_FILE, ""));
        }
    }

    private void initLogFileChooser() {

        Preference logToFileCheckbox = findPreference(KEY_DEBUG_TO_FILE);
        logToFileCheckbox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, Object value) {
                if (!checkWriteToSdcardPermission()) {
                    return false;
                }
                boolean logToFile = (Boolean) value;
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                preferences.edit().putBoolean(KEY_DEBUG_TO_FILE, logToFile).apply();
                LogToFile.logToFileEnabled = logToFile;
                return true;
            }
        });

        Preference buttonFileLog = findPreference(KEY_DEBUG_FILE);
        buttonFileLog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, "log-yourlocalweather.txt");
                    startActivityForResult(intent, 12344556);
                    fileChooserPreference = preference;
                    return true;
                } else {
                    new ChooserDialog(getActivity())
                            .withFilter(true, false)
                            .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                            .withChosenListener(new ChooserDialog.Result() {
                                @Override
                                public void onChoosePath(String path, File pathFile) {
                                    String logFileName = path + "/log-yourlocalweather.txt";
                                    LogToFile.logFilePathname = logFileName;
                                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                    preferences.edit().putString(KEY_DEBUG_FILE, logFileName).apply();
                                    preference.setSummary(preferences.getString(KEY_DEBUG_FILE, ""));
                                }
                            })
                            .build()
                            .show();
                    return true;
                }
            }
        });

    }

    Preference fileChooserPreference;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((data == null) || (fileChooserPreference == null) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        Uri uri = data.getData();
        final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        try {
            LogToFile.logFileUri = uri;
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            preferences.edit()
                    .putString(KEY_DEBUG_URI_AUTHORITY, uri.getEncodedAuthority())
                    .putString(KEY_DEBUG_URI_PATH, uri.getEncodedPath())
                    .putString(KEY_DEBUG_URI_SCHEME, uri.getScheme())
                    .apply();
            fileChooserPreference.setSummary(uri.getScheme() + "://" + uri.getAuthority() + "/" + uri.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        fileChooserPreference = null;
    }

    private boolean checkWriteToSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return true;
        }
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        123456);
            }
            return false;
        }
        return true;
    }

    private void initLogFileLasting() {
        Preference logFileLasting = findPreference(KEY_DEBUG_FILE_LASTING_HOURS);
        logFileLasting.setSummary(
                getLogFileLastingLabel(Integer.parseInt(
                        PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(KEY_DEBUG_FILE_LASTING_HOURS, "24"))
                )
        );
        logFileLasting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference logFileLasting, Object value) {
                String logFileLastingHoursTxt = (String) value;
                Integer logFileLastingHours = Integer.valueOf(logFileLastingHoursTxt);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                preferences.edit().putString(KEY_DEBUG_FILE_LASTING_HOURS, logFileLastingHoursTxt).apply();
                logFileLasting.setSummary(getString(getLogFileLastingLabel(logFileLastingHours)));
                LogToFile.logFileHoursOfLasting = logFileLastingHours;
                return true;
            }
        });
    }

    private int getLogFileLastingLabel(int logFileLastingValue) {
        int logFileLastingId;
        switch (logFileLastingValue) {
            case 12:
                logFileLastingId = R.string.log_file_12_label;
                break;
            case 48:
                logFileLastingId = R.string.log_file_48_label;
                break;
            case 72:
                logFileLastingId = R.string.log_file_72_label;
                break;
            case 168:
                logFileLastingId = R.string.log_file_168_label;
                break;
            case 720:
                logFileLastingId = R.string.log_file_720_label;
                break;
            case 24:
            default:
                logFileLastingId = R.string.log_file_24_label;
                break;
        }
        return logFileLastingId;
    }
}
