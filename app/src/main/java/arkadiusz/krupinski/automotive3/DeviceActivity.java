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

import java.util.Arrays;
import java.util.UUID;

import arkadiusz.krupinski.automotive3.Util.HexString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

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
    private Disposable connectionDisposable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @BindView(R.id.connectButton)
    Button connectButton;

    @BindView(R.id.writeButton)
    Button writeButton;

    @BindView(R.id.deviceStatus)
    TextView deviceStatus;

    @OnClick(R.id.connectButton)
    public void onConnectButtonClick() {
        if (isConnected()) {
            triggerDisconnect();
        } else {

            connectionDisposable =// bleDevice.establishConnection(false)
                    connectionObservable
                            .flatMapSingle(RxBleConnection::discoverServices)
                            .flatMapSingle(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(characteristicUuidWrite))
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnSubscribe(disposable -> Snackbar.make(findViewById(android.R.id.content), "Connecting...", Snackbar.LENGTH_SHORT).show())
                            .subscribe(bluetoothGattCharacteristic -> {
                                        Snackbar.make(findViewById(android.R.id.content), "Hey, connection has been established!", Snackbar.LENGTH_SHORT).show();
                                    },
                                    this::onConnectionFailure,
                                    this::onConnectionFinished
                            );

            compositeDisposable.add(connectionDisposable);


            Disposable subscribe = connectionObservable.flatMapSingle(rxBleConnection -> {
                return rxBleConnection.writeCharacteristic(characteristicUuidWrite, "03FFFF".getBytes());
            })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ssid3Bytes -> {
                        System.out.print("HAHAHAHAHAHA");
                        //do something
                    }, this::onWriteFailure, this::onWriteSuccess);

            compositeDisposable.add(subscribe);
            // kopia
//            connectionDisposable =// bleDevice.establishConnection(false)
//                    connectionObservable
//                            .observeOn(AndroidSchedulers.mainThread())
//                            .doFinally(this::dispose)
//                            .subscribe(this::onConnectionReceived, this::onConnectionFailure);
//
//            compositeDisposable.add(connectionDisposable);
            // koniec kopii
        }
    }



    @OnClick(R.id.writeButton)
    public void onWriteClick() {

        if (isConnected()) {
            String l = Integer.toHexString(100);
            String r = Integer.toHexString(100);
            String movement = "03";
            String data = movement + l + r;
            onScreenLogWrite(data);

            // use connectionObservable to write to IoT device
            Disposable disposable = connectionObservable
//                    .flatMapSingle(RxBleConnection::discoverServices)
//                    .firstOrError()
                    .flatMapSingle(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUuidWrite, HexString.hexToBytes(data)))
//                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
//                    .ignoreElement()
                    .subscribe(
//                            this::onWriteSuccess
                            bytes -> onWriteSuccess(),
                            this::onWriteFailure
                    );

            compositeDisposable.add(disposable);


//            Disposable subscribe = connectionObservable.flatMapSingle(rxBleConnection -> {
//                return rxBleConnection.writeCharacteristic(characteristicUuidWrite, "03FFFF" .getBytes());
////                        .flatMap(ssidBytes -> rxBleConnection.writeCharacteristic(SSID2, mPassPhrase.getText().toString().getBytes())
////                                .flatMap(ssid2Bytes -> rxBleConnection.writeCharacteristic(SSID3, mSecurityModeSpinner.getSelectedItem().toString().getBytes())));
//            })
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe(ssid3Bytes -> {
//                        System.out.print("HAHAHAHAHAHA");
//                        //do something
//                    }, this::onWriteFailure, this::onWriteSuccess);
//
//            compositeDisposable.add(subscribe);
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
                .takeUntil(disconnectTriggerSubject)
//                .subscribe()
                .share();
//                .compose(ReplayingShare.instance());

//        connectionObservable
//        connectionDisposable = bleDevice.establishConnection(false)
//                .observeOn(AndroidSchedulers.mainThread())
//                .doFinally(this::dispose)
//                .subscribe(this::onConnectionReceived, this::onConnectionFailure);

//        compositeDisposable.add(connectionDisposable);

        // observe connection status

        stateDisposable = bleDevice.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onConnectionStateChange);

        compositeDisposable.add(stateDisposable);
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
    }

    private void onWriteSuccess() {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Write success", Snackbar.LENGTH_SHORT).show();
    }

    private void onWriteFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Write error: " + throwable, Snackbar.LENGTH_SHORT).show();
    }

    private void onScreenLogWrite(String s) {
        Snackbar.make(findViewById(android.R.id.content), "Screen write: " + s, Snackbar.LENGTH_SHORT).show();
    }

    private void onConnectionFinished() {
        connectButton.setText(R.string.finished);
    }

    private void dispose() {
        connectionDisposable = null;
        updateUI();
    }

    private void triggerDisconnect() {

        if (connectionDisposable != null) {
            connectionDisposable.dispose();
        }
        disconnectTriggerSubject.onNext(true);
        compositeDisposable.clear();

        connectButton.setText(R.string.connect);
    }
}