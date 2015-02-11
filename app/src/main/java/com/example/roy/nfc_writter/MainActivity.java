package com.example.roy.nfc_writter;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;


public class MainActivity extends ActionBarActivity {



    EditText mNote;
    NfcAdapter gNfcAdapter;
    PendingIntent gNfcPendingIntent;
    IntentFilter[] gNdefExchangeFilters;
    IntentFilter[] gWriteTagFilters;
    private boolean gWriteMode = false;


    private NdefMessage getNoteAsNdef() {
        byte[] textBytes = mNote.getText().toString().getBytes();
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "text/plain".getBytes(),
                new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] {
                textRecord
        });
    }


    /**
     * 啟動Ndef交換資料模式。
     */
    private void enableNdefExchangeMode()
    {
        // 讓NfcAdatper啟動前景Push資料至Tag或應用程式。
//        gNfcAdapter.enableForegroundNdefPush(MainActivity.this, getNoteAsNdef());
        gNfcAdapter.setNdefPushMessage(getNoteAsNdef(),this);

        // 讓NfcAdapter啟動能夠在前景模式下進行intent filter的dispatch。
        gNfcAdapter.enableForegroundDispatch(this, gNfcPendingIntent, gNdefExchangeFilters, null);
    }

    private void disableNdefExchangeMode()
    {
//        gNfcAdapter.disableNdefExchangeMode(this);
        gNfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * 啟動Tag寫入模式，註冊對應的Intent Filter來前景模式監聽是否有Tag進入的訊息。
     */
    private void enableTagWriteMode()
    {
        gWriteMode = true;
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        gWriteTagFilters = new IntentFilter [] {tagDetected};
        gNfcAdapter.enableForegroundDispatch(this, gNfcPendingIntent, gWriteTagFilters, null);
    }

    /**
     * 停止Tag寫入模式，取消前景模式的監測。
     */
    private void disableTagWriteMode()
    {
        gWriteMode = false;
        gNfcAdapter.disableForegroundDispatch(this);
    }

    private View.OnClickListener gTagWriter = new View.OnClickListener() {

        @Override
        public void onClick(View v)
        {
            // 先停止接收任何的Intent，準備寫入資料至tag；
            disableNdefExchangeMode();
            // 啟動寫入Tag模式，監測是否有Tag進入
            enableTagWriteMode();
            // 顯示對話框，告知將Tag或手機靠近本機的NFC感應區
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Touch tag to write")
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            // 在取消模式下，先關閉監偵有Tag準備寫入的模式，再啟動等待資料交換的模式。
                            // 停止寫入Tag模式，代表已有Tag進入
                            disableTagWriteMode();
                            // 啟動資料交換
                            enableNdefExchangeMode();
                        }
                    }).create().show();

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button).setOnClickListener(this.gTagWriter);
        mNote = (EditText)findViewById(R.id.editText);

        // Handle all of our received NFC intents in this activity.
        gNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // Intent filters for reading a note from a tag or exchanging over p2p.
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefDetected.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) { }
        gNdefExchangeFilters = new IntentFilter[] { ndefDetected };

    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent)
    {


        // 監測到有指定ACTION進入，代表要寫入資料至Tag中。
        // Tag writing mode
        if (gWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag(getNoteAsNdef(), detectedTag);
        }
    }

    boolean writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;

        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();

                if (!ndef.isWritable()) {
                    toast("Tag is read-only.");
                    return false;
                }
                if (ndef.getMaxSize() < size) {
                    toast("Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size
                            + " bytes.");
                    return false;
                }

                ndef.writeNdefMessage(message);
                toast("Wrote message to pre-formatted tag.");
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        toast("Formatted tag and wrote message");
                        return true;
                    } catch (IOException e) {
                        toast("Failed to format tag.");
                        return false;
                    }
                } else {
                    toast("Tag doesn't support NDEF.");
                    return false;
                }
            }
        } catch (Exception e) {
            toast("Failed to write tag");
        }

        return false;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
