package com.aio.founder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Locale;

/** Android compatibility membrane for ASR/TTS. AIO voice state remains in AioVoiceRoom. */
final class VoiceRuntime implements RecognitionListener, TextToSpeech.OnInitListener {
    interface Listener {
        void onVoiceText(String text, boolean liveMode);
        void onVoiceState(String state);
        void onVoiceError(String code);
    }
    static final int AUDIO_PERMISSION_REQUEST=7401;

    private final Activity activity;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady, liveMode, closed;

    VoiceRuntime(Activity activity,Listener listener){
        this.activity=activity;this.listener=listener;
        tts=new TextToSpeech(activity,this);
    }
    boolean hasAudioPermission(){
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    }
    void requestAudioPermission(){
        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_PERMISSION_REQUEST);
    }
    boolean onDeviceRecognizerAvailable(){
        return android.os.Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);
    }
    void startDictation(boolean live){
        if(closed)return;
        if(!hasAudioPermission()){requestAudioPermission();listener.onVoiceState("MIC_PERMISSION_REQUIRED");return;}
        stopRecognition();
        liveMode=live;
        recognizer=onDeviceRecognizerAvailable()?SpeechRecognizer.createOnDeviceSpeechRecognizer(activity):SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(this);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        recognizer.startListening(i);
        listener.onVoiceState(onDeviceRecognizerAvailable()?"LISTENING_ON_DEVICE":"LISTENING_BOOTSTRAP_RECOGNIZER");
    }
    void stopRecognition(){
        if(recognizer!=null){
            try{recognizer.stopListening();recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}
            recognizer=null;
        }
    }
    void speak(String text){
        if(closed||!ttsReady||text==null||text.isBlank())return;
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"aio-founder-"+System.nanoTime());
        listener.onVoiceState("SPEAKING");
    }
    void stopSpeech(){if(tts!=null)tts.stop();}
    void close(){
        closed=true;stopRecognition();
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
    }
    @Override public void onInit(int status){
        main.post(()->{
            if(closed)return;
            TextToSpeech current=tts;
            if(status!=TextToSpeech.SUCCESS||current==null){
                ttsReady=false;
                listener.onVoiceState("TTS_UNAVAILABLE");
                return;
            }
            int language;
            try{language=current.setLanguage(Locale.getDefault());}
            catch(Exception failure){language=TextToSpeech.LANG_NOT_SUPPORTED;}
            ttsReady=language!=TextToSpeech.LANG_MISSING_DATA&&language!=TextToSpeech.LANG_NOT_SUPPORTED;
            listener.onVoiceState(ttsReady?"VOICE_BOUNDARY_READY":"TTS_UNAVAILABLE");
        });
    }
    @Override public void onReadyForSpeech(android.os.Bundle params){listener.onVoiceState("LISTENING");}
    @Override public void onBeginningOfSpeech(){listener.onVoiceState("FOUNDER_SPEAKING");}
    @Override public void onRmsChanged(float rmsdB){}
    @Override public void onBufferReceived(byte[] buffer){}
    @Override public void onEndOfSpeech(){listener.onVoiceState("RECOGNIZING");}
    @Override public void onError(int error){listener.onVoiceError("ASR_"+error);}
    @Override public void onResults(android.os.Bundle results){
        ArrayList<String> rows=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(rows!=null&&!rows.isEmpty())listener.onVoiceText(rows.get(0),liveMode);
        listener.onVoiceState("IDLE");stopRecognition();
    }
    @Override public void onPartialResults(android.os.Bundle partialResults){
        ArrayList<String> rows=partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(rows!=null&&!rows.isEmpty())listener.onVoiceState("HEARING: "+rows.get(0));
    }
    @Override public void onEvent(int eventType,android.os.Bundle params){}
}
