package com.example.client;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public class NV21EncoderH264 {

    private int width, height;
    private int frameRate = 30;
    private MediaCodec mediaCodec;
    private EncoderListener encoderListener;

    public NV21EncoderH264(int width, int height) {
        this.width = width;
        this.height = height;
        initMediaCodec();
    }

    private void initMediaCodec() {
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            //height和width一般都是照相机的height和width。
            //TODO 因为获取到的视频帧数据是逆时针旋转了90度的，所以这里宽高需要对调
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", height, width);
            //描述平均位速率（以位/秒为单位）的键。 关联的值是一个整数
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5);
            //描述视频格式的帧速率（以帧/秒为单位）的键。帧率，一般在15至30之内，太小容易造成视频卡顿。
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            //色彩格式，具体查看相关API，不同设备支持的色彩格式不尽相同
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            //关键帧间隔时间，单位是秒
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //开始编码
            mediaCodec.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将NV21编码成H264
     */
    public void encoderH264(byte[] data) {
        //将NV21编码成NV12
        byte[] bytes = NV21ToNV12(data, width, height);
        //视频顺时针旋转90度
        byte[] nv12 = rotateNV290(bytes, width, height);

        try {
            //拿到输入缓冲区,用于传送数据进行编码
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            //拿到输出缓冲区,用于取到编码后的数据
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            //当输入缓冲区有效时,就是>=0
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                //往输入缓冲区写入数据
                inputBuffer.put(nv12);
                //五个参数，第一个是输入缓冲区的索引，第二个数据是输入缓冲区起始索引，第三个是放入的数据大小，第四个是时间戳，保证递增就是
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, nv12.length, System.nanoTime() / 1000, 0);
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //拿到输出缓冲区的索引
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                //outData就是输出的h264数据
                if (encoderListener != null) {
                    encoderListener.h264(outData);
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * 因为从MediaCodec不支持NV21的数据编码，所以需要先讲NV21的数据转码为NV12
     */
    private byte[] NV21ToNV12(byte[] nv21, int width, int height) {
        byte[] nv12 = new byte[width * height * 3 / 2];
        int frameSize = width * height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[j + frameSize];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[j + frameSize - 1];
        }
        return nv12;
    }

    /**
     * 此处为顺时针旋转旋转90度
     *
     * @param data        旋转前的数据
     * @param imageWidth  旋转前数据的宽
     * @param imageHeight 旋转前数据的高
     * @return 旋转后的数据
     */
    private byte[] rotateNV290(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    /**
     * 设置编码成功后数据回调
     */
    public void setEncoderListener(EncoderListener listener) {
        encoderListener = listener;
    }

    public interface EncoderListener {
        void h264(byte[] data);
    }
}
