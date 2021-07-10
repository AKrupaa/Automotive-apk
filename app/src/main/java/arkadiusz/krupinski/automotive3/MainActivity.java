package arkadiusz.krupinski.automotive3;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import arkadiusz.krupinski.automotive3.Adapters.ScanResultsAdapter;
import arkadiusz.krupinski.automotive3.Util.LocationPermission;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.scan_toggle_btn)
    Button scanToggleButton;
    //    @BindView(R.id.scan_results)
    @BindView(R.id.scan_results)
    RecyclerView recyclerView;
    private RxBleClient rxBleClient;
    private Disposable scanDisposable;
    private ScanResultsAdapter resultsAdapter;
    private boolean hasClickedScan;

    private static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        //        Turn on Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        int REQUEST_ENABLE_BT = 1;
        this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        // Use this check to determine whether SampleApplication is supported on the device. Then
        // you can selectively disable SampleApplication-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
//            finish();
        } else {
//            good to know ;)
            Toast.makeText(this, R.string.ble_supported, Toast.LENGTH_SHORT).show();
        }

        rxBleClient = SampleApplication.getRxBleClient(this);
        configureResultList();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        } else {
            checkPermission();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermission();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isScanning()) {
            /*
             * Stop scanning in onPause callback.
             */
            scanDisposable.dispose();
        }
    }

    @OnClick(R.id.scan_toggle_btn)
    public void onScanToggleClick() {

        if (isScanning()) {
            scanDisposable.dispose();
        } else {
            if (rxBleClient.isScanRuntimePermissionGranted()) {
                scanBleDevices();
            } else {
                hasClickedScan = true;
                LocationPermission.requestLocationPermission(this, rxBleClient);
            }
        }

        updateButtonUIState();
    }

    private boolean isScanning() {
        return scanDisposable != null;
    }

    private void scanBleDevices() {
        scanDisposable = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                new ScanFilter.Builder()
//                            .setDeviceAddress("B4:99:4C:34:DC:8B")
                        // add custom filters if needed
                        .build()
        )
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> dispose())
                .subscribe(bleScanResult -> resultsAdapter.addScanResult(bleScanResult),
                        throwable -> onScanFailure(throwable));
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
            }
        }
    }

    private void configureResultList() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerLayoutManager);
        resultsAdapter = new ScanResultsAdapter();
        recyclerView.setAdapter(resultsAdapter);
        resultsAdapter.setClickListener(this::onAdapterItemClick);
    }

    private void onAdapterItemClick(View view, int position, ScanResult scanResult) {
        final String macAddress = scanResult.getBleDevice().getMacAddress();
        Log.i(TAG, "MAC: " + macAddress);
        final Intent intent = new Intent(this, DeviceActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_MAC_ADDRESS, macAddress);
        startActivity(intent);
        finish();
    }

    private void dispose() {
        scanDisposable = null;
        resultsAdapter.clearScanResults();
        updateButtonUIState();
    }

    private void updateButtonUIState() {
        scanToggleButton.setText(isScanning() ? getString(R.string.stop_scan) : getString(R.string.start_scan));
    }

    private void onScanFailure(Throwable throwable) {
        Log.e(TAG, "error", throwable);
//        if (throwable instanceof BleScanException) {
//            ScanExceptionHandler.handleException(this, (BleScanException) throwable);
//        }
    }
}