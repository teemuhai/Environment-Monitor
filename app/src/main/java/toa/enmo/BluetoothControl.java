package toa.enmo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.Bmp280Barometer;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Ltr329AmbientLight;
import com.mbientlab.metawear.module.MultiChannelTemperature;

import java.math.BigDecimal;
import java.util.List;

import static com.mbientlab.metawear.MetaWearBoard.ConnectionStateHandler;

public class BluetoothControl implements ServiceConnection {
    private Context activityContext;
    private MetaFragment mFrag;
    ConnectFragment cFrag;
    private MetaWearBleService.LocalBinder serviceBinder;
    MetaWearBoard mwBoard;
    BluetoothAdapter BA;
    Led ledModule = null;

    String temperature;
    String pressure;
    String light;
    String acceleration;

    float accValue;
    float pressValue;
    float lightValue;
    float tempValue;

    public BluetoothControl(Context c, MetaFragment mf, ConnectFragment cf) {
        activityContext = c;
        mFrag = mf;
        cFrag = cf;

        // Assign the bluetooth adapter and register the broadcast receiver
        BA = BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        activityContext.registerReceiver(mReceiver, filter);
    }

    /**
     * Method for making a toast message
     */
    public void toaster(final String s) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(activityContext, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void refreshMenu() {
        MainActivity mActivity = (MainActivity)activityContext;
        mActivity.invalidateOptionsMenu();
    }

    public void createConnection() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                System.out.println("thread running");
                    connectBoard();
                }

        });
        thread.start();
    }

    /**
     * Connect, disconnect, and retrieving functins for the MetaWear
     */
    public void connectBoard() {
        mwBoard.connect();
    }

    public void disconnectBoard() {
        mwBoard.disconnect();
    }

    public void retrieveBoard(String address) {
        final BluetoothManager btManager =
                (BluetoothManager) activityContext.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice =
                btManager.getAdapter().getRemoteDevice(address);

        System.out.println("Retrieving board\n " + "remotedevice: " +remoteDevice);

        if (remoteDevice != null) {
            // Create a MetaWear board object for the Bluetooth Device
            System.out.println("Remotedevice is alive");
            mwBoard = serviceBinder.getMetaWearBoard(remoteDevice);
            mwBoard.setConnectionStateHandler(stateHandler);
        } else {
            System.out.println("This is null");
        }
    }

    /**
     * Start the external sensors' data streams
     */
    public void activateSensors() {
        acceleration();
        pressure();
        light();
        // Create a thread to update the temperature at all times
        Thread tempThread = new Thread() {
            public void run() {
                while (cFrag.isDeviceConnected) {
                    try {
                        sleep(1000);
                        temperature();
                    } catch (Exception e) {
                        Log.d("Error", "tempthread:" + e);
                    }
                }
            }
        };
        tempThread.start();
    }

    /**
     * State handler for handling the MetaWear's connection
     */
    private final ConnectionStateHandler stateHandler = new ConnectionStateHandler() {
        @Override
        public void connected() {
            Log.i("MainActivity", "Connected");
            toaster("Connected \uD83C\uDF1A");
            cFrag.isDeviceConnected = true;
            cFrag.connectedDevice = cFrag.bluetoothDevices.get(cFrag.connectedDeviceIndex);
            ledColor();
            cFrag.connectDialog.dismiss();
            activateSensors();
            refreshMenu();
        }

        @Override
        public void disconnected() {
            Log.i("MainActivity", "Disconnected");
            toaster("Disconnected \uD83C\uDF1A");
            cFrag.isDeviceConnected = false;
            cFrag.connectedDevice = null;
            refreshMenu();
        }

        @Override
        public void failure(int status, Throwable error) {
            Log.e("MainActivity", "Error connecting", error);
            toaster("Connecting error, please try again.");
            cFrag.isDeviceConnected = false;
            cFrag.connectedDevice = null;
            cFrag.connectDialog.dismiss();
            refreshMenu();
        }
    };

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (MetaWearBleService.LocalBinder) iBinder;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

     BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Check that the name contains words that relate to the sensor
                String tempString;
                tempString = device.getName();
                if (tempString != null){
                    tempString = tempString.toLowerCase();

                    if (tempString.contains("wear") || tempString.contains("meta")){
                        cFrag.theList.add(device.getName());

                        // Add the address to an array to use when trying to pair
                        try {
                            cFrag.bluetoothDevices.add(device);

                        } catch (Exception e) {
                            System.out.println("what: " + e);
                        }
                    }
                }

                System.out.println(device.getName() + "        " + device.getAddress());
                // Update the array
                if (cFrag.isVisible()) {
                    ((ArrayAdapter) cFrag.lv.getAdapter()).notifyDataSetChanged();
                }
            }

        }
    };

    /**
     * Method for chaning the MetaWear's LED color
     */
    public void ledColor() {
        try {
            ledModule = mwBoard.getModule(Led.class);
        } catch (UnsupportedModuleException e) { }

        if (ledModule != null){
            ledModule.configureColorChannel(Led.ColorChannel.GREEN)
                    .setHighIntensity((byte) 31).setLowIntensity((byte) 31)
                    .setHighTime((short) 1000).setPulseDuration((short) 1000)
                    .setRepeatCount((byte) -1)
                    .commit();
            ledModule.play(false);
        }
    }


    /**
     * External Sensor methods
     */

    public void acceleration() {
        try {
            final Bmi160Accelerometer accModule = mwBoard.getModule(Bmi160Accelerometer.class);

            // Set measurement range to +/- 16G
            // Set output data rate to 100Hz
            accModule.configureAxisSampling()
                    .setFullScaleRange(Bmi160Accelerometer.AccRange.AR_16G)
                    .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_100_HZ)
                    .commit();
            // enable axis sampling
            accModule.enableAxisSampling();

            accModule.routeData().fromHighFreqAxes().stream("high_freq").commit()
                    .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                        @Override
                        public void success(RouteManager result) {
                            result.subscribe("high_freq", new RouteManager.MessageHandler() {
                                @Override
                                public void process(Message msg) {
                                    acceleration = (msg.getData(CartesianFloat.class).toString());
                                    String[] accA = acceleration.split(",");
                                    accValue = Float.parseFloat(accA[1]);
                                    System.out.println("accvalue: " + accValue);
                                    if (mFrag.isVisible()) {
                                        mFrag.sensorMsg(acceleration, "acc");
                                    }
                                }
                            });

                            accModule.setOutputDataRate(200.f);
                            accModule.enableAxisSampling();
                            accModule.start();
                        }
                    });

        } catch (UnsupportedModuleException e) {
            Log.e("MainActivity", "Module not present", e);
        }

    }

    public void temperature() {
        try {
            final MultiChannelTemperature mcTempModule = mwBoard.getModule(MultiChannelTemperature.class);
            final List<MultiChannelTemperature.Source> tempSources = mcTempModule.getSources();

            mcTempModule.routeData()
                    .fromSource(tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE)).stream("temp_nrf_stream")
                    .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("temp_nrf_stream", new RouteManager.MessageHandler() {
                        @Override
                        public void process(Message msg) {
                            temperature = (msg.getData(Float.class).toString() + " °C");
                            tempValue = msg.getData(Float.class);
                            System.out.println("ext temp: " + tempValue);
                            if (mFrag.isVisible()) {
                                mFrag.sensorMsg(temperature, "temp");
                            }
                        }
                    });

                    // Read temperature from the NRF soc chip
                    mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE));

                }
            });

        } catch (UnsupportedModuleException e) {
            Log.e("MainActivity", "Module not present", e);
        }

    }

    public void pressure() {
        try {

            final Bmp280Barometer bmp280Module = mwBoard.getModule(Bmp280Barometer.class);

            bmp280Module.configure()
                    .setFilterMode(Bmp280Barometer.FilterMode.AVG_4)
                    .setPressureOversampling(Bmp280Barometer.OversamplingMode.LOW_POWER)
                    .setStandbyTime(Bmp280Barometer.StandbyTime.TIME_125)
                    .commit();

            bmp280Module.routeData().fromPressure().stream("pressure_stream").commit()
                    .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                        @Override
                        public void success(RouteManager result) {
                            result.subscribe("pressure_stream", new RouteManager.MessageHandler() {
                                @Override
                                public void process(Message msg) {
                                    float press = msg.getData(Float.class);
                                    press = press / 100;
                                    pressure = (round(press, 2) + " mBar");
                                    pressValue = (round(press, 2));
                                    if (mFrag.isVisible()) {
                                        mFrag.sensorMsg(pressure, "pres");
                                    }
                                }
                            });
                            bmp280Module.start();
                        }
                    });
        } catch (UnsupportedModuleException e) {
            Log.e("MainActivity", "Module not present", e);
        }
    }

    public void light() {
        try {
            final Ltr329AmbientLight ltr329Module = mwBoard.getModule(Ltr329AmbientLight.class);

            ltr329Module.configure().setGain(Ltr329AmbientLight.Gain.LTR329_GAIN_4X)
                    .setIntegrationTime(Ltr329AmbientLight.IntegrationTime.LTR329_TIME_150MS)
                    .setMeasurementRate(Ltr329AmbientLight.MeasurementRate.LTR329_RATE_100MS)
                    .commit();

            ltr329Module.routeData().fromSensor().stream("light_sub").commit()
                    .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                        @Override
                        public void success(RouteManager result) {
                            result.subscribe("light_sub", new RouteManager.MessageHandler() {
                                @Override
                                public void process(Message msg) {
                                    float lux = msg.getData(Long.class);
                                    lux = lux / 1000;
                                    light = (round(lux, 2) + " lx");
                                    lightValue = round(lux, 2);
                                    if (mFrag.isVisible()) {
                                        mFrag.sensorMsg(light, "light");
                                    }

                                }
                            });
                            ltr329Module.start();
                        }
                    });
        } catch (UnsupportedModuleException e) {
            Log.e("MainActivity", "Module not present", e);
        }
    }

    /**
     * Method for rounding up external sensor data to
     * make it look better on screen
     */
    public static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

}
