package br.com.luiztools.chatapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static java.lang.System.err;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // socket
    ListView mensagens = null;
    ArrayAdapter<String> adapter = null;
    Socket socket = null;
    private final int READ_SOCKET = 1;
    private final int WRITE_SOCKET = 2;

    // acelerometro
    SensorManager sensorManager;
    Sensor acelerometro;
    TextView x_Values, y_Values, z_Values;
    Button B_start, B_stop, B_record, B_save, B_fast, B_slow, B_enviar;
    File arquivo;
    FileOutputStream fileOutputStream;
    FileWriter fileWriter;
    BufferedWriter out;

    // auxiliares
    boolean init;
    boolean record;
    boolean enviando;
    long last_update;
    int delay;
    int delaySensor;
    int clickCounter;
    int medicoes_local;
    int medicoes_server;
    String nomeArquivo;
    String path;
    String msgErro;
    Calendar data;
    Date timeStamp;
    JSONObject b;

    private ObjectInputStream inputObjectStream = null;
    private ObjectOutputStream outputObjectStream = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        delaySensor = SensorManager.SENSOR_DELAY_GAME;
        sensorManager.registerListener(MainActivity.this, acelerometro, delaySensor);

        startMeasure();
        stopMeasure();
        setSensorRate(); //CHECAR CONDIÇÕES DOS BOTÕES
        startRecord();
        saveRecord();
        sendButton();

        mensagens = (ListView)findViewById(R.id.mensagens);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mensagens.setAdapter(adapter);

        init = false;
        enviando = false;
        record = false;
        b = new JSONObject();
        medicoes_local = 0;
        medicoes_server = 0;
        delay = 0;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, READ_SOCKET);
        }
        else
            waitMessage();
    }

    // COMUNICACAO
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (socket != null)
            socket.disconnect();
    }

    public void sendButton() {
        B_enviar = findViewById(R.id.B_enviar);

        B_enviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enviando = true;
                Toast.makeText(getApplicationContext(), "ENVIANDO", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void waitMessage(){
        try {
            socket = IO.socket("http://10.0.2.2:3001");
            socket.on("chat message", new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            /*JSONObject x = new JSONObject();
                            try {
                                //x.put("font", 1); // 1 = celular, 0 = servidor
                                //adapter.add(args[0].toString());
                                //boolean y = args[0] instanceof JSONObject;
                                //Toast.makeText(getApplicationContext(), String.valueOf(y), Toast.LENGTH_SHORT).show();



                            } catch (JSONException e) {
                                e.printStackTrace();
                            }*/

                            //coloca a mensagem recebida na lista
                            adapter.add(args[0].toString());
                            adapter.notifyDataSetChanged();

                            // Apenas faz um scroll para o novo item da lista
                            mensagens.smoothScrollToPosition(adapter.getCount() - 1);
                        }
                    });
                }
            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, WRITE_SOCKET);
        }
        else {
            socket.emit("chat message", b);
            b = new JSONObject();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case READ_SOCKET: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    waitMessage();
                    return;
                }
                break;
            }
            case WRITE_SOCKET: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendMessage();
                    return;
                }
                break;
            }
        }

        Toast.makeText(this, "Sem essa permissão o app não irá funcionar. Tente novamente.", Toast.LENGTH_LONG).show();
    }

    // MEDICAO
    @Override
    public void onSensorChanged(SensorEvent event) {
        x_Values = findViewById(R.id.X_Values);
        y_Values = findViewById(R.id.Y_Values);
        z_Values = findViewById(R.id.Z_Values);

        long curTime = System.currentTimeMillis();

        if (init) {

            if((curTime) - last_update > delay) {
                last_update = curTime;

                //atualiza valores na tela
                x_Values.setText(String.format("%.4f", event.values[0]));
                y_Values.setText(String.format("%.4f", event.values[1]));
                z_Values.setText(String.format("%.4f", event.values[2]));

                if(record) {
                    Escreve(event.values[0], event.values[1], event.values[2]);
                    medicoes_local++;
                }

                if(enviando && medicoes_server < 50) {
                    // data = new GregorianCalendar();
                    // String timeStamp = data.get(Calendar.HOUR_OF_DAY) + ":" + data.get(Calendar.MINUTE) + ":" + data.get(Calendar.SECOND) + ":" + data.get(Calendar.MILLISECOND);
                    timeStamp = new Date();

                    try {
                        JSONObject a = new JSONObject();
                        a.put("font", 1); // 1 = celular, 0 = servidor
                        a.put("x", x_Values.getText().toString());
                        a.put("y", y_Values.getText().toString());
                        a.put("z", z_Values.getText().toString());
                        a.put("TimeStamp", timeStamp.getTime());
                        b.accumulate("Pacote", a);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    medicoes_server++;
                    //Toast.makeText(getApplicationContext(), String.valueOf(medicoes_server), Toast.LENGTH_SHORT).show();
                } else if(medicoes_server == 50) {
                    sendMessage();
                    Toast.makeText(getApplicationContext(), "ENVIADO", Toast.LENGTH_SHORT).show();
                    medicoes_server = 0;
                    enviando = false;
                }
            }




        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onPause() {
        super.onPause();
        saveRecord();
        sensorManager.unregisterListener(MainActivity.this);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, acelerometro, delaySensor);
    }

    public void startMeasure() {
        B_start = findViewById(R.id.B_start);

        B_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Measure started!", Toast.LENGTH_SHORT).show();
                sensorManager.registerListener(MainActivity.this, acelerometro, delaySensor);
                init = true;
            }
        });
    }

    public void stopMeasure() {
        B_stop = findViewById(R.id.B_stop);

        B_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorManager.unregisterListener(MainActivity.this);
                Toast.makeText(getApplicationContext(), "Measured paused!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setSensorRate() {
        B_fast = findViewById(R.id.B_fast);
        B_slow = findViewById(R.id.B_slow);

        B_fast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickCounter == 0) {
                    delay = 100; //set delay to 100 msec
                    clickCounter = 1;
                    Toast.makeText(getApplicationContext(), "Delay set to 100 msec", Toast.LENGTH_SHORT).show();
                } else if(clickCounter == 1) {
                    delay = 1; //set delay to 1 msec
                    clickCounter = 2;
                    Toast.makeText(getApplicationContext(), "Delay set to 1 msec", Toast.LENGTH_SHORT).show();
                } else {
                    delay = 0; //no data delay
                    clickCounter = 3;
                    Toast.makeText(getApplicationContext(), "Minimum delay defined!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        B_slow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickCounter == 3) {
                    delay = 1; //set delay to 1 msec
                    clickCounter = 1;
                    Toast.makeText(getApplicationContext(), "Delay set to 1 msec", Toast.LENGTH_SHORT).show();
                } else if (clickCounter == 2) {
                    delay = 100; //set delay to 100 msec
                    clickCounter = 1;
                    Toast.makeText(getApplicationContext(), "Delay set to 100 msec", Toast.LENGTH_SHORT).show();
                } else if(clickCounter == 1) {
                    delay = 1000; //set delay to 1000 msec = 1 sec
                    clickCounter = 0;
                    Toast.makeText(getApplicationContext(), "Delay set to 1000 msec", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Maximum delay defined!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void startRecord() {
        B_record = findViewById(R.id.B_record);
        nomeArquivo = "testSaveData.txt";
        //path = "/storage/emulated/0/Android/data/br.com.luiztools.chatapp/files";
        //path = getExternalCacheDir().toString();
        path = getExternalFilesDir("newDataStorage").toString(); //cria nova pasta


        B_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    arquivo = new File(path, nomeArquivo);
                    fileOutputStream = new FileOutputStream(arquivo.toString(), true);
                    fileWriter = new FileWriter(arquivo);
                    out = new BufferedWriter(fileWriter);
                    out.write("MEASUREMENT START\n");
                    Toast.makeText(getApplicationContext(), "Data recording started", Toast.LENGTH_SHORT).show();
                } catch (FileNotFoundException erro) {
                    msgErro = "Can't start data recording!\nErro: " + erro.getMessage();
                    Toast.makeText(getApplicationContext(), msgErro, Toast.LENGTH_SHORT).show();
                } catch (IOException erro) {
                    msgErro = "Can't start data recording!\nErro: " + erro.getMessage();
                    Toast.makeText(getApplicationContext(), msgErro, Toast.LENGTH_SHORT).show();
                }

                record = true;
            }
        });
    }

    public void Escreve(float valorX, float valorY, float valorZ) {
        try {
            data = new GregorianCalendar();
            out.write("\nX: " + valorX);
            out.write("; Y: " + valorY);
            out.write("; Z: " + valorZ);
            //out.write("; Delay: " + delay + "msec");
            out.write("; TimeStamp: " + data.get(Calendar.HOUR_OF_DAY)
                    + ":" + data.get(Calendar.MINUTE)
                    + ":" + data.get(Calendar.SECOND)
                    + ":" + data.get(Calendar.MILLISECOND));

        } catch (Exception erro) {
            msgErro = "Can't save data!\nError: " + erro.getMessage();
            Toast.makeText(getApplicationContext(), msgErro, Toast.LENGTH_SHORT).show();
        }
    }

    public void saveRecord() {
        B_save = findViewById(R.id.B_save);

        B_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                record = false;

                try {
                    out.write("\n\n" + String.valueOf(medicoes_local));
                    out.write("\n\nEND OF MEASURE");
                    out.close();
                    fileOutputStream.close();
                    medicoes_local = 0;
                    Toast.makeText(getApplicationContext(), "Text saved successfully", Toast.LENGTH_SHORT).show();
                } catch (Exception erro) {
                    msgErro = "Can't save data!\nErro: " + erro.getMessage();
                    Toast.makeText(getApplicationContext(), msgErro, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
