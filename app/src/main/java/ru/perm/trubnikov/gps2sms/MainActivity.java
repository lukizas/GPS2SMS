package ru.perm.trubnikov.gps2sms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    // Menu
    public static final int IDM_SETTINGS = 101;
    public static final int IDM_RATE = 105;

    // Activities
    private static final int ACT_RESULT_CHOOSE_CONTACT = 1001;
    private static final int ACT_RESULT_SETTINGS = 1002;

    // Dialogs
    private static final int SEND_SMS_DIALOG_ID = 0;
    private final static int SAVE_POINT_DIALOG_ID = 1;

    ProgressDialog mSMSProgressDialog;

    // My GPS states
    public static final int GPS_PROVIDER_DISABLED = 1;
    public static final int GPS_GETTING_COORDINATES = 2;
    public static final int GPS_GOT_COORDINATES = 3;
    //public static final int GPS_PROVIDER_UNAVIALABLE = 4;
    //public static final int GPS_PROVIDER_OUT_OF_SERVICE = 5;
    public static final int GPS_PAUSE_SCANNING = 6;

    // Location manager
    private LocationManager manager;

    // SMS thread
    ThreadSendSMS mThreadSendSMS;

    // Views
    ImageButton chooseContactBtn;
    ImageButton sendpbtn;
    ImageButton btnShare;
    ImageButton btnMap;
    ImageButton btnCopy;
    ImageButton btnSave;
    EditText plainPh;
    Button enableGPSBtn;
    TextView GPSstate;
    Menu mMenu;

    // Globals
    private String coordsToSend;
    private String coordsToShare;
    private String coordsToNavitel;
    private String phoneToSendSMS;

    // Database
    DBHelper dbHelper;

    // Define the Handler that receives messages from the thread and update the
    // progress
    // SMS send thread. Result handling
    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {

            String res_send = msg.getData().getString("res_send");
            // String res_deliver = msg.getData().getString("res_deliver");

            dismissDialog(SEND_SMS_DIALOG_ID);

            if (res_send.equalsIgnoreCase(getString(R.string.info_sms_sent))) {
                // HideKeyboard();
                Intent intent = new Intent(MainActivity.this,
                        AnotherMsgActivity.class);
                startActivity(intent);
            } else {
                DBHelper.ShowToastT(MainActivity.this, res_send,
                        Toast.LENGTH_SHORT);
            }

        }
    };

    // Location events (we use GPS only)
    private LocationListener locListener = new LocationListener() {

        public void onLocationChanged(Location argLocation) {
            printLocation(argLocation, GPS_GOT_COORDINATES);
        }

        @Override
        public void onProviderDisabled(String arg0) {
            printLocation(null, GPS_PROVIDER_DISABLED);
        }

        @Override
        public void onProviderEnabled(String arg0) {
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    };

    private void printLocation(Location loc, int state) {

        String accuracy;

        switch (state) {
            case GPS_PROVIDER_DISABLED:
                GPSstate.setText(R.string.gps_state_disabled);
                setGPSStateAccentColor();
                enableGPSBtn.setVisibility(View.VISIBLE);
                break;
            case GPS_GETTING_COORDINATES:
                GPSstate.setText(R.string.gps_state_in_progress);
                setGPSStateNormalColor();
                enableGPSBtn.setVisibility(View.INVISIBLE);
                break;
            case GPS_PAUSE_SCANNING:
                GPSstate.setText("");
                enableGPSBtn.setVisibility(View.INVISIBLE);
                break;
            case GPS_GOT_COORDINATES:
                if (loc != null) {

                    // Accuracy
                    if (loc.getAccuracy() < 0.0001) {
                        accuracy = "?";
                    } else if (loc.getAccuracy() > 99) {
                        accuracy = "> 99";
                    } else {
                        accuracy = String.format(Locale.US, "%2.0f",
                                loc.getAccuracy());
                    }

                    String separ = System.getProperty("line.separator");

                    String la = String
                            .format(Locale.US, "%2.7f", loc.getLatitude());
                    String lo = String.format(Locale.US, "%3.7f",
                            loc.getLongitude());

                    coordsToSend = la + "," + lo;

                    coordsToNavitel = "<NavitelLoc>" + (loc.getLatitude() > 0 ? "N" : "S") + la + "° " + (loc.getLongitude() > 0 ? "E" : "W") + lo + "°<N>";

                    coordsToShare = DBHelper.getShareBody(MainActivity.this,
                            coordsToSend, accuracy);

                    GPSstate.setText(getString(R.string.info_print1) + " "
                            + accuracy + " " + getString(R.string.info_print2)
                            + separ + getString(R.string.info_latitude) + " " + la
                            + separ + getString(R.string.info_longitude) + " " + lo);

                    setGPSStateNormalColor();

                    btnShare.setVisibility(View.VISIBLE);
                    btnCopy.setVisibility(View.VISIBLE);
                    btnMap.setVisibility(View.VISIBLE);
                    btnSave.setVisibility(View.VISIBLE);

                    ShowSendButton();
                    enableGPSBtn.setVisibility(View.INVISIBLE);

                } else {
                    GPSstate.setText(R.string.gps_state_unavialable);
                    setGPSStateAccentColor();
                    enableGPSBtn.setVisibility(View.VISIBLE);
                }
                break;
        }

    }

    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        mMenu = menu;

        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);

        menu.add(Menu.NONE, IDM_SETTINGS, Menu.NONE,
                R.string.menu_item_settings);
        menu.add(Menu.NONE, IDM_RATE, Menu.NONE, R.string.menu_item_rate);

        return (super.onCreateOptionsMenu(menu));
    }

    // Dialogs
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {

            case SEND_SMS_DIALOG_ID:
                mSMSProgressDialog = new ProgressDialog(MainActivity.this);
                // mCatProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mSMSProgressDialog.setCanceledOnTouchOutside(false);
                mSMSProgressDialog.setCancelable(false);
                mSMSProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mSMSProgressDialog.setMessage(getString(R.string.info_please_wait)
                        + " " + phoneToSendSMS);
                return mSMSProgressDialog;

            case SAVE_POINT_DIALOG_ID:
                LayoutInflater inflater_sp = getLayoutInflater();
                View layout_sp = inflater_sp.inflate(R.layout.save_point_dialog,
                        (ViewGroup) findViewById(R.id.save_point_dialog_layout));

                AlertDialog.Builder builder_sp = new AlertDialog.Builder(this);
                builder_sp.setView(layout_sp);

                final EditText lPointName = (EditText) layout_sp
                        .findViewById(R.id.point_edit_text);

                // builder_sp.setMessage(getString(R.string.save_point_dlg_header));

                builder_sp.setPositiveButton(getString(R.string.save_btn_txt),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dbHelper = new DBHelper(MainActivity.this);
                                dbHelper.insertMyCoord(lPointName.getText()
                                        .toString(), coordsToSend);
                                dbHelper.close();
                                lPointName.setText(""); // Чистим
                                DBHelper.ShowToast(MainActivity.this,
                                        R.string.point_saved, Toast.LENGTH_LONG);
                            }
                        });

                builder_sp.setNegativeButton(getString(R.string.cancel_btn_txt),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                lPointName.setText(""); // Чистим
                                dialog.cancel();
                            }
                        });

                builder_sp.setCancelable(true);
                AlertDialog dialog = builder_sp.create();
                dialog.setTitle(getString(R.string.save_point_dlg_header));
                // show keyboard automatically
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                return dialog;

        }
        return null;
    }

    // Menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case IDM_SETTINGS:
                Intent i = new Intent(this, UserSettingActivity.class);
                startActivityForResult(i, ACT_RESULT_SETTINGS);
                break;
            case R.id.action_sms_regexp:
                Intent intent = new Intent(MainActivity.this, TabsActivity.class);
                startActivity(intent);
                break;
            case IDM_RATE:
                Intent int_rate = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id="
                                + getApplicationContext().getPackageName()));
                int_rate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(int_rate);
                break;

            default:
                return false;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Держать ли экран включенным?
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        if (sharedPrefs.getBoolean("prefKeepScreen", true)) {
            getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Возобновляем работу с GPS-приемником
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                locListener);

        btnShare.setVisibility(View.INVISIBLE);
        btnCopy.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);
        btnSave.setVisibility(View.INVISIBLE);

        HideSendButton();

        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            printLocation(null, GPS_GETTING_COORDINATES);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.removeUpdates(locListener);
    }

    public void showSelectedNumber(String number, String name) {
        plainPh.setText(number);
        plainPh.setSelection(plainPh.getText().length());
        // @ TODO отображать как-то имя контакта?
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        switch (reqCode) {
            case (ACT_RESULT_CHOOSE_CONTACT):
                String number;
                String name;
                // int type = 0;
                if (data != null) {
                    Uri uri = data.getData();

                    if (uri != null) {
                        Cursor c = null;
                        try {

                            c = getContentResolver()
                                    .query(uri,
                                            new String[]{
                                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                                    ContactsContract.CommonDataKinds.Phone.TYPE,
                                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                                            null, null, null);

                            if (c != null && c.moveToFirst()) {
                                number = c.getString(0);
                                number = number.replace("-", "").replace(" ", "")
                                        .replace("(", "").replace(")", "");
                                // type = c.getInt(1);
                                name = c.getString(2);
                                showSelectedNumber(number, name);

                                // update
                                dbHelper = new DBHelper(MainActivity.this);
                                dbHelper.setSlot(0, name, number);
                                dbHelper.close();

                            }
                        } finally {
                            if (c != null) {
                                c.close();
                            }
                        }
                    }
                }

                break;
        }
    }
/*
    public static void setImageButtonEnabled(Context ctxt, boolean enabled,
			ImageButton item, int iconResId) {

		item.setEnabled(enabled);
		Drawable originalIcon = ctxt.getResources().getDrawable(iconResId);
		Drawable icon = enabled ? originalIcon
				: convertDrawableToGrayScale(originalIcon);
		item.setImageDrawable(icon);
	}

	public static void setMenuItemEnabled(Context ctxt, boolean enabled,
			MenuItem item, int iconResId) {

		item.setEnabled(enabled);
		Drawable originalIcon = ctxt.getResources().getDrawable(iconResId);
		Drawable icon = enabled ? originalIcon
				: convertDrawableToGrayScale(originalIcon);
		item.setIcon(icon);
	}
*/

    /**
     * Mutates and applies a filter that converts the given drawable to a Gray
     * image. This method may be used to simulate the color of disable icons in
     * Honeycomb's ActionBar.
     * <p/>
     * a mutated version of the given drawable with a color filter
     * applied.
     */
	/*public static Drawable convertDrawableToGrayScale(Drawable drawable) {
		if (drawable == null)
			return null;

		Drawable res = drawable.mutate();
		res.setColorFilter(Color.GRAY, Mode.SRC_IN);
		return res;
	}*/


    // ------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Определение темы должно быть ДО super.onCreate и setContentView
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        setTheme(sharedPrefs.getString("prefAppTheme", "1").equalsIgnoreCase(
                "1") ? R.style.AppTheme_Dark : R.style.AppTheme_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Plain phone number
        plainPh = (EditText) findViewById(R.id.editText1);

        // Select contact
        chooseContactBtn = (ImageButton) findViewById(R.id.choose_contact);
        chooseContactBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        ContactsContract.Contacts.CONTENT_URI);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, ACT_RESULT_CHOOSE_CONTACT);
            }
        });

        // Stored phone number -> to EditText
        dbHelper = new DBHelper(this);
        showSelectedNumber(dbHelper.getSlot(0, "phone"), "");
        dbHelper.close();

        // GPS init
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Enable GPS button
        enableGPSBtn = (Button) findViewById(R.id.button3);
        enableGPSBtn.setVisibility(View.INVISIBLE);

        enableGPSBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            }

        });

        // Share buttons

        btnShare = (ImageButton) findViewById(R.id.btnShare);
        btnCopy = (ImageButton) findViewById(R.id.btnCopy);
        btnMap = (ImageButton) findViewById(R.id.btnMap);
        btnSave = (ImageButton) findViewById(R.id.btnSave);
        btnShare.setVisibility(View.INVISIBLE);
        btnCopy.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);
        btnSave.setVisibility(View.INVISIBLE);
        btnShare.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent sharingIntent = new Intent(
                        android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareBody = coordsToShare;
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                        getString(R.string.share_topic));
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                        shareBody);
                startActivity(Intent.createChooser(sharingIntent,
                        getString(R.string.share_via)));
            }
        });
        btnCopy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DBHelper.clipboardCopy(getApplicationContext(), coordsToSend,
                        DBHelper.getGoogleMapsLink(coordsToSend),
                        DBHelper.getOSMLink(coordsToSend));
                DBHelper.ShowToast(MainActivity.this, R.string.text_copied,
                        Toast.LENGTH_LONG);
            }
        });
        btnMap.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DBHelper.openOnMap(getApplicationContext(), coordsToSend);
            }
        });
        btnSave.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(SAVE_POINT_DIALOG_ID);
            }
        });

        // Send buttons
        sendpbtn = (ImageButton) findViewById(R.id.send_plain);
        HideSendButton();
        sendpbtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateSMSSend(0);
            }
        });

        // GPS-state TextView init
        GPSstate = (TextView) findViewById(R.id.textView1);
        setGPSStateNormalColor();

    }

    private void setGPSStateNormalColor() {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        GPSstate.setTextColor(sharedPrefs.getString("prefAppTheme", "1")
                .equalsIgnoreCase("1") ? Color.parseColor("#FFFFFF") : Color
                .parseColor("#000000"));
    }

    private void setGPSStateAccentColor() {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        GPSstate.setTextColor(sharedPrefs.getString("prefAppTheme", "1")
                .equalsIgnoreCase("1") ? getResources().getColor(R.color.accent_dt) : getResources().getColor(R.color.accent_lt));
    }

    // ------------------------------------------------------------------------------------------

    protected void sendSMS(String lCoords, boolean addText, int Receiver) {

        dbHelper = new DBHelper(MainActivity.this);
        String smsMsg = lCoords;
        if (addText) {
            SharedPreferences sharedPrefs = PreferenceManager
                    .getDefaultSharedPreferences(this);
            smsMsg = sharedPrefs.getString("prefSMSText",
                    getString(R.string.default_sms_msg)) + " " + smsMsg;
        }

        if (Receiver == 0) {
            String plph = plainPh.getText().toString().replace("-", "")
                    .replace(" ", "").replace("(", "").replace(")", "");
            dbHelper.setSlot(Receiver, plph, plph);
        }

        phoneToSendSMS = dbHelper.getSlot(Receiver, "phone");
        dbHelper.close();

        if (phoneToSendSMS.equalsIgnoreCase("")) {
            if (Receiver == 0)
                DBHelper.ShowToast(MainActivity.this,
                        R.string.error_no_phone_number, Toast.LENGTH_LONG);
            else
                DBHelper.ShowToast(MainActivity.this,
                        R.string.error_contact_is_not_selected,
                        Toast.LENGTH_LONG);
        } else {
            showDialog(SEND_SMS_DIALOG_ID);

            // Запускаем новый поток для отправки SMS
            mThreadSendSMS = new ThreadSendSMS(handler, getApplicationContext());
            mThreadSendSMS.setMsg(smsMsg);
            mThreadSendSMS.setPhone(phoneToSendSMS);
            mThreadSendSMS.setState(ThreadSendSMS.STATE_RUNNING);
            mThreadSendSMS.start();
        }

    }


    public void initiateSMSSend(int Receiver) {

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        boolean isSendInNavitelFormat = sharedPrefs.getBoolean("prefSendInNavitelFormat", false);

        sendSMS(isSendInNavitelFormat ? coordsToNavitel : coordsToSend,
                !isSendInNavitelFormat,
                Receiver);

    }

    protected void ShowSendButton() {
        sendpbtn.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) plainPh.getLayoutParams();
        params.addRule(RelativeLayout.LEFT_OF, R.id.send_plain);
        plainPh.setLayoutParams(params); //causes layout update
    }

    protected void HideSendButton() {
        sendpbtn.setVisibility(View.INVISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) plainPh.getLayoutParams();
        params.addRule(RelativeLayout.LEFT_OF, 0);
        plainPh.setLayoutParams(params); //causes layout update
    }
	
	/*
	 * TODO
	 * 
	 * Удаление СМС, Фотку на кнопке с выбранным контактом
	 */

}