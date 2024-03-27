package com.example.tfg_bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

//Manejo peticiones HTTP
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

//Solicitudes GET
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

//Solicitudes POST
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

//Encriptar dirección MAC
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final long INTERVALO_DE_TIEMPO = 2*60*1000;

    private Handler handler;
    private BluetoothAdapter bluetoothAdapter;

    // Para mostrar datos en la interfaz como lista
    private ArrayAdapter<String> devicesArrayAdapter;
    //Contiene nombre y direccion MAC (se puede cambiar por un ArrayList para guardar solo la MAC)
    private Map<String, String> devicesMap;

    private List<String> devicesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicesMap = new HashMap<>();
        // Convertir los valores del HashMap a una lista
        devicesList = new ArrayList<>(devicesMap.values());

        // Crear el ArrayAdapter con la lista
        //* Cambiar a android.R.layout.simple_list_item_1
        devicesArrayAdapter = new ArrayAdapter<>(this, R.layout.list_item_device);


        ListView devicesListView = findViewById(R.id.devicesListView);
        devicesListView.setAdapter(devicesArrayAdapter);

        // Verificar si el dispositivo soporta Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "El dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Solicitar permisos de ubicación si es necesario (para dispositivos con Android 6.0 y superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Verificar si Bluetooth está habilitado
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // Después de la inicialización de devicesListView en onCreate
        Button searchButton = findViewById(R.id.searchButton);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Manejar el clic del botón para iniciar la búsqueda nuevamente
                startDiscovery();
            }
        });

        handler = new Handler();
    }


    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDiscovery();
            } else {
                Toast.makeText(this, "Se requiere permiso de ubicación para buscar dispositivos Bluetooth", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startDiscovery() {
        // Limpiar la lista antes de comenzar una nueva búsqueda
        devicesMap.clear();
        devicesArrayAdapter.clear();

        // Registrar el BroadcastReceiver para recibir eventos de descubrimiento de Bluetooth
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        // Iniciar el descubrimiento de dispositivos Bluetooth
        bluetoothAdapter.startDiscovery();
    }

    // Declarar el método para calcular la hashed_mac a partir de la dirección MAC
    private String hashMac(String macAddress) {
        try {
            // Crear una instancia de MessageDigest con el algoritmo SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Convertir la macAddress a bytes
            byte[] macBytes = macAddress.getBytes();

            // Calcular el hash de la macAddress
            byte[] hashBytes = digest.digest(macBytes);

            // Convertir el hash a una representación hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // Devolver la representación hexadecimal del hash
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            // Manejar el caso en que el algoritmo no esté disponible
            return null;
        }
    }


    // BroadcastReceiver para recibir eventos de descubrimiento de Bluetooth
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Se ha encontrado un dispositivo Bluetooth
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceAddress = device.getAddress(); // Obtener la dirección MAC del dispositivo
                String hashed_mac = hashMac(deviceAddress);

                // Obtener la fecha y hora actual
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String fecha_hora = dateFormat.format(new Date());

                //Latitud y longitud
                double latitud = 0.0;
                double longitud = 0.0;
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager != null) {
                    Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (location != null) {
                        latitud = location.getLatitude();
                        longitud = location.getLongitude();
                    }
                }

                //* Cambiar esto si se cambia a list
                if(!devicesMap.containsKey(deviceAddress)){
                    String deviceInfo = "Name: " + deviceName + "\n" +
                            "Address: " + deviceAddress + "\n" +
                            "HASHED Address: " + hashed_mac + "\n" +
                            "Date/Time: " + fecha_hora + "\n" +
                            "Latitude: " + latitud + "\n" +
                            "Longitude: " + longitud ;

                    devicesMap.put(deviceAddress, deviceInfo);
                    updateListView();
                }

                // Llamar al método para enviar los datos al servidor
                sendDataToServer(hashed_mac, fecha_hora, latitud, longitud);
            }

        }
    };


    private void updateListView() {
        // Limpiar y volver a llenar el ArrayAdapter con las direcciones y nombres del mapa
        devicesArrayAdapter.clear();
        //*Cambiar esto tambien
        for (String deviceInfo : devicesMap.values()) {
            devicesArrayAdapter.add(deviceInfo);
        }
        devicesArrayAdapter.notifyDataSetChanged();
    }


    private void sendDataToServer(final String hashed_mac, final String fecha_hora, final double latitud, final double longitud) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Llamar al método para enviar datos al servidor con los parámetros proporcionados
                performDataSending(hashed_mac, fecha_hora, latitud, longitud);
                // Programar la tarea nuevamente después del intervalo de tiempo especificado
                handler.postDelayed(this, INTERVALO_DE_TIEMPO);
            }
        }, INTERVALO_DE_TIEMPO); // Iniciar la tarea después del intervalo de tiempo especificado por primera vez
    }

    private void performDataSending(final String hashed_mac, final String fecha_hora, final double latitud, final double longitud) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                // Crear una instancia de HttpHandler
                HttpHandler httpHandler = new HttpHandler();

                // Realizar la solicitud POST al servidor con los datos proporcionados
                String postUrl = "https://a88f-83-60-71-170.ngrok-free.app/dispositivos/";
                String requestBody = "{\"hashed_mac\": \"" + hashed_mac + "\", \"fecha_hora\": \"" + fecha_hora + "\", \"latitud\": \"" + latitud + "\", \"longitud\": \"" + longitud + "\"}";
                try {
                    return httpHandler.doPostRequest(postUrl, requestBody);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String response) {
                super.onPostExecute(response);
                // Procesar la respuesta recibida si es necesario
                if (response != null) {
                    Log.d("POST Response", response);
                } else {
                    Log.e("POST Error", "No se pudo completar la solicitud HTTP POST");
                }
            }
        }.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener el descubrimiento y desregistrar el BroadcastReceiver al salir de la actividad
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(receiver);

        // Detener el envío de datos al servidor cuando se destruye la actividad
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    // Clase para manejar las solicitudes HTTP
    private static class HttpHandler {

        public String doGetRequest(String urlString) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            StringBuilder response = new StringBuilder();
            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } finally {
                connection.disconnect();
            }
            return response.toString();
        }

        public String doPostRequest(String urlString, String requestBody) throws IOException {
            Log.d("URLString", urlString);
            URL url = new URL(urlString);
            Log.d("URL", url.toString());

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);

            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                byte[] postData = requestBody.getBytes(StandardCharsets.UTF_8);
                outputStream.write(postData);
            }


            StringBuilder response = new StringBuilder();
            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } finally {
                connection.disconnect();
            }
            return response.toString();
        }
    }
}


