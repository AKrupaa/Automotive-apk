package arkadiusz.krupinski.automotive3;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.snackbar.Snackbar;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import arkadiusz.krupinski.automotive3.Util.CSVWriterManager;
import arkadiusz.krupinski.automotive3.Util.EngineValuesPWM;
import arkadiusz.krupinski.automotive3.Util.HexString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.github.controlwear.virtual.joystick.android.JoystickView;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class DeviceActivity extends AppCompatActivity implements Player.EventListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DeviceActivity";

    public static final String LOG_FILENAME = "logs";

    public static final String EXTRA_MAC_ADDRESS = "extra_mac_address";
    private String macAddress;

    public static final String UUID_SERVICE_DEVICE_NAME = "00001800-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_DEVICE_NAME = "00002A00-0000-1000-8000-00805F9B34FB";
    public static final String UUID_SERVICE = "00001234-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_WRITE = "00001235-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_NOTIFY = "00001236-0000-1000-8000-00805F9B34FB";


    public static final double QMC5883L_SCALE_FACTOR = 0.732421875f;
    public static final byte BLE_TRANSMIT_DO_NOTHING = 0x01;
    public static final byte BLE_TRANSMIT_AUTO_MANUAL = 0x02;
    public static final byte BLE_TRANSMIT_MOVEMENT = 0x03;
    public static final byte BLE_RECEIVED_TEMPERATURE = 0x04;
    public static final byte BLE_RECEIVED_X = 0x05;
    public static final byte BLE_RECEIVED_Y = 0x06;
    public static final byte BLE_RECEIVED_Z = 0x07;
    public static final byte BLE_RECEIVED_ULTRASOUND = 0x08;
    public static final byte BLE_ULTRASOUND_CONFIG = 0x09;
    public static final byte BLE_TRANSMIT_ULTRASOUND_VALUE = 0x0A;

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

    private CSVWriterManager csvWriterManager;

    SimpleExoPlayer player;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.connectButton)
    Button connectButton;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.writeButton)
    Button writeButton;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.deviceStatus)
    TextView deviceStatus;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.joystick)
    JoystickView joystickView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.exo_player_view)
    PlayerView playerView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.spinner)
    Spinner distancesSpinner;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.distance_ultrasound)
    TextView distanceTextView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.textViewX)
    TextView xMagnetometerTextView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.textViewY)
    TextView yMagnetometerTextView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.textViewZ)
    TextView zMagnetometerTextView;

    @SuppressLint("NonConstantResourceId")
    @BindView(R.id.textViewHeading)
    TextView headingTextView;

    @SuppressLint("NonConstantResourceId")
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
                        connectButton.setText(R.string.disconnected);
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
//            writeToDevice(new byte[]{0x03, u2, u2});

            Disposable subscribe1 = rxBleConnection.setupNotification(UUID.fromString(UUID_CHARACTERISTIC_NOTIFY))
                    .doOnNext(observable -> {
                        // notification has been set up
                        onNotificationSet("It works!");
                    })
                    .flatMap(observable -> observable)
                    .subscribe(
                            bytes -> {
                                // given characteristic has been changes, here is the value
                                onNotificationChange(bytes);
                            },
                            throwable -> {
                                onNotificationFailure(throwable);
                            }
                    );

            compositeDisposable.add(subscribe1);
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


        // player
        initializePlayer();


        // write to CSV new sequence with timestamp
        csvWriterManager = new CSVWriterManager(this);

        try {
            csvWriterManager.createFile(LOG_FILENAME);

            List<String[]> strings = new ArrayList<>(0);
            //String[] entries = String.format("%d,%s,%s,%d", scan.getTimestampNanos(), scan.getBleDevice().getMacAddress(), scan.getBleDevice().getName(), scan.getRssi()).split(",");

            String[] columns = String.format("%s,%s,%s,%s", "timestamp", "X", "Y", "Z", "distance to obstacle").split(",");
            Date currentTime = Calendar.getInstance().getTime();
            String[] welcome = String.format("%s", "Record of: " + currentTime.toString()).split(",");

            strings.add(welcome);
            strings.add(columns);

            csvWriterManager.csvWriterOneByOne(strings);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // create spinner
        distancesSpinner.setOnItemSelectedListener(this);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.ultrasound_distances, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        distancesSpinner.setAdapter(adapter);
        distancesSpinner.setSelection(7); // (id == 7) { // 40 cm
    }


    public void initializePlayer() {

        Uri uri = Uri.parse("rtsp://192.168.1.1:8554/mjpeg/1");
        // Create a player instance.
        player = new SimpleExoPlayer.Builder(this).build();
////         Per MediaItem settings.
        MediaItem mediaItem =
                new MediaItem.Builder()
                        .setUri(uri)
                        .setLiveMaxPlaybackSpeed(1.03f)
                        .setLiveMaxOffsetMs(1000)
                        .build();
        player.setMediaItem(mediaItem);


        // Create an RTSP media source pointing to an RTSP uri.
//        MediaSource mediaSource =
//                new RtspMediaSource.Factory()
//                        .createMediaSource(MediaItem.fromUri(uri));

// Set the media source to be played.
//        player.setMediaSource(mediaSource);
// Prepare the player.
        player.prepare();

        playerView.setPlayer(player);
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        if (isBehindLiveWindow(e)) {
            // Re-initialize player at the current live window default position.
            player.seekToDefaultPosition();
            player.prepare();
            Log.e(TAG, "onPlayerError");
        } else {
            // Handle other errors.
            Log.e(TAG, "onPlayerError (else)");
        }
    }

    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
        Log.e(TAG, "ERROR ", throwable);
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
        Log.e(TAG, "Write error ", throwable);
    }

    private void onNotificationSet(String text) {
        Snackbar.make(findViewById(android.R.id.content), "Notification set: " + text, Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Notification has been set: " + text);
    }

    private void onNotificationChange(byte[] bytes) {
        Snackbar.make(findViewById(android.R.id.content), "Notification change: " + HexString.bytesToHex(bytes), Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Given characteristic has been changes, here is the value: " + HexString.bytesToHex(bytes));

        double X = 0;
        double Y = 0;
        double Z = 0;
        double ultrasound_dist = 0;

        if (bytes.length % 3 != 0)
            return;

        ArrayList<byte[]> arrayList = new ArrayList<>(0);

        for (int i = 0; i < bytes.length; i += 3) {
            byte[] resolving = {bytes[i], bytes[i + 1], bytes[i + 2]};
            arrayList.add(resolving);
        }

        for (byte[] array :
                arrayList) {
            byte[] data;
            String bytesToHex;
            int decimal;
            if (array.length == 3) {
                data = new byte[]{array[2], array[1]};
                bytesToHex = HexString.bytesToHex(data);
            } else
                return;


            if (array[0] == BLE_TRANSMIT_ULTRASOUND_VALUE) {
                decimal = Integer.parseInt(bytesToHex, 16);
//            ((ULTRASOUND_DIST_15CM) / TIMER_10_RESOLUTION * 2)
                double result = (decimal * 0.125 * 0.0344 / 2); // timer result * 2 * timer resolution * speed of sound in cm/us
                ultrasound_dist = result;
                runOnUiThread(() -> distanceTextView.setText(String.format(Locale.getDefault(), "Distance = %.2f cm", result)));
            } else if (array[0] == BLE_RECEIVED_X) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                short result = byteBuffer.getShort();
                X = result / 1.0;
                runOnUiThread(() -> xMagnetometerTextView.setText(String.format(Locale.getDefault(), "%.2f", result / 1.0)));
                Log.i(TAG, "X = " + String.valueOf(result / 1.0));
            } else if (array[0] == BLE_RECEIVED_Y) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                short result = byteBuffer.getShort();
                Y = result / 1.0;
                runOnUiThread(() -> yMagnetometerTextView.setText(String.format(Locale.getDefault(), "%.2f", result / 1.0)));
                Log.i(TAG, "Y = " + String.valueOf(result / 1.0));
            } else if (array[0] == BLE_RECEIVED_Z) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                short result = byteBuffer.getShort();
                Z = result / 1.0;
                runOnUiThread(() -> zMagnetometerTextView.setText(String.format(Locale.getDefault(), "%.2f", result / 1.0)));
                Log.i(TAG, "Z = " + String.valueOf(result / 1.0));
            }
//
            double finalY = Y;
            double finalX = X;
            double finalZ = Z;
            double finalUltrasound_dist = ultrasound_dist;
            runOnUiThread(() -> {
                try {

//                    double heading = Math.atan2(finalY, finalX);

//                    heading += 93.67 / 1000;  // radian, Tekirdag/Turkey
                    //WEST
                    //heading -= 93.67/1000;
                    double heading = Math.atan2(finalY, finalX) * (180 / Math.PI);
//                    if (heading < 0) {
//                        heading += 2 * Math.PI;
//                    } else if (heading > 2 * Math.PI) {
//                        heading -= 2 * Math.PI;
//                    }

//                    double finalHeading = heading;
                    headingTextView.setText(String.format(Locale.getDefault(), "%.4f", heading));
//                    String[] columns = String.format("%s,%s,%s,%s", "timestamp", "X", "Y", "Z", "distance to obstacle").split(",");
                    Date date = new Date(System.currentTimeMillis());
                    String[] entry = String.format(Locale.getDefault(), "%s'%.2f'%.2f'%.2f'%.2f", date.toString(), finalX, finalY, finalZ, finalUltrasound_dist).split("'");
                    csvWriterManager.csvWriterOnce(entry);

                } catch (Exception e) {
                    e.getCause();
                    e.getLocalizedMessage();
                    e.getStackTrace();
                }
            });


        }


//        //TODO: przetestować reagowanie na przysylane dane...
//
//
//        // WRITE TO CSV file
////        try {
////            csvWriterManager.createFile(LOG_FILENAME);
//            Date date = new Date(System.currentTimeMillis());
//
////            String[] columns = String.format("%s,%s,%s,%s", "timestamp", "X", "Y", "Z", "Temperature").split(",");
//
//            Integer X = null, Y = null, Z = null, temperature = null;
//
//            byte[] data = {bytes[1], bytes[2]};
//            ByteBuffer wrapped;
//            boolean isNegative = false;
//
//            switch (bytes[0]) {
//                case BLE_TRANSMIT_TEMPERATURE:
//
//                    // 12bit ADC
//                    if (((bytes[1] & 0xFF) & (1 << 4)) == (1 << 4)) {
//                        isNegative = true;
//                    }
//
//                    wrapped = ByteBuffer.wrap(data);
//                    temperature = wrapped.getInt();
//                    temperature /= 2 ^ 12 - 1; // resolution 12 bit ADC (STM32L162RDTX)
//
//                    if (isNegative)
//                        temperature -= 2 ^ 12 - 1; // this is used for coverting U2 to INT (not tested yet)
//
//                    break;
//                case BLE_TRANSMIT_X:
//
//                    wrapped = ByteBuffer.wrap(data);
//                    X = wrapped.getInt();
//
//                    break;
//                case BLE_TRANSMIT_Y:
//
//                    wrapped = ByteBuffer.wrap(data);
//                    Y = wrapped.getInt();
//
//                    break;
//                case BLE_TRANSMIT_Z:
//
//                    wrapped = ByteBuffer.wrap(data);
//                    Z = wrapped.getInt();
//                    break;
//            }
//
//
//            /*
//            byte[] arr = { 0x00, 0x01 };
//            ByteBuffer wrapped = ByteBuffer.wrap(arr); // big-endian by default
//            short num = wrapped.getShort(); // 1
//
//            ByteBuffer dbuf = ByteBuffer.allocate(2);
//            dbuf.putShort(num);
//            byte[] bytes = dbuf.array(); // { 0, 1 }
//             */
//
//            String[] entry = String.format(Locale.getDefault(), "%s,%d,%d,%d,%d", date.toString(), X, Y, Z, temperature).split(",");
//
////            csvWriterManager.csvWriterOnce(entry);
////        } catch (IOException ioException) {
////            ioException.printStackTrace();
////        } catch (Exception e) {
////            e.printStackTrace();
////        }


    }

    private void onNotificationFailure(Throwable throwable) {
        Snackbar.make(findViewById(android.R.id.content), "Notification error: " + throwable, Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Notification error", throwable);
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

    // FOR SPINNER
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)

        Log.i(TAG, parent.getItemAtPosition(position).toString());
//        Log.i(TAG, String.valueOf(id));

        if (id == 0) { // 5 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x00});
        } else if (id == 1) { // 10 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x01});
        } else if (id == 2) { // 15 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x02});
        } else if (id == 3) { // 20 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x03});
        } else if (id == 4) { // 25 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x04});
        } else if (id == 5) { // 30 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x05});
        } else if (id == 6) { // 35 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x06});
        } else if (id == 7) { // 40 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x07});
        } else if (id == 8) { // 50 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x08});
        } else if (id == 9) { // 60 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x09});
        } else if (id == 10) { // 70 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x0A});
        } else if (id == 11) { // 80 cm
            writeToDevice(new byte[]{BLE_ULTRASOUND_CONFIG, 0x00, 0x0B});
        }


//        writeToDevice(new byte[]{0x03, left, right});
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
}