package com.example.tfg_bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<String> devicesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicesList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devicesList);

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
        } else {
            // Después de la inicialización de devicesListView en onCreate
            Button searchButton = findViewById(R.id.searchButton);
            searchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Manejar el clic del botón para iniciar la búsqueda nuevamente
                    startDiscovery();
                }
            });
        }
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
        devicesList.clear();

        // Registrar el BroadcastReceiver para recibir eventos de descubrimiento de Bluetooth
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        // Iniciar el descubrimiento de dispositivos Bluetooth
        bluetoothAdapter.startDiscovery();
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

                // Agregar el dispositivo a la lista
                devicesList.add(deviceName + "\n" + deviceAddress);
                devicesArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener el descubrimiento y desregistrar el BroadcastReceiver al salir de la actividad
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(receiver);
    }
}
