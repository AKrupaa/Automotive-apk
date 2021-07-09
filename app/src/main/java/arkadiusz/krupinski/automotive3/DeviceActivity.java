package arkadiusz.krupinski.automotive3;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rx3.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleDeviceServices;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import arkadiusz.krupinski.automotive3.Util.CSVWriterManager;
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

public class DeviceActivity extends AppCompatActivity implements Player.EventListener {
    private static final String TAG = "DeviceActivity";

    public static final String LOG_FILENAME = "logs";

    public static final String EXTRA_MAC_ADDRESS = "extra_mac_address";
    private String macAddress;

    public static final String UUID_SERVICE_DEVICE_NAME = "00001800-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_DEVICE_NAME = "00002A00-0000-1000-8000-00805F9B34FB";
    public static final String UUID_SERVICE = "00001234-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_WRITE = "00001235-0000-1000-8000-00805F9B34FB";
    public static final String UUID_CHARACTERISTIC_NOTIFY = "00001236-0000-1000-8000-00805F9B34FB";

    public static final byte BLE_RECEIVED_DO_NOTHING = 0x01;
    public static final byte BLE_RECEIVED_AUTO_MANUAL = 0x02;
    public static final byte BLE_RECEIVED_MOVEMENT = 0x03;
    public static final byte BLE_TRANSMIT_TEMPERATURE = 0x04;
    public static final byte BLE_TRANSMIT_X = 0x05;
    public static final byte BLE_TRANSMIT_Y = 0x06;
    public static final byte BLE_TRANSMIT_Z = 0x07;

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

    @BindView(R.id.connectButton)
    Button connectButton;

    @BindView(R.id.writeButton)
    Button writeButton;

    @BindView(R.id.deviceStatus)
    TextView deviceStatus;

    @BindView(R.id.joystick)
    JoystickView joystickView;

    @BindView(R.id.exo_player_view)
    PlayerView playerView;

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

            String[] columns = String.format("%s,%s,%s,%s", "timestamp", "X", "Y", "Z", "Temperature").split(",");
            Date currentTime = Calendar.getInstance().getTime();
            String[] welcome = String.format("%s", "Zapis z dnia: " + currentTime.toString()).split(",");

            strings.add(welcome);
            strings.add(columns);

            csvWriterManager.csvWriterOneByOne(strings);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void initializePlayer() {
        // Global settings.
//        player =
//                new SimpleExoPlayer.Builder(this)
//                        .setMediaSourceFactory(
//                                new DefaultMediaSourceFactory(this)
//                                //.setLiveTargetOffsetMs(5000)
//                        )
//                        .build();
//
//        playerView.setPlayer(player);
////
        Uri uri = Uri.parse("rtsp://192.168.1.1:8554/mjpeg/1");
////
////         Per MediaItem settings.
//        MediaItem mediaItem =
//                new MediaItem.Builder()
//                        .setUri(uri)
//                        .setLiveMaxPlaybackSpeed(1.03f)
//                        .setLiveMaxOffsetMs(10)
//                        .build();
//        player.setMediaItem(mediaItem);


        // Create an RTSP media source pointing to an RTSP uri.
        MediaSource mediaSource =
                new RtspMediaSource.Factory()
                        .createMediaSource(MediaItem.fromUri(uri));
// Create a player instance.
        player = new SimpleExoPlayer.Builder(this).build();
// Set the media source to be played.
        player.setMediaSource(mediaSource);
// Prepare the player.
        player.prepare();

        playerView.setPlayer(player);
        // --------------------------------------------------------

        // Create a data source factory.
//        DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory();
//// Create a progressive media source pointing to a stream uri.
//        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(MediaItem.fromUri(uri));
//// Create a player instance.
//        SimpleExoPlayer player = new SimpleExoPlayer.Builder(this).build();
//// Set the media source to be played.
//        player.setMediaSource(mediaSource);
//// Prepare the player.
//        player.prepare();
//        playerView.setPlayer(player);
//        player.setPlayWhenReady(true);
//        Uri myUri = Uri.parse("http://192.168.4.1/"); // initialize Uri here
//        String url = "http://192.168.4.1/"; // your URL here
//        MediaPlayer mediaPlayer = new MediaPlayer();
//        // ... other initialization here ...
//        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
//
//
//        WifiManager.WifiLock wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
//                .createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");
//
//        wifiLock.acquire();
//        mediaPlayer.setAudioAttributes(
//                new AudioAttributes.Builder()
//                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .setUsage(AudioAttributes.USAGE_MEDIA)
//                        .build()
//        );
//        try {
//            mediaPlayer.setDataSource(url);
//            mediaPlayer.setOnPreparedListener(this::onPrepared);
//            mediaPlayer.prepareAsync();
////            mediaPlayer.prepare(); // might take long! (for buffering, etc)
//        } catch (IOException ioException) {
//            ioException.printStackTrace();
//        }
//        mediaPlayer.start();

//        VideoView videoView = (VideoView)findViewById(R.id.video_view);
//        MediaController mediaController= new MediaController(this);
//        mediaController.setAnchorView(videoView);
//        Uri uri = Uri.parse("http://home/video.mp4");
//        videoView.setMediaController(mediaController);
//        videoView.setVideoURI(uri);
//        videoView.requestFocus();
//        videoView.start();

    }

//    public void onPrepared(MediaPlayer player) {
//        player.start();
//    }

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

    private void onNotificationSet(String text) {
        Snackbar.make(findViewById(android.R.id.content), "Notification set: " + text, Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Notification has been set:" + text);
    }

    private void onNotificationChange(byte[] bytes) {
        Snackbar.make(findViewById(android.R.id.content), "Notification change: " + HexString.bytesToHex(bytes), Snackbar.LENGTH_SHORT).show();
        Log.e(TAG, "Given characteristic has been changes, here is the value." + HexString.bytesToHex(bytes));

        //TODO: przetestować reagowanie na przysylane dane...


        // WRITE TO CSV file
        try {
            csvWriterManager.createFile(LOG_FILENAME);
            Date date = new Date(System.currentTimeMillis());

//            String[] columns = String.format("%s,%s,%s,%s", "timestamp", "X", "Y", "Z", "Temperature").split(",");

            Integer X = null, Y = null, Z = null, temperature = null;

            byte[] data = {bytes[1], bytes[2]};
            ByteBuffer wrapped;
            boolean isNegative = false;

            switch (bytes[0]) {
                case BLE_TRANSMIT_TEMPERATURE:

                    // 12bit ADC
                    if (((bytes[1] & 0xFF) & (1 << 4)) == (1 << 4)) {
                        isNegative = true;
                    }

                    wrapped = ByteBuffer.wrap(data);
                    temperature = wrapped.getInt();
                    temperature /= 2 ^ 12 - 1; // resolution 12 bit ADC (STM32L162RDTX)

                    if (isNegative)
                        temperature -= 2 ^ 12 - 1; // this is used for coverting U2 to INT (not tested yet)

                    break;
                case BLE_TRANSMIT_X:

                    wrapped = ByteBuffer.wrap(data);
                    X = wrapped.getInt();

                    break;
                case BLE_TRANSMIT_Y:

                    wrapped = ByteBuffer.wrap(data);
                    Y = wrapped.getInt();

                    break;
                case BLE_TRANSMIT_Z:

                    wrapped = ByteBuffer.wrap(data);
                    Z = wrapped.getInt();
                    break;
            }


            /*
            byte[] arr = { 0x00, 0x01 };
            ByteBuffer wrapped = ByteBuffer.wrap(arr); // big-endian by default
            short num = wrapped.getShort(); // 1

            ByteBuffer dbuf = ByteBuffer.allocate(2);
            dbuf.putShort(num);
            byte[] bytes = dbuf.array(); // { 0, 1 }
             */

            String[] entry = String.format(Locale.getDefault(), "%s,%d,%d,%d,%d", date.toString(), X, Y, Z, temperature).split(",");

            csvWriterManager.csvWriterOnce(entry);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }


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
}