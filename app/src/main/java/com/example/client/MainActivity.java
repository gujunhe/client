package com.example.client;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SurfaceHolder.Callback, NV21EncoderH264.EncoderListener {
    /**
     *
     * 相机
     *
     */
    private String[] permissions = new String[]{

            Manifest.permission.CAMERA,

    };
    NV21EncoderH264 nv21EncoderH264;
    private  boolean isSending=false;
    private SurfaceHolder holder;
    private final int MY_REQUEST_CODE = 1000;
    static ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static final String TAG = "MainActivity";

    private DataOutputStream os;
    private EditText et_ip;
    private EditText et_msg;
    private TextView tv_send;
    private TextView tv_confirm;
    private SurfaceView surfaceView;
    private Socket mSocket;
    private Camera camera;
    private ServerSocket mServerSocket;
    private OutputStream mOutStream;
    private InputStream mInStream;
    private SocketConnectThread socketConnectThread;
    private StringBuffer sb = new StringBuffer();

    @SuppressLint("HandlerLeak")
    public Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1){
                Bundle data = msg.getData();
                sb.append(data.getString("msg"));
                sb.append("\n");
                tv_msg.setText(sb.toString());
            }
        }
    };
    private TextView tv_msg;




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        PackageManager packageManager = this.getPackageManager();

        PermissionInfo permissionInfo = null;
//        PermissionGroupInfo permissionGroupInfo = null;

        for (int i = 0; i < permissions.length; i++) {
            try {
                permissionInfo = packageManager.getPermissionInfo(permissions[i], 0);
//                permissionGroupInfo = packageManager.getPermissionGroupInfo(permissions[i], 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            CharSequence permissionName = permissionInfo.loadLabel(packageManager);
//            CharSequence permissionnGropName = permissionGroupInfo.loadLabel(packageManager);
            if (ContextCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED){
                // 未获取权限
                Log.i(TAG, "您未获得【" + permissionName + "】的权限 ===>");
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])){
                    // 这是一个坑，某些手机弹出提示时没有永不询问的复选框，点击拒绝就默认勾上了这个复选框，而某些手机上即使勾选上了永不询问的复选框也不起作用
                    Log.i(TAG, "您勾选了不再提示【" + permissionName + "】权限的申请");
                } else {
                    ActivityCompat.requestPermissions(this, permissions, MY_REQUEST_CODE);
                }
            } else {
                Log.i(TAG, "您已获得了【" + permissionName + "】的权限");
            }
        }

        setContentView(R.layout.activity_main);

        initView();
        setListener();
        try {
            mServerSocket = new ServerSocket(1989);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
    private void initView() {

        tv_confirm = (TextView) findViewById(R.id.tv_confirm);
        surfaceView=findViewById(R.id.sfv);
        holder = surfaceView.getHolder();
        holder.addCallback(this);

    }

    private void setListener() {

        tv_confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!isSending) {
                    socketConnectThread=new SocketConnectThread();
                    socketConnectThread.start();
                    tv_confirm.setText("停止监控");
                    isSending=true;
                }
                else {

                    socketConnectThread.interrupt();
                    isSending=false;
                    tv_confirm.setText("开始监控");
                }
            }
        });
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        //打开相机
        openCamera();
    }




    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        //关闭相机
        releaseCamera(camera);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        //获取相机参数
        Camera.Parameters parameters = camera.getParameters();
        //获取相机支持的预览的大小
        Camera.Size previewSize = getCameraPreviewSize(parameters);

        int width = previewSize.width;
        int height = previewSize.height;
        Log.d("AAA",""+width+" "+height);
        //设置预览格式（也就是每一帧的视频格式）YUV420下的NV21
        parameters.setPreviewFormat(ImageFormat.NV21);
        //设置预览图像分辨率

        parameters.setPreviewSize(width, height);
        //相机旋转90度
        camera.setDisplayOrientation(90);
        //配置camera参数
        camera.setParameters(parameters);
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //设置监听获取视频流的每一帧
        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
            }
        });
          nv21EncoderH264 = new NV21EncoderH264(width, height);
        nv21EncoderH264.setEncoderListener(this);


        //调用startPreview()用以更新preview的surface
        camera.startPreview();
        camera.autoFocus(null);
    }
    //编码成功的回调
    @Override
    public void h264(byte[] data) {
        Log.e("TAG1", data.length+ "");
        try {
            send(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    /**
     * 获取设备支持的最大分辨率
     */
    private Camera.Size getCameraPreviewSize(Camera.Parameters parameters) {
        List<Camera.Size> list = parameters.getSupportedPreviewSizes();
        Camera.Size needSize = null;
        for (Camera.Size size : list) {
            if (needSize == null) {
                needSize = size;
                continue;
            }
            if (size.width >= needSize.width) {
                if (size.height > needSize.height) {
                    needSize = size;
                }
            }
        }
        return needSize;
    }

    /**
     * 关闭相机
     */
    public void releaseCamera(Camera camera) {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
        }
    }


    class SocketConnectThread extends Thread{
        public void run(){
            Log.e("info", "run: ============线程启动" );
            try {
                //等待客户端的连接，Accept会阻塞，直到建立连接，
                //所以需要放在子线程中运行。
                mSocket = mServerSocket.accept();
                 os = new DataOutputStream(mSocket.getOutputStream());
                 mSocket.setSendBufferSize(1000000);
                //设置监听获取视频流的每一帧
                camera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        nv21EncoderH264.encoderH264(data);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            Log.e("info","connect success========================================");

        }

    }

    public void send( byte[] data) throws InterruptedException {
        Thread thread=new Thread() {
            @Override
            public void run() {
                try {
                    // socket.getInputStream()
                    if(mSocket!=null&&isSending) {

                        Log.e("TAG2", data.length+ "");
                        os.writeInt(data.length);
                        os.flush();
                        //os.write(data);
                        os.write(data);
                        os.flush();

                       // writer.writeUTF(str); // 写一个UTF-8的信息
                    }
                } catch (IOException e) {
                    socketConnectThread=new SocketConnectThread();
                    socketConnectThread.start();
                    e.printStackTrace();
                }
            }
        };
        executorService.submit(thread);
    }

    /**
     * 从参数的Socket里获取最新的消息
     */
    private void startReader(final Socket socket) {

        new Thread(){
            @Override
            public void run() {
                DataInputStream reader;
                try {
                    // 获取读取流
                    reader = new DataInputStream(socket.getInputStream());
                    while (true) {
                        System.out.println("*等待客户端输入*");
                        // 读取数据
                        String msg = reader.readUTF();
                        System.out.println("获取到客户端的信息：=" + msg);
                        Message message = new Message();
                        message.what = 1;
                        Bundle bundle = new Bundle();
                        bundle.putString("msg", msg);
                        message.setData(bundle);

                        handler.sendMessage(message);
                    }
                } catch (IOException e) {

                    e.printStackTrace();
                }
            }
        }.start();
    }





    public static String getIPAddress(Context context) {
        NetworkInfo info = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//当前使用2G/3G/4G网络
                try {
                    //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }

            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//当前使用无线网络
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());//得到IPV4地址
                return ipAddress;
            }
        } else {
            //当前无网络连接,请在设置中打开网络
        }
        return null;
    }

    /**
     * 将得到的int类型的IP转换为String类型
     *
     * @param ip
     * @return
     */
    public static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }
}
