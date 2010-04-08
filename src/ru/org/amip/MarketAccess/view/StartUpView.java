package ru.org.amip.MarketAccess.view;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import ru.org.amip.MarketAccess.R;
import ru.org.amip.MarketAccess.utils.AppManager;
import ru.org.amip.MarketAccess.utils.CompleteListener;
import ru.org.amip.MarketAccess.utils.RunWithProgress;

public class StartUpView extends TabActivity implements OnTabChangeListener {
  public static final String MARKET_ACCESS = "MarketAccess";
  public static final String APPLY_ON_BOOT = "applyOnBoot";
  public static final String APPLY_SIM_NUM = "applySimNumeric";
  public static final String SHOW_NOTIFICATION = "showNotification";
  public static final String ACTUAL = "actual";
  private static final String CUSTOM = "custom";
  private static final String LIST = "list";
  private static final String SIM_NUM = "simNumeric";
  private static final String BACKUP_AVAILABLE = "backupAvailable";

  private TelephonyManager tm;
  private TextView simNumeric;
  private Button restore;
  private CheckBox bootCheckbox;
  private CheckBox notificationCheckbox;
  private SharedPreferences preferences;
  private ListView list;

  public void setList(ListView list) {
    this.list = list;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    setContentView(R.layout.mainview);
    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    setupActualTab();
    setupCustomTab();
    setupListAndTabs();
  }

  private void setupCheckboxes() {
    bootCheckbox = (CheckBox) findViewById(R.id.bootCheckbox);
    notificationCheckbox = (CheckBox) findViewById(R.id.notificationCheckbox);

    boolean applyOnBoot = preferences.getBoolean(APPLY_ON_BOOT, false);

    bootCheckbox.setChecked(applyOnBoot);
    notificationCheckbox.setChecked(preferences.getBoolean(SHOW_NOTIFICATION, false));
    notificationCheckbox.setEnabled(applyOnBoot);

    bootCheckbox.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (((CheckBox) view).isChecked()) {
          new AlertDialog.Builder(StartUpView.this)
            .setMessage(R.string.boot_warning)
            .setCancelable(false)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int id) {
                final String sim = simNumeric.getText().toString();
                final String msg = String.format(getString(R.string.boot_emulation_for), sim);
                saveBootSettings();
                Toast.makeText(StartUpView.this, msg, Toast.LENGTH_SHORT).show();
                notificationCheckbox.setEnabled(true);
              }
            }).
            setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int i) {
                dialog.cancel();
                bootCheckbox.setChecked(false);
              }
            }).create().show();
        } else {
          saveBootSettings();
          Toast.makeText(StartUpView.this, R.string.boot_emulation_off, Toast.LENGTH_SHORT).show();
          notificationCheckbox.setEnabled(false);
        }
      }
    });

    notificationCheckbox.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        saveNotificationSettings();
      }
    });
  }

  private void setupListAndTabs() {
    TabHost mTabHost = getTabHost();
    mTabHost.addTab(mTabHost.newTabSpec(ACTUAL).setIndicator(getString(R.string.actual)).setContent(R.id.actual));

    TabSpec tab = mTabHost.newTabSpec(LIST).setIndicator(getString(R.string.settings));
    tab.setContent(new Intent(this, ListView.class));
    mTabHost.addTab(tab);

    mTabHost.addTab(mTabHost.newTabSpec(CUSTOM).setIndicator(getString(R.string.custom)).setContent(R.id.custom));
    mTabHost.setOnTabChangedListener(this);

    mTabHost.setCurrentTabByTag(ACTUAL);
  }

  private void setupActualTab() {
    simNumeric = (TextView) findViewById(R.id.actualsimNumericValue);
    Button save = (Button) findViewById(R.id.buttonSave);
    save.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        backupSettings();
      }
    });
    restore = (Button) findViewById(R.id.buttonRestore);
    restore.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        restoreSettings();
      }
    });
    if (!preferences.getBoolean(BACKUP_AVAILABLE, false)) {
      restore.setEnabled(false);
    }
    setupCheckboxes();
    updateActualView();
  }

  private void setupCustomTab() {
    final TextView simNumeric = (TextView) findViewById(R.id.customsimNumericValue);
    simNumeric.setText(getSimOperator());
    Button setValues = (Button) findViewById(R.id.customsetValues);
    setValues.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        final RunWithProgress rwp =
          new RunWithProgress(StartUpView.this, simNumeric.getText().toString(), getString(R.string.emulating));
        rwp.setCompleteListener(new CompleteListener() {
          @Override
          public void onComplete() {
            if (list != null) list.invalidate();
          }
        });
        rwp.doRun();
      }
    });
  }

  private void saveBootSettings() {
    SharedPreferences.Editor editor = preferences.edit();
    editor.putBoolean(APPLY_ON_BOOT, bootCheckbox.isChecked());
    editor.putString(APPLY_SIM_NUM, simNumeric.getText().toString());
    editor.commit();
  }

  private void saveNotificationSettings() {
    SharedPreferences.Editor editor = preferences.edit();
    editor.putBoolean(SHOW_NOTIFICATION, notificationCheckbox.isChecked());
    editor.commit();
  }

  private void backupSettings() {
    final String sim = simNumeric.getText().toString();
    SharedPreferences.Editor editor = preferences.edit();
    editor.putBoolean(BACKUP_AVAILABLE, true);
    editor.putString(SIM_NUM, sim);
    editor.commit();
    restore.setEnabled(true);
    Toast.makeText(this, String.format(getString(R.string.backup_ok), sim), Toast.LENGTH_SHORT).show();
  }

  private void restoreSettings() {
    final String sim = preferences.getString(SIM_NUM, "");
    new AlertDialog.Builder(this)
      .setMessage(String.format(getString(R.string.confirm_restore), sim))
      .setCancelable(false)
      .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          final RunWithProgress rwp = new RunWithProgress(StartUpView.this, sim, getString(R.string.restoring));
          rwp.setCompleteListener(new CompleteListener() {
            @Override
            public void onComplete() {
              updateActualView();
              if (list != null) list.invalidate();
            }
          });
          rwp.doRun();
        }
      }).
      setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int i) {
          dialog.cancel();
        }
      }).create().show();
  }

  public void updateActualView() {
    simNumeric.setText(getSimOperator());
  }

  public String getSimOperator() {return tm.getSimOperator();}

  @Override
  public void onTabChanged(String tabId) {
    if (tabId.equals(ACTUAL)) {
      updateActualView();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
      suicide();
    }
    return super.onKeyDown(keyCode, event);
  }

  public static void suicide() {
    AppManager.getInstance().kill("ru.org.amip.MarketAccess");
  }
}
