package nifty.intern.teamc.IbeaconReceiver;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import nifty.intern.teamc.database.DatabaseManager;

/**
 * Created by USER on 2016/08/31.
 */
public class IbeaconReceiver extends Service {
    private BluetoothAdapter mBluetoothAdapter; // BLE機器のスキャンを行うクラス
    Handler mHandler = new Handler(); // スキャンを別スレッドで行うためのハンドラ
    private static final String TAG = "IbeaconReceiver";
    private final int REPEAT_INTERVAL = 10000; // 更新のくりかえし間隔（ms）
    private Runnable runnable;

    private String MemberID; // 端末の固有番号を格納

    private static String beaconId;
    private static String major;
    private static String minor;

    @Override
    public void onCreate() {

        MemberID = android.provider.Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        Log.d(TAG, "onCreate");

        // ビーコン受信用のクラスを利用する
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

    }

    // (iBeaconに限らず)BLE機器をスキャンした際のコールバック
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            // 受信できた端末の情報をログ出力
            Log.d(TAG, "receive");
            boolean flag = getScanData(scanRecord); // iBeacon端末に絞込み、ログを出力する（flagはiBeaconか否か）

            // iBeacon端末なら、詳細をログに出力する
            if (flag == true) {
                Log.d(TAG, "UUID: " + beaconId);
                Log.d(TAG, "Major: " + major);
                Log.d(TAG, "Minor: " + minor);
                Log.d(TAG, "device name: " + device.getName()); // デバイス名 (null)
                Log.d(TAG, "device address: " + device.getAddress()); // MAC Address
                Log.d(TAG, "Device Strength; " + Integer.toString(rssi)); // 電波強度

                // データベースに登録
                DatabaseManager.updateMember(MemberID, beaconId, rssi);
            }
        }
    };

    // スキャンしたデータからiBeacon端末であるかどうかを絞り込み、iBeaconであればログを出力する
    private boolean getScanData (byte[] scanRecord) {
        if (scanRecord.length > 30) {
            if ( (scanRecord[5] == (byte)0x4c ) && (scanRecord[6] == (byte)0x00) &&
                    (scanRecord[7] == (byte)0x02) && (scanRecord[8] == (byte)0x15)) {
                // 受信したUUID, Major, Minorの型変換
                beaconId = Integer.toHexString(scanRecord[9] & 0xff)
                        +  Integer.toHexString(scanRecord[10] & 0xff)
                        +  Integer.toHexString(scanRecord[11] & 0xff)
                        +  Integer.toHexString(scanRecord[12] & 0xff)
                        +  Integer.toHexString(scanRecord[13] & 0xff)
                        +  Integer.toHexString(scanRecord[14] & 0xff)
                        +  Integer.toHexString(scanRecord[15] & 0xff)
                        +  Integer.toHexString(scanRecord[16] & 0xff)
                        +  Integer.toHexString(scanRecord[17] & 0xff)
                        +  Integer.toHexString(scanRecord[18] & 0xff)
                        +  Integer.toHexString(scanRecord[19] & 0xff)
                        +  Integer.toHexString(scanRecord[20] & 0xff)
                        +  Integer.toHexString(scanRecord[21] & 0xff)
                        +  Integer.toHexString(scanRecord[22] & 0xff)
                        +  Integer.toHexString(scanRecord[23] & 0xff)
                        +  Integer.toHexString(scanRecord[24] & 0xff);
                major = Integer.toHexString(scanRecord[25] & 0xff)
                        + Integer.toHexString(scanRecord[26] & 0xff);
                minor = Integer.toHexString(scanRecord[27] & 0xff)
                        + Integer.toHexString(scanRecord[28] & 0xff);

                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        runnable = new Runnable() {
            @Override
            public void run() {

                // 繰り返し処理を書く
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                // 終了 -> 開始をしないとなぜか更新されない
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                // 次回処理をセットする
                mHandler.postDelayed(runnable, REPEAT_INTERVAL);
            }
        };

        // 初回実行を書く（再帰処理となる）
        mHandler.postDelayed(runnable, REPEAT_INTERVAL);

        return START_STICKY;

        //return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(runnable); // 終了時にコールバック削除

        mBluetoothAdapter.stopLeScan(mLeScanCallback); // スキャン終了
    }

    @Nullable
    @Override
    public IBinder onBind(Intent arg0){
        return null;
    }
}