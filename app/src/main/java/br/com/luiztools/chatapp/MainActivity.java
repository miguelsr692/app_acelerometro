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
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.*;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static java.lang.Integer.parseInt;
import static java.lang.System.err;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // VARIAVEIS

    // socket
    ListView mensagens = null; //usado na caixa que mostra a resposta do servidor
    ArrayAdapter<String> adapter = null; //variavel que recebe o valr da resposta do socket
    Socket socket = null; //variavel do socket
    private final int READ_SOCKET = 1; //variaveis de estado do socket
    private final int WRITE_SOCKET = 2;

    // acelerometro
    SensorManager sensorManager;
    Sensor acelerometro; //variavel do sensor

    // tela
    TextView x_Values, y_Values, z_Values; //variaveis dos textos na tela
    EditText input; //variavel da caixa de texto que recebe o numero de envios
    Button B_start, B_stop, B_record, B_save, B_fast, B_slow, B_enviar; //variavies do botões

    //arquivo
    File arquivo;
    FileOutputStream fileOutputStream;
    FileWriter fileWriter;
    BufferedWriter out;

    // auxiliares
    boolean init; //verifica se o botão START ja foi pressionado pela primeira vez
    boolean record; //verifica se esta gravando os dados em txt ou não
    boolean enviando; //verifica se está gravando os dados no vetor ou não
    long last_update; //ultima leitura dos dados do sensor
    long delay_medicao; //intervalo entre a ultima medição do sensor (no vetor ou no arquivo)
    long temp; //auxiliar no calculo do interval entre medições
    int delay_tela; //atraso na amostragem das leituras do sensor na tela, mas também afeta a gravação no vetor e no arquivo txt
    int delaySensor; //delay da biblioteca do android do sensor
    int clickCounter; //contador de cliques nos botões de velocidade de leitura
    int medicoes_local; //contador de medicoes feitas -arquivo txt
    int medicoes_server; //contador de medicoes feitas - vetor servidor
    int num_medicoes; //numero limite de medicoes
    String nomeArquivo; //nome do arquivo onde será escrito os dados
    String path; //local de salvamento do arquivo
    String msgErro; //variavel auxiliar para mensagem de erro
    Calendar data; //variavel auxiliar para data
    Date timeStamp; //variavel auxiliar para tempo
    ArrayList b; //vetor que receberá os dados que serão enviados para o servidor


    // -------------------------- FUNÇÕES --------------------------

    /*
    função que é chamada quando o aplicativo se inicia
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // inicializando o sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);                       //define o sensorManager de acordo com o sistema do aparelho
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);               //define qual sensor iremos utilizar
        delaySensor = SensorManager.SENSOR_DELAY_FASTEST;                                       //define o tipo de delay do sensor
        sensorManager.registerListener(MainActivity.this, acelerometro, delaySensor);    //registra o "listener" do sensor

        //funções dos botões
        startMeasure();
        stopMeasure();
        setSensorRate();
        startRecord();
        saveRecord();
        sendButton();

        //inicialização das variaveis para a lista de respostas do  soscket
        mensagens = (ListView)findViewById(R.id.mensagens);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mensagens.setAdapter(adapter);

        //inicialização de outras variáveis
        init = false;
        enviando = false;
        record = false;
        b = new ArrayList();
        medicoes_local = 0;
        medicoes_server = 0;
        delay_tela = 0;
        temp = 0;
        num_medicoes = 10;
        input = findViewById(R.id.n_envios);

        //verifica se há permissão para conexão com a internet, se sim, chama a função que conecta com o servidor e escuta a porta do socket
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, READ_SOCKET);
        }
        else
            waitMessage();
    }

    // -------------------------- COMUNICACAO --------------------------

    /*
    função que é chamada quando o app é fechado
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (record) saveRecord();
        if (socket != null)
            socket.disconnect();
    }

    /*
    função do botão SEND: inicia a gravação dos dados em um vetor
     */
    public void sendButton() {
        B_enviar = findViewById(R.id.B_enviar);

        B_enviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                num_medicoes = parseInt(input.getText().toString());
                enviando = true;
                Toast.makeText(getApplicationContext(), "GRAVANDO DADOS NO VETOR", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /*
    função que faz a conexão com o servidor e fica "escutando" a porta do socket
     */
    private void waitMessage(){
        try {
            //socket = IO.socket("http://10.0.2.2:3001"); //envia localmente
            socket = IO.socket("https://projpibic.herokuapp.com"); //envia para o app online
            socket.on("display", new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
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

    /*
    função que envia o vetor de dados que foi gravado para o servidor
     */
    private void sendMessage() {
        Toast.makeText(getApplicationContext(), "ENVIANDO", Toast.LENGTH_SHORT).show();

        //verifica se tem permissão de conexão com a internet
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, WRITE_SOCKET);
        }

        else {
            //envia o vetor em pacotes de acordo com o limite máximo de envio que o servidor suporta
            //"quebra" o vetor em pedaços menores

            int n = 9000; // limite de envio
            int index_atual = 0;
            int m = b.size()/n;
            List b_aux = new ArrayList();

            for(int i=0; i<m; i++) {
                b_aux = b.subList(index_atual, n+index_atual);
                socket.emit("chat message", b_aux);
                index_atual += n;
            }

            if(index_atual != b.size()) {
                b_aux = b.subList(index_atual, b.size());
                socket.emit("chat message", b_aux);
            }

            socket.emit("chat message", "fim");

            b = new ArrayList(); //zera o vetor
        }
    }

    /*
    função necessária ????????
     */
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

    // -------------------------- MEDICAO --------------------------

    /*
    função que é chamada toda vez que o valor do sensor muda
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        x_Values = findViewById(R.id.X_Values);
        y_Values = findViewById(R.id.Y_Values);
        z_Values = findViewById(R.id.Z_Values);

        //tempo atual do sistema
        long curTime = System.currentTimeMillis();

        //calcula o delay em relação a ultima medição
        timeStamp = new Date();
        long timeInMillis = (new Date()).getTime() + (event.timestamp - System.nanoTime()) / 1000000L;
        delay_medicao = timeInMillis - temp;
        temp = timeInMillis;


        /*
        verifica se o botão start foi pressionado ao menos uma vez - garante que,
        ao iniciar, os valores do sensor na tela estejam zerados, mesmo que o sensor
        esteja sendo lido.
         */
        if (init) {
            if((curTime - last_update) > delay_tela) {
                last_update = curTime;

                //atualiza valores na tela
                x_Values.setText(String.format("%.4f", event.values[0]));
                y_Values.setText(String.format("%.4f", event.values[1]));
                z_Values.setText(String.format("%.4f", event.values[2]));


                //grava as medições no arquivo
                if(record) {
                    Escreve(event.values[0], event.values[1], event.values[2], delay_medicao);
                    medicoes_local++;
                }

                //grava medições num vetor até o limite determinado
                if(enviando && medicoes_server < num_medicoes) {
                    ArrayList a = new ArrayList();

                    a.add(event.values[0]); // valores X, Y e Z do sensor
                    a.add(event.values[1]);
                    a.add(event.values[2]);
                    a.add(delay_medicao);
                    a.add(timeInMillis);
                    b.add(a);

                    medicoes_server++;

                // quando o limite for atingido, envia os dados para o servidor
                } else if(medicoes_server == num_medicoes) {

                    try {
                        sendMessage();
                        Toast.makeText(getApplicationContext(), "ENVIADO", Toast.LENGTH_SHORT).show();
                        medicoes_server = 0;
                        enviando = false;
                    } catch (IOError erro) {
                        Toast.makeText(getApplicationContext(), erro.getMessage(), Toast.LENGTH_LONG).show();
                    }

                }

            }

        }
    }

    /*
    função é chamada quando a precisão do sensor muda
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /*
    aplicativo em pausa
     */
    @Override
    public void onPause() {
        super.onPause();
        if (record) saveRecord();
        sensorManager.unregisterListener(MainActivity.this);
    }

    /*
    aplicativo retorna da pausa
     */
    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, acelerometro, delaySensor);
    }

    /*
    função do botão START: inicia ou retoma medição
     */
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

    /*
    função do botão STOP: pausa medição
     */
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

    /*
    função dos botões FAST e SLOW:
    altera a velocidade na mudança dos valores na tela, mas não na leitura do sensor
     */
    public void setSensorRate() {
        B_fast = findViewById(R.id.B_fast);
        B_slow = findViewById(R.id.B_slow);

        B_fast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickCounter == 0) {
                    delay_tela = 100; //set delay to 100 msec
                    clickCounter = 1;
                    Toast.makeText(getApplicationContext(), "Delay set to 100 msec", Toast.LENGTH_SHORT).show();
                } else if(clickCounter == 1) {
                    delay_tela = 10; //set delay to 10 msec
                    clickCounter = 2;
                    Toast.makeText(getApplicationContext(), "Delay set to 10 msec", Toast.LENGTH_SHORT).show();
                } else if (clickCounter == 2) {
                        delay_tela = 0; //set delay to 100 msec
                        clickCounter = 3;
                        Toast.makeText(getApplicationContext(), "Delay set to 0", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Minimum delay defined!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        B_slow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickCounter == 3) {
                    delay_tela = 10; //set delay to 10 msec
                    clickCounter = 2;
                    Toast.makeText(getApplicationContext(), "Delay set to 10 msec", Toast.LENGTH_SHORT).show();
                } else if (clickCounter == 2) {
                    delay_tela = 100; //set delay to 100 msec
                    clickCounter = 1;
                    Toast.makeText(getApplicationContext(), "Delay set to 100 msec", Toast.LENGTH_SHORT).show();
                } else if(clickCounter == 1) {
                    delay_tela = 1000; //set delay to 1000 msec = 1 sec
                    clickCounter = 0;
                    Toast.makeText(getApplicationContext(), "Delay set to 1000 msec", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Maximum delay defined!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /*
    função do botão RECORD:
    cria o arquivo que será escrito na memória interna do celular
     */
    public void startRecord() {
        B_record = findViewById(R.id.B_record);
        nomeArquivo = "testSaveData.txt";
        path = getExternalFilesDir("newDataStorage").toString(); //cria nova pasta


        B_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //inicialização do arquivo
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

    /*
    rotina que escreve cada valor do sensor em um arquivo txt externo
     */
    public void Escreve(float valorX, float valorY, float valorZ, long delay) {
        try {
            data = new GregorianCalendar();
            out.write("\nX: " + valorX);
            out.write("; Y: " + valorY);
            out.write("; Z: " + valorZ);
            out.write("; Delay: " + delay + "msec");
            out.write("; TimeStamp: " + data.get(Calendar.HOUR_OF_DAY)
                    + ":" + data.get(Calendar.MINUTE)
                    + ":" + data.get(Calendar.SECOND)
                    + ":" + data.get(Calendar.MILLISECOND));

        } catch (Exception erro) {
            msgErro = "Can't save data!\nError: " + erro.getMessage();
            Toast.makeText(getApplicationContext(), msgErro, Toast.LENGTH_SHORT).show();
        }
    }

    /*
    função do botão SAVE: para a gravação dos dados em arquivo txt externo
    */
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
