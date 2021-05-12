package arkadiusz.krupinski.automotive3;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rx3.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleDeviceServices;

import java.util.Arrays;
import java.util.UUID;

import arkadiusz.krupinski.automotive3.Util.EngineValuesPWM;
import arkadiusz.krupinski.automotive3.Util.HexString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.github.controlwear.virtual.joystick.android.JoystickView;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = "DeviceActivity";

    public static final String EXTRA_MAC_ADDRESS = "extra_mac_address";
    private String macAddress;

    public static final String UUID_SERVICE_DEVICE_NAME = "00001800-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_DEVICE_NAME = "00002A00-0000-1000-8000-00805F9B34FB";
    public static final String UUID_SERVICE = "00001234-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_WRITE = "00001235-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_NOTIFY = "00001236-0000-1000-8000-00805F9B34FB";

    private UUID serviceDeviceNameUUID;
    private UUID characteristicUuidDeviceName;
    private UUID serviceUUID;
    private UUID characteristicUuidWrite;
    private UUID characteristicUuidNotify;

    private PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    private Disposable stateDisposable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private RxBleConnection rxBleConnection;

    @BindView(R.id.connectButton)
    Button connectButton;

    @BindView(R.id.writeButton)
    Button writeButton;

    @BindView(R.id.deviceStatus)
    TextView deviceStatus;

    @BindView(R.id.joystick)
    JoystickView joystickView;

    @OnClick(R.id.connectButton)
    public void onConnectButtonClick() {
        if (isConnected()) {
            triggerDisconnect();
        } else {

            Disposable subscribe = connectionObservable.flatMapSingle(rxBleConnection -> {
                this.rxBleConnection = rxBleConnection;
                return rxBleConnection.writeCharacteristic(characteristicUuidWrite, new byte[]{0x01, 0x01, 0x01});
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ssid3Bytes -> {
//                        System.out.print("This is working");
                        //do something
                        Snackbar.make(findViewById(android.R.id.content), "Connection established!", Snackbar.LENGTH_SHORT).show();
                    }, this::onWriteFailure, this::onWriteSuccess);

            compositeDisposable.add(subscribe);
        }
    }


    @OnClick(R.id.writeButton)
    public void onWriteButtonClick() {

        if (isConnected()) {
//            triggerDisconnect();
//            writeToDevice(new byte[]{0x02, 0x01, 0x01});
            int percent = -15;
            byte u2 = 0;
            if (percent >= 0) {
                u2 = (byte) percent;
            } else {
                u2 = (byte) Math.abs(percent);
                u2 = (byte) ~u2;
                u2 += 1;
            }

            // czyli 30% lewy w druga strone oraz 30% prawy
            writeToDevice(new byte[]{0x03, u2, u2});
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        macAddress = getIntent().getStringExtra(DeviceActivity.EXTRA_MAC_ADDRESS);

        ButterKnife.bind(this);

        serviceDeviceNameUUID = UUID.fromString(UUID_SERVICE_DEVICE_NAME);
        characteristicUuidDeviceName = UUID.fromString(UUID_CHARACTERISTIC_DEVICE_NAME);

        serviceUUID = UUID.fromString(UUID_SERVICE);
        characteristicUuidWrite = UUID.fromString(UUID_CHARACTERISTIC_WRITE);
        characteristicUuidNotify = UUID.fromString(UUID_CHARACTERISTIC_NOTIFY);
        bleDevice = SampleApplication.getRxBleClient(this).getBleDevice(macAddress);

        // share connection I guess
        connectionObservable = bleDevice.establishConnection(true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .takeUntil(disconnectTriggerSubject)
                .share();
//                .compose(ReplayingShare.instance());

        // observe connection status
        stateDisposable = bleDevice.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onConnectionStateChange);

        compositeDisposable.add(stateDisposable);

        // joystick

        joystickView.setOnMoveListener((angle, strength) -> {
            double nJoyX = strength * cos(angle * Math.PI / 180);
            double nJoyY = strength * sin(angle * Math.PI / 180);
            EngineValuesPWM engineValuesPWM = new EngineValuesPWM(nJoyX, nJoyY);
            engineValuesPWM.calulcate();

//            byte left = engineValuesPWM.getLeft();
//            byte right = engineValuesPWM.getRight();

            byte left = engineValuesPWM.getLeftU2();
            byte right = engineValuesPWM.getRightU2();

            writeToDevice(new byte[]{0x03, left, right});
        }, 20);

    }

    private void writeToDevice(byte[] bytes) {
        if (isConnected()) {

            compositeDisposable.add(rxBleConnection.writeCharacteristic(characteristicUuidWrite, bytes)
                    .observeOn(Schedulers.io())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(written -> {
                        Log.i(TAG, "bytes written");
                        Log.i(TAG, "sent bytes: " + HexString.bytesToHex(written));
                    }/* do sth with bytes */, this::onWriteFailure));
        }
    }

    private void onConnectionStateChange(RxBleConnection.RxBleConnectionState newState) {
        deviceStatus.setText(newState.toString());
        updateUI();
    }

    private boolean isConnected() {
        return bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void updateUI() {
        final boolean connected = isConnected();
        connectButton.setText(connected ? getString(R.string.disconnected) : getString(R.string.connect));
//        autoConnectToggleSwitch.setEnabled(!connected);
    }

    private void onConnectionFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Connection error: " + throwable, Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "ERROR", throwable);
    }

    @SuppressWarnings("unused")
    private void onConnectionReceived(RxBleConnection connection) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Connection received", Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Connection received " + connection.toString());
    }

    private void onWriteSuccess() {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Write success", Snackbar.LENGTH_SHORT).show();
        Log.i(TAG, "Write success");
    }

    private void onWriteFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Write error: " + throwable, Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Write error", throwable);
    }

    private void onScreenLogWrite(String s) {
        Snackbar.make(findViewById(android.R.id.content), "Screen write: " + s, Snackbar.LENGTH_SHORT).show();
    }

    private void onConnectionFinished() {
        connectButton.setText(R.string.finished);
    }

    private void dispose() {
//        connectionDisposable = null;
        updateUI();
    }

    private void triggerDisconnect() {
        disconnectTriggerSubject.onNext(true);
        compositeDisposable.clear();

        connectButton.setText(R.string.connect);
        Snackbar.make(findViewById(android.R.id.content), "Disconnection triggered", Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Disconnection triggered");
    }
}