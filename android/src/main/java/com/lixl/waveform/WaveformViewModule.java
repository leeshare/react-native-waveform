
package com.lixl.waveform;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
//import android.support.v7.app.AppCompatActivity;
import android.content.ContextWrapper;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.iflytek.cloud.EvaluatorListener;
import com.iflytek.cloud.EvaluatorResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvaluator;

import com.iflytek.cloud.SpeechUtility;
import com.lixl.waveform.view.VoiceLineView;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;

import android.widget.Toast;

import static android.content.Context.MODE_PRIVATE;

/**
 * Author: <a href="https://github.com/leeshare">lixl</a>
 * <p>
 * Created by lixl on 17/5/17.
 * <p>
 * show a waveform controlled by a voice
 * <p>
 */

public class WaveformViewModule extends ReactContextBaseJavaModule implements Runnable, LifecycleEventListener {

    private MediaRecorder mMediaRecorder;
    private boolean isAlive = true;
    private VoiceLineView voiceLineView;

    Activity activity;
    private Dialog dialog = null;

    private Context mContext;

    private static final String BOX_HEIGHT = "boxHeight";   //弹出框 高度
    private static final String STANDARD_TXT = "standardTxt";   //用于识别语音的 标准文字
    private static final String DESTINATION_DIR = "destinationDir";     //用户语音保存地址

    private static final String CONFIRM_EVENT_NAME = "confirmEvent";
    private static final String EVENT_KEY_CONFIRM = "confirm";

    //科大讯飞
    private static String TAG = "Waveform";

    private final static String PREFER_NAME = "ise_settings";
    private final static int REQUEST_CODE_SETTINGS = 1;

    // 评测语种
	private String language;
	// 评测题型
	private String category;
	// 结果等级
	private String result_level;

	private String mLastResult;

    private SpeechEvaluator mIse;

    private Toast mToast;

    private String standardTxt;

    private String destinationDir = "self";


    public WaveformViewModule(ReactApplicationContext reactContext){
        super(reactContext);
    }

    @Override
    public String getName() {
        return "WaveformViewModule";
    }

    @ReactMethod
    public void alert(String message) {
        Toast.makeText(getReactApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    /*
    初始化波形
    录音到本地
     */
    private void initMediaRecorder(){
        if (mMediaRecorder == null)
            mMediaRecorder = new MediaRecorder();
        else {
            mMediaRecorder.release();
            mMediaRecorder = new MediaRecorder();
        }

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

        /*if(destinationDir == null || destinationDir == "") {
            destinationDir = ".ys/";
        }
        String fullPath = Environment.getExternalStorageDirectory().getPath() + "/" + destinationDir;
        showTip(fullPath);
        File temp = Environment.getExternalStoragePublicDirectory(fullPath);
        if(!temp.exists()){
            temp.mkdir();
        }*/

        String fullPath = mContext.getApplicationContext().getExternalFilesDir("").getAbsolutePath();
        //File file = new File(Environment.getExternalStorageDirectory().getPath(), "HelloWorld.log");
        //File file = new File(fullPath, "self_" + destinationDir);
        File file = new File(fullPath, destinationDir);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        mMediaRecorder.setMaxDuration(1000 * 60 * 10);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.start();
    }

    class MyClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            stop();
        }
    }

    TextView btn;
    private void bindButtonClick(View view){
        btn = (TextView)view.findViewById(R.id.txtStopVoice);
        btn.setOnClickListener(new MyClickListener());
    }

    @ReactMethod
    public void _init(ReadableMap options) throws InterruptedException {
        activity = getCurrentActivity();
        if(activity != null){
            mContext = activity.getApplicationContext();
            //SpeechUtility.createUtility(mContext, "appid=" + mContext.getString(R.string.app_id));
            View view = activity.getLayoutInflater().inflate(R.layout.activity_main, null);

            isAlive = true;
            int height = 300;
            standardTxt = "";
            if(options.hasKey(BOX_HEIGHT)){
                height = options.getInt(BOX_HEIGHT);
            }
            if(options.hasKey(STANDARD_TXT)){
                standardTxt = options.getString(STANDARD_TXT);
            }
            //destinationDir = ".ys/";
            if(options.hasKey(DESTINATION_DIR)){
                destinationDir = options.getString(DESTINATION_DIR);
            }
//            if (dialog == null) {
                dialog = new Dialog(activity, R.style.Dialog_Full_Screen);
                dialog.setContentView(view);
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                Window window = dialog.getWindow();
                if (window != null) {
                    layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    layoutParams.format = PixelFormat.TRANSPARENT;
                    layoutParams.windowAnimations = R.style.PickerAnim;
                    layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                    layoutParams.height = height;
                    layoutParams.gravity = Gravity.BOTTOM;
                    layoutParams.y = 0;
                    window.setAttributes(layoutParams);
                }

                dialog.show();
//            } else {
//                dialog.show();
//            }

            voiceLineView = (VoiceLineView)view.findViewById(R.id.voicLine);
//            initMediaRecorder();

            if(mIse == null){
                mIse = SpeechEvaluator.createEvaluator(mContext, null);
            }
            startEvaluate();

            bindButtonClick(view);
            Thread thread = new Thread(this);
            thread.start();
        }else {
            Toast.makeText(getReactApplicationContext(), "Activity is null", Toast.LENGTH_SHORT).show();
        }
    }

    @ReactMethod
    public void start(ReadableMap options) {
        if (dialog == null) {
            return;
        }
        if (!dialog.isShowing()) {
            isAlive = true;
//            initMediaRecorder();

            if(options != null && options.hasKey(STANDARD_TXT)){
                standardTxt = options.getString(STANDARD_TXT);
            }
            int height = 300;
            if(options.hasKey(BOX_HEIGHT)){
                height = options.getInt(BOX_HEIGHT);
            }

            mContext = activity.getApplicationContext();
            if(mIse == null)
                mIse = SpeechEvaluator.createEvaluator(mContext, null);
            startEvaluate();

            dialog.show();
            Thread thread = new Thread(this);
            thread.start();
        }
    }

    private void hideDialog(){
        if (dialog == null) {
            return;
        }
        if (dialog.isShowing()) {

            isAlive = false;
            if(mMediaRecorder != null) {
                mMediaRecorder.stop();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }

            dialog.dismiss();
            handler.removeCallbacks(this);
        }
    }

    @ReactMethod
    public void stop() {
        if(mIse != null) {
            showTip("mIse.isEvaluating()=" + (mIse.isEvaluating()));
            if (mIse.isEvaluating()) {
                mIse.stopEvaluating();
                showTip("mIse.isEvaluating()=" + (mIse.isEvaluating()));
            }
        }
        hideDialog();
        commonEvent(EVENT_KEY_CONFIRM, 2);
    }

    private static final String ERROR_NOT_INIT = "please initialize the component first";

    @ReactMethod
    public void isWaveformShow(Callback callback) {
        if (callback == null)
            return;
        if (dialog == null) {
            callback.invoke(ERROR_NOT_INIT);
        } else {
            callback.invoke(null, dialog.isShowing());
        }
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(mMediaRecorder==null) return;
            double ratio = (double) mMediaRecorder.getMaxAmplitude() / 100;
            //if(mIse == null) return;
            //double ratio = (double) mIse.get
            double db = 0;// 分贝
            //默认的最大音量是100,可以修改，但其实默认的，在测试过程中就有不错的表现
            //你可以传自定义的数字进去，但需要在一定的范围内，比如0-200，就需要在xml文件中配置maxVolume
            //同时，也可以配置灵敏度sensibility
            if (ratio > 1)
                db = 20 * Math.log10(ratio);
            //只要有一个线程，不断调用这个方法，就可以使波形变化
            //主要，这个方法必须在ui线程中调用
            voiceLineView.setVolume((int) (db));
        }
    };

    @Override
    public void onHostPause(){

    }
    @Override
    public void onHostResume(){

    }
    @Override
    public void onHostDestroy() {
        isAlive = false;
        if(mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    @Override
    public void run() {
        while (isAlive) {
            handler.sendEmptyMessage(0);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void commonEvent(String eventKey, Integer type) {
        WritableMap map = Arguments.createMap();
        map.putString("type", eventKey);
        /*WritableArray indexes = Arguments.createArray();
        WritableArray values = Arguments.createArray();
        for (ReturnData data : returnData) {
            indexes.pushInt(data.getIndex());
            values.pushString(data.getItem());
        }*/

        String voiceResult = "";
        // 解析最终结果
        if (!TextUtils.isEmpty(mLastResult)) {
            com.iflytek.ise.result.xml.XmlResultParser resultParser = new com.iflytek.ise.result.xml.XmlResultParser();
            com.iflytek.ise.result.Result result = resultParser.parse(mLastResult);

            if (null != result) {
                voiceResult = result.toString();
                Log.d(TAG, "结果：" + voiceResult);
                showTip("结果：" + voiceResult);

            } else {
                showTip("解析结果为空");
            }
        }

        map.putString("voiceResult", voiceResult);
        map.putString("voiceApiType", type.toString());
        sendEvent(getReactApplicationContext(), CONFIRM_EVENT_NAME, map);
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    //接入第三方的 科大讯飞 语音测评

    // 评测监听接口
    private EvaluatorListener mEvaluatorListener = new EvaluatorListener() {

        @Override
        public void onResult(EvaluatorResult result, boolean isLast) {
            Log.d(TAG, "evaluator result :" + isLast);

            if (isLast) {
                StringBuilder builder = new StringBuilder();
                builder.append(result.getResultString());
                mLastResult = builder.toString();

                showTip("评测结束");
                hideDialog();
                commonEvent(EVENT_KEY_CONFIRM, 1);
            }else {
                showTip("测评进行中");
            }
        }

        @Override
        public void onError(SpeechError error) {
            if(error != null) {
                showTip("error:"+ error.getErrorCode() + "," + error.getErrorDescription());
                alert(error.getErrorDescription());
                hideDialog();
                commonEvent(EVENT_KEY_CONFIRM, 1);
                //startEvaluate();
            } else {
                Log.d(TAG, "evaluator over");
                showTip("evaluator over");
            }
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            Log.d(TAG, "evaluator begin");
            showTip("evaluator begin");
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            Log.d(TAG, "evaluator stoped");
            showTip("evaluator stopped");
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            //showTip("当前音量：" + volume);
            //showTip("返回音频数据："+data.length);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };

    private void startEvaluate(){
        if (mIse == null) {
            showTip("mIse is null in 'startEvaluate'");
            return;
        }
        showTip(standardTxt);
        String evaText = standardTxt;
        mLastResult = null;

        setParams();
        mIse.startEvaluating(evaText, null, mEvaluatorListener);
    }

    private void setParams() {
        /*SharedPreferences pref = activity.getSharedPreferences(PREFER_NAME, MODE_PRIVATE);
        // 设置评测语言
        language = pref.getString(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置需要评测的类型
        category = pref.getString(SpeechConstant.ISE_CATEGORY, "read_sentence");
        // 设置结果等级（中文仅支持complete）
        result_level = pref.getString(SpeechConstant.RESULT_LEVEL, "complete");
        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        String vad_bos = pref.getString(SpeechConstant.VAD_BOS, "5000");
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        String vad_eos = pref.getString(SpeechConstant.VAD_EOS, "1800");
        // 语音输入超时时间，即用户最多可以连续说多长时间；
        String speech_timeout = pref.getString(SpeechConstant.KEY_SPEECH_TIMEOUT, "-1");
        */
        language = "zh_cn";
        // 设置需要评测的类型
        //category = "read_sentence";
        category = "read_word";
        // 设置结果等级（中文仅支持complete）
        result_level = "complete";
        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        String vad_bos = "5000";
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        String vad_eos = "1800";
        // 语音输入超时时间，即用户最多可以连续说多长时间；
        String speech_timeout = "-1";

        mIse.setParameter(SpeechConstant.LANGUAGE, language);
        mIse.setParameter(SpeechConstant.ISE_CATEGORY, category);
        mIse.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        mIse.setParameter(SpeechConstant.VAD_BOS, vad_bos);
        mIse.setParameter(SpeechConstant.VAD_EOS, vad_eos);
        mIse.setParameter(SpeechConstant.KEY_SPEECH_TIMEOUT, speech_timeout);
        mIse.setParameter(SpeechConstant.RESULT_LEVEL, result_level);

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
//        mIse.setParameter(SpeechConstant.AUDIO_FORMAT, null);
//        mIse.setParameter(SpeechConstant.ISE_AUDIO_PATH, "");

        String fullPath = mContext.getApplicationContext().getExternalFilesDir("").getAbsolutePath();
        mIse.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        //mIse.setParameter(SpeechConstant.ISE_AUDIO_PATH, fullPath + "/self.wav");
        //mIse.setParameter(SpeechConstant.ISE_AUDIO_PATH, fullPath + "/self");
        mIse.setParameter(SpeechConstant.ISE_AUDIO_PATH, fullPath + "/" + destinationDir);
	try {
            File f = new File(fullPath + "/" + destinationDir);
            if(f.exists()){
                f.delete();
            }
        } catch (Exception e){

        }
    }

    private void showTip(String str) {
        //alert(str);
	}

    /*
    初始化，并开始记录
    只是记录声音到本地，不去科大讯飞校验
    */
    @ReactMethod
    public void initRecordVoice(ReadableMap options) throws InterruptedIOException {
        activity = getCurrentActivity();
        if (activity != null) {
            mContext = activity.getApplicationContext();
            View view = activity.getLayoutInflater().inflate(R.layout.activity_main, null);

            isAlive = true;
            int height = 300;
            standardTxt = "";
            if (options.hasKey(BOX_HEIGHT)) {
                height = options.getInt(BOX_HEIGHT);
            }
            //destinationDir = ".ys/";
            if (options.hasKey(DESTINATION_DIR)) {
                destinationDir = options.getString(DESTINATION_DIR);
            }
            if (dialog == null) {
                dialog = new Dialog(activity, R.style.Dialog_Full_Screen);
                dialog.setContentView(view);
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                Window window = dialog.getWindow();
                if (window != null) {
                    layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    layoutParams.format = PixelFormat.TRANSPARENT;
                    layoutParams.windowAnimations = R.style.PickerAnim;
                    layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                    layoutParams.height = height;
                    layoutParams.gravity = Gravity.BOTTOM;
                    layoutParams.y = 0;
                    window.setAttributes(layoutParams);
                }

                dialog.show();
            } else {
                dialog.show();
            }

            voiceLineView = (VoiceLineView) view.findViewById(R.id.voicLine);
            initMediaRecorder();

            bindButtonClick(view);
            Thread thread = new Thread(this);
            thread.start();
        } else {
            Toast.makeText(getReactApplicationContext(), "Activity is null", Toast.LENGTH_SHORT).show();
        }
    }

    /*
    再次
    记录声音到本地，不去科大讯飞校验
    （覆盖原来的记录）
     */
    @ReactMethod
    public void startRecordVoice(ReadableMap options){
        if (dialog == null) {
            return;
        }
        if (!dialog.isShowing()) {
            isAlive = true;
            if (options.hasKey(DESTINATION_DIR)) {
                destinationDir = options.getString(DESTINATION_DIR);
            }
            initMediaRecorder();

            mContext = activity.getApplicationContext();

            dialog.show();
            Thread thread = new Thread(this);
            thread.start();
        }
    }

}
