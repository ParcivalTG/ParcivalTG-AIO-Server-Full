package com.aio.founder;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Temporary Founder integration candidate. PC effects use a typed encrypted membrane. */
public class MainActivity extends Activity {
    private static final int FILE_TREE_REQUEST=2401;
    private static final int SCREEN_PROJECTION_REQUEST=2402;
    private static final int UPDATE_APK_REQUEST=2403;
    private static final int NOTIFICATION_PERMISSION_REQUEST=2404;
    private static final int LOCAL_NETWORK_PERMISSION_REQUEST=2405;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService nodeIo = Executors.newSingleThreadExecutor();
    private final ConnectionEpoch epoch = new ConnectionEpoch();
    private final AioPresenceField presence = new AioPresenceField();
    private final AioVoiceRoom voiceRoom = new AioVoiceRoom();
    private AioAndroidRuntime androidRuntime;
    private AioAndroidNode androidNode;
    private final Object historyLock = new Object();
    private final Object authorityLock = new Object();
    private SecretStore secrets;
    private AndroidCapabilityBroker capabilityBroker;
    private AndroidCapabilityDispatcher capabilityDispatcher;
    private AioFounderStateStore stateStore;
    private ChatGptProviderRuntime chatGpt;
    private AndroidUpdateStager updateStager;
    private AioPersistentNodeService.LocalBinder persistentNodeBinder;
    private AndroidScreenProjectionService.LocalBinder screenProjectionBinder;
    private boolean persistentNodeBound,screenProjectionBound;
    private SharedPreferences configuration;
    private VoiceRuntime voiceRuntime;
    private final ServiceConnection persistentNodeConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){
            persistentNodeBinder=(AioPersistentNodeService.LocalBinder)service;
            persistentNodeBound=true;
            if(session==null)restorePersistentSession();
            else maybeAdoptPersistentLink();
            refreshViews();
        }
        @Override public void onServiceDisconnected(ComponentName name){
            persistentNodeBinder=null;
            persistentNodeBound=false;
            refreshViews();
        }
    };
    private final ServiceConnection screenProjectionConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){
            screenProjectionBinder=(AndroidScreenProjectionService.LocalBinder)service;
            screenProjectionBound=true;refreshViews();
        }
        @Override public void onServiceDisconnected(ComponentName name){
            screenProjectionBinder=null;screenProjectionBound=false;refreshViews();
        }
    };
    private volatile Session session;
    private volatile boolean destroyed, historyHeld;
    private boolean submitting, setupShown, priorUserStopObserved;
    private int surface;
    private LinearLayout body;
    private TextView stateView, historyView, fabricView, voiceStateView, nodeResourceView;
    private TextView chatGptStateView,chatGptStreamView;
    private Button connectButton, sendButton, micButton, liveVoiceButton,chatGptAuthButton;
    private EditText textView;
    private Spinner privacyView, voiceModeView,chatGptModelView;
    private String draftText = "",chatGptStreamingText="";
    private int draftPrivacy;
    private JSONArray history = new JSONArray(), evidence = new JSONArray();
    private String historyRepresentation, evidenceRepresentation;
    private String state = "DISCONNECTED", lastLocalEvent = "No network operation has run";
    private long lastStatusAt, lastRoundTripMs = -1;
    private volatile boolean peerIdentityVerified;
    private volatile boolean capabilityAuditHeld;
    private boolean liveVoiceMode;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        secrets = new SecretStore(this);
        androidRuntime = AioAndroidRuntime.get(this);
        androidNode = androidRuntime.node();
        capabilityBroker = androidRuntime.files();
        capabilityDispatcher = androidRuntime.dispatcher();
        stateStore = androidRuntime.stateStore();
        chatGpt = androidRuntime.chatGpt();
        updateStager = new AndroidUpdateStager(this);
        configuration = getSharedPreferences("aio_founder_connection_v1", MODE_PRIVATE);
        priorUserStopObserved=AioPersistentNodeController.reconcileUserRequestedStop(this);
        voiceRuntime = new VoiceRuntime(this,new VoiceRuntime.Listener() {
            @Override public void onVoiceText(String spoken,boolean live) {
                runOnUiThread(() -> {
                    draftText=spoken;
                    if(textView!=null)textView.setText(spoken);
                    AioVoiceRoom.Privacy p=selectedVoicePrivacy();
                    try{voiceRoom.commit("Founder",spoken,p);}catch(Exception failure){lastLocalEvent=safeCode(failure);}
                    boolean gptLive=voiceRoom.mode()==AioVoiceRoom.Mode.GPT&&chatGptReady()&&draftPrivacy>=2;
                    boolean aioLive=voiceRoom.mode()==AioVoiceRoom.Mode.AIO&&session!=null&&session.authenticated;
                    if(live&&(gptLive||aioLive)){liveVoiceMode=true;submitIntent();}
                    else if(live){lastLocalEvent="VOICE_READY | selected execution locus is not ready";}
                    refreshViews();
                });
            }
            @Override public void onVoiceState(String value) {
                runOnUiThread(() -> { if(voiceStateView!=null)voiceStateView.setText("Voice | "+value); });
            }
            @Override public void onVoiceError(String code) {
                runOnUiThread(() -> {
                    voiceRoom.stopListening();lastLocalEvent=code;
                    if(voiceStateView!=null)voiceStateView.setText("Voice | HOLD | "+code);
                    refreshViews();
                });
            }
        });
        try {
            stateStore.initialize();
            history=stateStore.historyTail(AioFounderStateStore.HOT_HISTORY_ROWS);
            evidence=stateStore.evidenceTail(AioFounderStateStore.HOT_EVIDENCE_ROWS);
            historyRepresentation=AioNativeCellStateCodec.encodeArray(history);
            evidenceRepresentation=AioNativeCellStateCodec.encodeArray(evidence);
        } catch (Exception failure) {
            historyHeld = true; state = "HISTORY_HOLD";
            lastLocalEvent = "AIO-native selective state could not be reconstructed; encrypted journals preserved for recovery";
        }
        if(priorUserStopObserved)
            lastLocalEvent="Android reported a prior user-requested Stop; persistent node remains disabled until Founder explicitly enables it";
        buildUi();
        io.scheduleWithFixedDelay(this::refreshPresence, 10, 10, TimeUnit.SECONDS);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(28,42,48));
        view.setPadding(dp(16),dp(9),dp(16),dp(9)); view.setTextIsSelectable(true); return view;
    }
    private Button button(String label) {
        Button value = new Button(this); value.setText(label); value.setAllCaps(false); return value;
    }
    private EditText input(String hint, String value, boolean multiline) {
        EditText view = new EditText(this); view.setHint(hint); view.setText(value);
        view.setSingleLine(!multiline); view.setPadding(dp(16),dp(10),dp(16),dp(10));
        if (multiline) { view.setMinLines(3); view.setGravity(Gravity.TOP); }
        body.addView(view); return view;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248,250,251));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView header = text("AIO for Android | Founder candidate",20);
        header.setTextColor(Color.WHITE); header.setBackgroundColor(Color.rgb(8,28,36)); root.addView(header);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBaselineAligned(false);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {"Dialogue","Live Fabric","Desktop Lens","Readiness"};
        for (int i=0;i<names.length;i++) {
            final int selected=i; Button tab=button(names[i]);
            tab.setOnClickListener(v -> { captureDraft(); surface=selected; showSurface(); });
            tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(56),1));
        }
        root.addView(tabs);
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(8),dp(8),dp(8),dp(24));
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); showSurface();
    }
    private void captureDraft() {
        if (!setupShown && surface==0 && textView!=null) {
            draftText=textView.getText().toString();
            draftPrivacy=privacyView.getSelectedItemPosition();
        }
    }
    private void clearViews() {
        body.removeAllViews(); stateView=null; historyView=null; fabricView=null; voiceStateView=null; nodeResourceView=null;
        chatGptStateView=null;chatGptStreamView=null;
        connectButton=null; sendButton=null; micButton=null; liveVoiceButton=null; chatGptAuthButton=null;textView=null;
        voiceModeView=null;privacyView=null;chatGptModelView=null;
    }
    private void showSurface() {
        setupShown=false; clearViews(); stateView=text("",14); body.addView(stateView);
        LinearLayout controls=new LinearLayout(this);
        connectButton=button(session==null?"Connect":"Disconnect");
        connectButton.setOnClickListener(v -> toggleConnection());
        controls.addView(connectButton,new LinearLayout.LayoutParams(0,dp(54),1));
        Button pairing=button("Pairing & authority");
        pairing.setOnClickListener(v -> { captureDraft(); showPairing(); });
        controls.addView(pairing,new LinearLayout.LayoutParams(0,dp(54),1)); body.addView(controls);
        if(surface==0)showDialogue();
        else if(surface==1)showFabric();
        else if(surface==2)showDesktop();
        else showReadiness();
        refreshViews();
    }
    private void showDialogue() {
        body.addView(text("AIO Dialogue Field",26));
        ChatGptProviderState.Snapshot gpt=chatGpt.snapshot();
        String locus=voiceRoom.mode()==AioVoiceRoom.Mode.AIO?"AIO / Windows Fabric":
            voiceRoom.mode()==AioVoiceRoom.Mode.GPT?"ChatGPT / OpenAI projection":"AIO <-> GPT supervisor";
        body.addView(text("Execution locus: "+locus+
            "\nDurable truth: encrypted AIO conversation/evidence journals"+
            "\nCompatibility projections materialize only the bounded state required by the selected locus.",14));

        voiceModeView=new Spinner(this);
        voiceModeView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"AIO Field","GPT Projection","AIO <-> GPT Supervisor | NOT_QUALIFIED"}));
        voiceModeView.setSelection(voiceRoom.mode()==AioVoiceRoom.Mode.AIO?0:
            voiceRoom.mode()==AioVoiceRoom.Mode.GPT?1:2);
        voiceModeView.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
                AioVoiceRoom.Mode next=position==0?AioVoiceRoom.Mode.AIO:
                    position==1?AioVoiceRoom.Mode.GPT:AioVoiceRoom.Mode.AIO_GPT_SUPERVISED;
                voiceRoom.selectMode(next);
                voiceRoom.grantExternalProjection(next==AioVoiceRoom.Mode.GPT&&chatGptReady());
                if(next==AioVoiceRoom.Mode.GPT&&!chatGptReady())
                    lastLocalEvent="GPT projection selected | Continue with ChatGPT required";
                else if(next==AioVoiceRoom.Mode.AIO_GPT_SUPERVISED)
                    lastLocalEvent="AIO_GPT_SUPERVISOR_NOT_QUALIFIED";
                refreshViews();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        body.addView(voiceModeView);

        chatGptStateView=text(chatGptSummary(gpt),13);body.addView(chatGptStateView);
        chatGptAuthButton=button(gpt.phase==ChatGptProviderState.Phase.READY?"Sign out ChatGPT":
            gpt.phase==ChatGptProviderState.Phase.SIGNING_IN?"ChatGPT authorization in progress":
            gpt.phase==ChatGptProviderState.Phase.SIGNED_IN_NO_PLAN?"Authorize ChatGPT plan use":
            "Continue with ChatGPT");
        chatGptAuthButton.setEnabled(gpt.phase!=ChatGptProviderState.Phase.SIGNING_IN);
        chatGptAuthButton.setOnClickListener(v -> {
            if(chatGpt.snapshot().phase==ChatGptProviderState.Phase.READY)signOutChatGpt();
            else signInChatGpt();
        });
        body.addView(chatGptAuthButton);

        if(!gpt.models.isEmpty()){
            ArrayList<String> names=new ArrayList<>();
            int selectedIndex=0;
            for(int i=0;i<gpt.models.size();i++){
                ChatGptResponsesContract.Model model=gpt.models.get(i);
                names.add(model.displayName+" | "+model.slug);
                if(model.slug.equals(gpt.selectedModel))selectedIndex=i;
            }
            chatGptModelView=new Spinner(this);
            chatGptModelView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            chatGptModelView.setSelection(selectedIndex);
            chatGptModelView.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
                    ChatGptProviderState.Snapshot current=chatGpt.snapshot();
                    if(position<0||position>=current.models.size())return;
                    String slug=current.models.get(position).slug;
                    if(!slug.equals(current.selectedModel))chatGpt.selectModel(slug,chatGptUiListener());
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            });
            body.addView(chatGptModelView);
        }

        body.addView(text("Privacy projection",16));
        privacyView=new Spinner(this);
        privacyView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"LOCAL_ONLY","TRADE_SECRET_LOCAL_ONLY","EXTERNAL_MINIMIZED | projection required","EXTERNAL_ALLOWED"}));
        privacyView.setSelection(Math.max(0,Math.min(3,draftPrivacy)));
        privacyView.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
                draftPrivacy=position;
                voiceRoom.grantExternalProjection(voiceRoom.mode()==AioVoiceRoom.Mode.GPT&&chatGptReady());
                refreshViews();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        body.addView(privacyView);
        body.addView(text("GPT receives nothing from LOCAL_ONLY or TRADE_SECRET_LOCAL_ONLY. EXTERNAL_MINIMIZED stays held until an explicit minimized projection exists.",12));

        textView=input("Message (up to 4096 characters)",draftText,true);
        LinearLayout voiceControls=new LinearLayout(this);
        micButton=button("Dictate");
        micButton.setOnClickListener(v -> {
            captureDraft();voiceRuntime.stopSpeech();voiceRoom.bargeIn();liveVoiceMode=false;
            voiceRuntime.startDictation(false);refreshViews();
        });
        voiceControls.addView(micButton,new LinearLayout.LayoutParams(0,dp(54),1));
        liveVoiceButton=button(liveVoiceMode?"End Voice":"Live Voice");
        liveVoiceButton.setOnClickListener(v -> {
            captureDraft();
            if(liveVoiceMode){
                liveVoiceMode=false;voiceRuntime.stopRecognition();voiceRuntime.stopSpeech();voiceRoom.stopListening();
                lastLocalEvent="Live Voice ended by Founder";
            }else{
                liveVoiceMode=true;voiceRuntime.stopSpeech();voiceRoom.bargeIn();voiceRuntime.startDictation(true);
                lastLocalEvent="Live Voice listening | "+locus;
            }
            refreshViews();
        });
        voiceControls.addView(liveVoiceButton,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(voiceControls);
        voiceStateView=text("Voice | "+voiceRoom.state()+" | on-device ASR="+
            (voiceRuntime.onDeviceRecognizerAvailable()?"AVAILABLE":"BOOTSTRAP_FALLBACK"),13);
        body.addView(voiceStateView);

        chatGptStreamView=text(chatGptStreamingText.isEmpty()?"Provider stream: DORMANT":"Provider stream:\n"+chatGptStreamingText,13);
        body.addView(chatGptStreamView);
        sendButton=button("Manifest & Send");sendButton.setOnClickListener(v -> submitIntent());body.addView(sendButton);
        body.addView(text("Encrypted AIO conversation continuity",18));historyView=text("",13);body.addView(historyView);
    }

    private String selectedPrivacyClass(){
        switch(draftPrivacy){
            case 0:return "LOCAL_ONLY";
            case 1:return "TRADE_SECRET_LOCAL_ONLY";
            case 2:return "EXTERNAL_MINIMIZED";
            case 3:return "EXTERNAL_ALLOWED";
            default:return "LOCAL_ONLY";
        }
    }

    private AioVoiceRoom.Privacy selectedVoicePrivacy(){
        switch(draftPrivacy){
            case 0:return AioVoiceRoom.Privacy.LOCAL_ONLY;
            case 1:return AioVoiceRoom.Privacy.TRADE_SECRET_LOCAL_ONLY;
            case 2:return AioVoiceRoom.Privacy.EXTERNAL_MINIMIZED;
            case 3:return AioVoiceRoom.Privacy.EXTERNAL_ALLOWED;
            default:return AioVoiceRoom.Privacy.LOCAL_ONLY;
        }
    }

    private boolean chatGptReady(){
        return chatGpt!=null&&chatGpt.snapshot().phase==ChatGptProviderState.Phase.READY;
    }

    private String chatGptSummary(ChatGptProviderState.Snapshot snapshot){
        return "CHATGPT PROVIDER"+
            "\nstate="+snapshot.phase+
            " | account="+(snapshot.accountLabel.isEmpty()?"NOT_AUTHORIZED":snapshot.accountLabel)+
            "\nmodel="+(snapshot.selectedModel.isEmpty()?"NOT_SELECTED":snapshot.selectedModel)+
            " | plan="+(snapshot.planUsageGranted?"AUTHORIZED":"NOT_AUTHORIZED")+
            "\nlast="+snapshot.lastCode;
    }

    private ChatGptProviderRuntime.Listener chatGptUiListener(){
        return new ChatGptProviderRuntime.Listener(){
            @Override public void onState(ChatGptProviderState.Snapshot snapshot){
                runOnUiThread(()->{
                    lastLocalEvent=snapshot.lastCode;
                    voiceRoom.grantExternalProjection(
                        voiceRoom.mode()==AioVoiceRoom.Mode.GPT&&snapshot.phase==ChatGptProviderState.Phase.READY);
                    if(surface==0&&!destroyed)showSurface();else refreshViews();
                });
            }
            @Override public void onFailure(String code){
                runOnUiThread(()->{lastLocalEvent=code;refreshViews();});
            }
        };
    }

    private void signInChatGpt(){
        lastLocalEvent="Opening system browser for ChatGPT authorization";
        chatGpt.signIn(chatGptUiListener());refreshViews();
    }

    private void signOutChatGpt(){
        lastLocalEvent="Ending renewable ChatGPT session";
        chatGpt.signOut(chatGptUiListener());refreshViews();
    }

    private void showFabric() {
        body.addView(text("Live Fabric",26));
        body.addView(text("Presence discovery refreshes every 10 seconds while connected. Discovery is separate from the pinned authenticated encrypted receipt.",14));
        fabricView=text("",14);body.addView(fabricView);
        nodeResourceView=text("",14);body.addView(nodeResourceView);
        boolean persistentEnabled=AioPersistentNodeController.founderEnabled(this);
        AioPersistentNodeService.Snapshot persistentSnapshot=persistentNodeBinder==null?null:persistentNodeBinder.snapshot();
        body.addView(text("PERSISTENT ANDROID NODE\nFounder preference: "+(persistentEnabled?"ENABLED":"DISABLED")+
            "\nRuntime: "+(persistentSnapshot==null?(persistentEnabled?"STARTING_OR_UNBOUND":"STOPPED"):persistentSnapshot.state)+
            "\nCloud-link ownership: "+((session!=null&&session.persistentOwned)?"FOREGROUND_SERVICE":"ACTIVITY_OR_NONE"),14));
        Button persistence=button(persistentEnabled?"Disable persistent node":"Enable persistent node");
        persistence.setEnabled(persistentEnabled||cloudConfigured());
        persistence.setOnClickListener(v -> {
            if(AioPersistentNodeController.founderEnabled(this)){
                Session selected=session;
                boolean cleanReturn=true;
                if(selected!=null&&selected.persistentOwned&&persistentNodeBinder!=null){
                    boolean returned=persistentNodeBinder.returnCloudLinkToActivity(
                        selected.transport instanceof CloudPresenceTransport?(CloudPresenceTransport)selected.transport:null);
                    if(returned){
                        selected.persistentOwned=false;
                        if(selected.transport instanceof CloudPresenceTransport)
                            startAndroidNodeService(selected,(CloudPresenceTransport)selected.transport);
                    }else{
                        cleanReturn=false;
                        epoch.invalidate();session=null;selected.persistentOwned=false;peerIdentityVerified=false;
                        state="DISCONNECTED";
                    }
                }
                AioPersistentNodeController.stop(this);
                lastLocalEvent=cleanReturn?
                    "Persistent Android node disabled by Founder; live link returned to Activity":
                    "Persistent Android node disabled; stale Activity link dropped, reconnect explicitly";
            }else{
                if(!cloudConfigured()){notice("Persistent node held: CLOUD_FALLBACK_NOT_CONFIGURED");return;}
                requestNodeNotificationPermissionIfNeeded();
                AioPersistentNodeController.startFromVisibleFounder(this);
                bindPersistentNode();
                lastLocalEvent="Persistent Android node enabled; cloud link will transfer when available";
            }
            showSurface();
        });
        body.addView(persistence);
        AndroidBackgroundReadiness.Snapshot background=AndroidBackgroundReadiness.capture(this);
        body.addView(text(background.summary(),14));
        Button backgroundSettings=button(background.samsung?
            "Open Galaxy Never sleeping apps":"Open battery optimization settings");
        backgroundSettings.setOnClickListener(v -> {
            try{AndroidBackgroundSettingsController.open(this);}
            catch(Exception failure){notice("Background settings held: "+safeCode(failure));}
        });
        body.addView(backgroundSettings);
        body.addView(text("Objective graph, Codex/workers, selective causal state: NOT_CONNECTED. Resource measurements below are local Android facts; bounded resource contribution is separately Founder-granted and re-admitted for every task.",14));
        Button unavailable=button("Expand objective causal cone | NOT_CONNECTED");
        unavailable.setEnabled(false);body.addView(unavailable);
        AndroidResourceProjection.Snapshot resourceSnapshot=null;
        try{resourceSnapshot=AndroidResourceProjection.capture(this);}catch(Exception ignored){}
        boolean resourceEligible=resourceSnapshot!=null&&
            "ELIGIBLE_LOCAL_ONLY".equals(resourceSnapshot.contributionState)&&remoteGrantReady();
        Button grantCompute=button(resourceEligible?
            "Grant bounded Android compute - 15 min":
            "Bounded Android compute | HOLD");
        grantCompute.setEnabled(resourceEligible);
        grantCompute.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.RESOURCE_CONTRIBUTE,15*60_000L));
        body.addView(grantCompute);
        body.addView(text("Remote compute kernels are fixed to SHA-256 and DEFLATE only; decoded input <= 6 KiB, output <= 16 KiB, no arbitrary code or shell.",13));
        body.addView(text("Local evidence | witness-collapsed projection; raw encrypted evidence remains preserved",16));
        synchronized(historyLock){body.addView(text(AioEvidenceCollapse.project(evidence,16),12));}
        body.addView(text(AioNativeRuntimeTelemetry.snapshot().summary(),12));
        body.addView(text(AioNativeRepresentationAtlas.summary(),11));
    }
    private String nativeStateSummary(){
        try{
            return stateStore.summary()+
                "\nHot history: "+AioNativeCellStateCodec.describeStored(historyRepresentation)+
                "\nHot evidence: "+AioNativeCellStateCodec.describeStored(evidenceRepresentation)+
                "\nJSON/Android is a compatibility projection; durable truth is selectively reconstructed from encrypted AIO journals.";
        }catch(Exception failure){
            return "AIO NATIVE SELECTIVE STATE\nHOLD: "+safeCode(failure);
        }
    }

    private AndroidFounderReadiness.Report readinessReport(){
        boolean pairingMaterial=false;
        try{
            String client=configuration.getString("client","").trim();
            String pin=configuration.getString("pin","").trim();
            pairingMaterial=secrets.hasSecret()&&pairingGenerationMatches()&&
                client.matches("[A-Za-z0-9_.-]{1,128}")&&!pin.isEmpty();
            if(pairingMaterial)E2eCodec.pinnedKey(pin);
        }catch(Exception ignored){pairingMaterial=false;}

        String destination=configuration.getString("endpoint","").trim();
        boolean routeConfigured=!destination.isEmpty()||cloudConfigured();
        boolean directPermissionReady=true;
        if(!destination.isEmpty()){
            try{
                Endpoint endpoint=Endpoint.parse(destination);
                directPermissionReady=hasDirectLocalPermission(endpoint.host)||cloudConfigured();
            }catch(Exception failure){directPermissionReady=false;}
        }
        boolean notificationsReady=android.os.Build.VERSION.SDK_INT<33||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==
                android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean filesGranted=capabilityBroker!=null&&capabilityBroker.hasStorageGrant();
        AndroidScreenProjectionService.Snapshot screen=screenProjectionBinder==null?null:screenProjectionBinder.snapshot();
        boolean screenActive=screen!=null&&"ACTIVE".equals(screen.state);
        Session selected=session;
        boolean presenceReady=selected!=null&&selected.authenticated&&current(selected);

        return AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            AndroidSigningIdentity.matchesExpectedRelease(this),
            pairingMaterial,
            routeConfigured,
            directPermissionReady,
            AioPersistentNodeController.founderEnabled(this),
            notificationsReady,
            filesGranted,
            screenActive,
            AndroidGestureController.available(),
            AioAppVisibility.isForegroundVisible(),
            presenceReady,
            presenceReady&&peerIdentityVerified
        ));
    }

    private void showReadiness(){
        body.addView(text("Readiness",26));
        AndroidFounderReadiness.Report report=readinessReport();
        body.addView(text(report.summary(),15));
        body.addView(text(
            "Core readiness is intentionally separate from optional device capabilities. "+
            "A successful install does not imply pairing, and a Presence connection does not imply pinned Windows peer authority.",13));
        body.addView(text(nativeStateSummary(),13));

        LinearLayout actions=new LinearLayout(this);
        Button pairing=button("Pairing & authority");
        pairing.setOnClickListener(v -> showPairing());
        actions.addView(pairing,new LinearLayout.LayoutParams(0,dp(54),1));
        Button capabilities=button("Android capabilities");
        capabilities.setOnClickListener(v -> {surface=2;showSurface();});
        actions.addView(capabilities,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(actions);

        Button refresh=button("Refresh readiness");
        refresh.setOnClickListener(v -> showSurface());
        body.addView(refresh);

        body.addView(text("PRE-INSTALL / PHYSICAL COURTS",18));
        body.addView(text(
            "GitHub release signing: "+(AndroidSigningIdentity.matchesExpectedRelease(this)?
                "EXPECTED SIGNER VERIFIED":"NOT VERIFIED / DEVELOPMENT BUILD")+
            "\nS25 Ultra install: physical court required"+
            "\nAndroid -> R5 -> Windows natural Dialogue: physical court required"+
            "\nWindows -> R5 -> Android typed capabilities: physical court required"+
            "\nSamsung/One UI background and permission behavior: physical court required",13));
    }

    private void showDesktop() {
        body.addView(text("Desktop Lens",26));
        body.addView(text("NOT_CONNECTED\nWindows screen, pointer, keyboard, clipboard, and power projections are not yet physically bound to this Android endpoint.",16));
        for(String label:new String[]{"Observe Windows desktop","Windows pointer / keyboard","Windows clipboard","Windows power controls"}) {
            Button unavailable=button(label+" | NOT_CONNECTED");unavailable.setEnabled(false);body.addView(unavailable);
        }

        AioAndroidNode.Manifest manifest=AioAndroidNode.manifest("android-founder-local");
        AndroidUpdateStager.StageResult stagedUpdate=updateStager.peek();
        boolean notificationsReady=android.os.Build.VERSION.SDK_INT<33||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        body.addView(text("Android AIO Node",22));
        AndroidScreenProjectionService.Snapshot screen=screenProjectionBinder==null?null:screenProjectionBinder.snapshot();
        body.addView(text("ANDROID SCREEN OBSERVATION\n"+
            "Local projection: "+(screen==null?"NOT_MEASURED":screen.state)+
            (screen!=null&&screen.width>0?" | "+screen.width+"x"+screen.height+" @ "+screen.densityDpi+"dpi":"")+
            "\nRemote screen authority: NOT_GRANTED",14));
        LinearLayout screenControls=new LinearLayout(this);
        Button startScreen=button("Start Android screen observation");
        startScreen.setOnClickListener(v -> {
            try{startActivityForResult(AndroidScreenProjectionController.consentIntent(this),SCREEN_PROJECTION_REQUEST);}
            catch(Exception failure){notice("Screen observation held: "+safeCode(failure));}
        });
        screenControls.addView(startScreen,new LinearLayout.LayoutParams(0,dp(54),1));
        Button stopScreen=button("Stop screen observation");
        stopScreen.setOnClickListener(v -> {
            AndroidScreenProjectionController.stop(this);
            lastLocalEvent="Android screen observation stopped by Founder";
            recordEvidence("ANDROID_SCREEN_PROJECTION_STOPPED",null,-1);
            refreshViews();
        });
        screenControls.addView(stopScreen,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(screenControls);

        body.addView(text("Node version: "+manifest.version+
            "\nTyped capability definitions: "+manifest.capabilities+
            "\nResource envelope: maxConcurrent="+manifest.envelope.maxConcurrent+
            ", maxBytes="+manifest.envelope.maxBytes+
            ", chargingOnly="+manifest.envelope.chargingOnly+
            ", thermalPolicy="+manifest.envelope.thermalPolicy,14));

        boolean filesGranted=capabilityBroker!=null&&capabilityBroker.hasStorageGrant();
        String localCapabilityState=
            "RESOURCE_STATUS: LOCAL_PLATFORM_READY / REMOTE_GRANT_NOT_ISSUED"+
            "\nFILE_READ/WRITE: "+(filesGranted?"LOCAL_PLATFORM_GRANTED / REMOTE_GRANT_NOT_ISSUED":"FOUNDER_PLATFORM_GRANT_REQUIRED")+
            "\nCLIPBOARD: "+(AioAppVisibility.isForegroundVisible()?
                "FOREGROUND_PLATFORM_READY / REMOTE_GRANT_NOT_ISSUED":"FOREGROUND_VISIBILITY_REQUIRED")+
            "\nNOTIFICATIONS: "+(notificationsReady?
                "LOCAL_PLATFORM_READY / REMOTE_GRANT_NOT_ISSUED":"POST_NOTIFICATIONS_PERMISSION_REQUIRED")+
            "\nSCREEN_OBSERVE: "+(screen!=null&&"ACTIVE".equals(screen.state)?
                "LOCAL_PLATFORM_GRANTED / REMOTE_GRANT_NOT_ISSUED":"FOUNDER_MEDIA_PROJECTION_CONSENT_REQUIRED")+
            "\nGESTURE_INPUT: "+(AndroidGestureController.available()?
                "LOCAL_PLATFORM_GRANTED / REMOTE_GRANT_NOT_ISSUED":"FOUNDER_ACCESSIBILITY_ENABLE_REQUIRED")+
            "\nPACKAGE_STAGE: "+(stagedUpdate==null?
                "FOUNDER_LOCAL_STAGE_REQUIRED / REMOTE_INSTALL_NOT_EXPOSED":
                "SIGNED_SELF_UPDATE_STAGED / LOCAL_FOUNDER_INSTALL_REQUIRED")+
            "\nRemote typed grants issued this runtime: "+androidNode.receipts().size();
        body.addView(text(localCapabilityState,14));

        LinearLayout fileControls=new LinearLayout(this);
        Button grantFiles=button(filesGranted?"Change file-tree grant":"Grant file-tree access");
        grantFiles.setOnClickListener(v -> {
            Intent request=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(request,FILE_TREE_REQUEST);
        });
        fileControls.addView(grantFiles,new LinearLayout.LayoutParams(0,dp(54),1));
        Button revokeFiles=button("Revoke file-tree grant");
        revokeFiles.setEnabled(filesGranted);
        revokeFiles.setOnClickListener(v -> {
            capabilityBroker.clearGrant();
            recordEvidence("ANDROID_FILE_TREE_GRANT_REVOKED",null,-1);
            lastLocalEvent="Android file-tree grant revoked; remote file capabilities remain denied";
            showSurface();
        });
        fileControls.addView(revokeFiles,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(fileControls);
        body.addView(text("File access is restricted to the Founder-selected Android document tree through Storage Access Framework. No raw filesystem path or arbitrary shell is exposed.",13));

        body.addView(text("GOVERNED AIO SELF-UPDATE\n"+
            (stagedUpdate==null?"No update APK staged.":
                "Staged: "+stagedUpdate.summary())+
            "\nInstall status: "+AndroidUpdateStatusActivity.statusSummary(this)+
            "\nOnly a newer APK for this exact package and current signer is accepted. Install always requires Founder biometric approval plus Android system confirmation.",13));
        LinearLayout updateControls=new LinearLayout(this);
        Button selectUpdate=button("Select AIO update APK");
        selectUpdate.setOnClickListener(v -> {
            Intent request=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            request.addCategory(Intent.CATEGORY_OPENABLE);
            request.setType("application/vnd.android.package-archive");
            startActivityForResult(request,UPDATE_APK_REQUEST);
        });
        updateControls.addView(selectUpdate,new LinearLayout.LayoutParams(0,dp(54),1));
        Button installUpdate=button("Install staged AIO update");
        installUpdate.setEnabled(stagedUpdate!=null);
        installUpdate.setOnClickListener(v -> installStagedUpdate());
        updateControls.addView(installUpdate,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(updateControls);
        Button clearUpdate=button("Clear staged update");
        clearUpdate.setEnabled(stagedUpdate!=null);
        clearUpdate.setOnClickListener(v -> {
            updateStager.clear();
            recordEvidence("ANDROID_UPDATE_STAGE_CLEARED",null,-1);
            lastLocalEvent="Staged AIO update cleared";
            showSurface();
        });
        body.addView(clearUpdate);

        Session selected=session;
        String remotePeer=configuration.getString("cloud_target","").trim();
        boolean remoteGrantReady=selected!=null&&selected.authenticated&&peerIdentityVerified&&
            "R5_CLOUD".equals(selected.route)&&remotePeer.matches("[A-Za-z0-9_.:-]{1,128}");
        int activeGrants=remotePeer.matches("[A-Za-z0-9_.:-]{1,128}")?androidNode.activeGrantCount(remotePeer):0;
        body.addView(text("REMOTE ANDROID AUTHORITY\n"+
            (remoteGrantReady?"READY_FOR_FOUNDER_GRANT":"HOLD - requires authenticated R5 session and pinned E2E gateway response")+
            "\nActive endpoint-local grants for configured Windows peer: "+activeGrants+
            "\nGrant identifiers never leave Android.",14));

        LinearLayout grantRow=new LinearLayout(this);
        Button grantResources=button("Grant resource status - 15 min");
        grantResources.setEnabled(remoteGrantReady);
        grantResources.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.RESOURCE_STATUS,15*60_000L));
        grantRow.addView(grantResources,new LinearLayout.LayoutParams(0,dp(54),1));
        Button grantRead=button("Grant file read - 15 min");
        grantRead.setEnabled(remoteGrantReady&&filesGranted);
        grantRead.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.FILE_READ,15*60_000L));
        grantRow.addView(grantRead,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(grantRow);

        LinearLayout screenGrantRow=new LinearLayout(this);
        Button grantScreen=button("Grant screen observe - 5 min");
        grantScreen.setEnabled(remoteGrantReady&&screen!=null&&"ACTIVE".equals(screen.state));
        grantScreen.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.SCREEN_OBSERVE,5*60_000L));
        screenGrantRow.addView(grantScreen,new LinearLayout.LayoutParams(0,dp(54),1));
        Button screenStatus=button(screen!=null&&"ACTIVE".equals(screen.state)?"Screen consent active":"Screen consent required");
        screenStatus.setEnabled(false);
        screenGrantRow.addView(screenStatus,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(screenGrantRow);

        LinearLayout clipboardGrantRow=new LinearLayout(this);
        Button grantClipboard=button("Grant clipboard - 5 min");
        grantClipboard.setEnabled(remoteGrantReady&&AioAppVisibility.isForegroundVisible());
        grantClipboard.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.CLIPBOARD,5*60_000L));
        clipboardGrantRow.addView(grantClipboard,new LinearLayout.LayoutParams(0,dp(54),1));
        Button clipboardState=button("Foreground-visible only");
        clipboardState.setEnabled(false);
        clipboardGrantRow.addView(clipboardState,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(clipboardGrantRow);

        LinearLayout notificationGrantRow=new LinearLayout(this);
        Button grantNotifications=button("Grant AIO notifications - 15 min");
        grantNotifications.setEnabled(remoteGrantReady&&notificationsReady);
        grantNotifications.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.NOTIFICATIONS,15*60_000L));
        notificationGrantRow.addView(grantNotifications,new LinearLayout.LayoutParams(0,dp(54),1));
        Button notificationPermission=button(notificationsReady?"Notification permission ready":"Enable notifications");
        notificationPermission.setEnabled(!notificationsReady);
        notificationPermission.setOnClickListener(v -> requestNodeNotificationPermissionIfNeeded());
        notificationGrantRow.addView(notificationPermission,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(notificationGrantRow);

        LinearLayout gestureRow=new LinearLayout(this);
        Button gestureSettings=button(AndroidGestureController.available()?"Gesture service enabled":"Enable gesture service");
        gestureSettings.setOnClickListener(v -> AndroidGestureController.openAccessibilitySettings(this));
        gestureRow.addView(gestureSettings,new LinearLayout.LayoutParams(0,dp(54),1));
        Button grantGesture=button("Grant gesture input - 5 min");
        grantGesture.setEnabled(remoteGrantReady&&AndroidGestureController.available());
        grantGesture.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.GESTURE_INPUT,5*60_000L));
        gestureRow.addView(grantGesture,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(gestureRow);
        body.addView(text("Gesture service is limited to bounded tap/swipe dispatch. It does not retrieve Android window content or enable itself.",13));

        LinearLayout sensitiveRow=new LinearLayout(this);
        Button grantModify=button("Grant file rename/delete - 5 min");
        grantModify.setEnabled(remoteGrantReady&&filesGranted);
        grantModify.setOnClickListener(v -> issueAndroidGrant(AioAndroidNode.Capability.FILE_WRITE,5*60_000L));
        sensitiveRow.addView(grantModify,new LinearLayout.LayoutParams(0,dp(54),1));
        Button revokeRemote=button("Revoke Android remote grants");
        revokeRemote.setEnabled(activeGrants>0);
        revokeRemote.setOnClickListener(v -> revokeAndroidGrants());
        sensitiveRow.addView(revokeRemote,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(sensitiveRow);
    }
    private void installStagedUpdate(){
        if(updateStager.peek()==null){notice("Update held: UPDATE_NOT_STAGED");return;}
        AioBiometricGate.authenticate(this,"Authorize AIO self-update",new AioBiometricGate.Callback(){
            @Override public void onAccepted(){
                lastLocalEvent="Verifying staged AIO update before Android installer handoff";
                refreshViews();
                io.execute(()->{
                    try{
                        AndroidUpdateStager.StageResult verified=updateStager.verifyPending();
                        runOnUiThread(()->{
                            try{
                                String result=AndroidUpdateInstaller.requestInstall(MainActivity.this,updateStager,verified);
                                recordEvidence("ANDROID_UPDATE_"+result.replace(':','_'),null,-1);
                                lastLocalEvent=result.equals("INSTALL_SOURCE_PERMISSION_REQUIRED")?
                                    "Android requires Install unknown apps approval for AIO; approve it, then tap Install staged AIO update again":
                                    "Signed self-update committed to Android PackageInstaller; system confirmation remains authoritative";
                                refreshViews();
                            }catch(Exception failure){notice("Update held: "+safeCode(failure));}
                        });
                    }catch(Exception failure){
                        runOnUiThread(()->notice("Update verification held: "+safeCode(failure)));
                    }
                });
            }
            @Override public void onRejected(String reason){lastLocalEvent=reason;refreshViews();}
        });
    }

    private boolean remoteGrantReady(){
        Session selected=session;
        String peer=configuration.getString("cloud_target","").trim();
        return selected!=null&&selected.authenticated&&peerIdentityVerified&&
            "R5_CLOUD".equals(selected.route)&&peer.matches("[A-Za-z0-9_.:-]{1,128}");
    }
    private void issueAndroidGrant(AioAndroidNode.Capability capability,long ttlMs){
        if(!remoteGrantReady()){notice("Grant held: REMOTE_PEER_NOT_VERIFIED");return;}
        AndroidCapabilityCatalog.Spec spec;
        try{
            spec=AndroidCapabilityCatalog.spec(capability);
            AndroidCapabilityCatalog.validateGrant(capability,ttlMs);
        }catch(Exception failure){notice("Grant held: "+safeCode(failure));return;}
        String peer=configuration.getString("cloud_target","").trim();
        Runnable issue=()->{
            try{
                androidNode.grant(peer,capability,spec.minimumTier,AioAndroidNode.Privacy.FOUNDER_ONLY,ttlMs);
                recordEvidence("ANDROID_GRANT_"+capability.name(),null,-1);
                lastLocalEvent="Founder granted "+capability+" to the verified Windows peer for "+(ttlMs/60_000L)+" minutes";
                showSurface();
            }catch(Exception failure){notice("Grant held: "+safeCode(failure));}
        };
        if(!spec.biometricRecommended){issue.run();return;}
        AioBiometricGate.authenticate(this,"Authorize "+capability.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '),
            new AioBiometricGate.Callback(){
                @Override public void onAccepted(){issue.run();}
                @Override public void onRejected(String reason){lastLocalEvent=reason;refreshViews();}
            });
    }
    private void revokeAndroidGrants(){
        String peer=configuration.getString("cloud_target","").trim();
        if(!peer.matches("[A-Za-z0-9_.:-]{1,128}")){notice("Revoke held: CLOUD_PEER_ID_INVALID");return;}
        for(AioAndroidNode.Capability capability:new AioAndroidNode.Capability[]{
            AioAndroidNode.Capability.RESOURCE_STATUS,AioAndroidNode.Capability.FILE_READ,
            AioAndroidNode.Capability.FILE_WRITE,AioAndroidNode.Capability.SCREEN_OBSERVE,
            AioAndroidNode.Capability.GESTURE_INPUT,AioAndroidNode.Capability.CLIPBOARD,
            AioAndroidNode.Capability.NOTIFICATIONS,AioAndroidNode.Capability.RESOURCE_CONTRIBUTE})
            androidNode.revokePeerCapability(peer,capability);
        recordEvidence("ANDROID_REMOTE_GRANTS_REVOKED",null,-1);
        lastLocalEvent="Android remote grants revoked for configured Windows peer";
        showSurface();
    }

    private void showPairing() {
        setupShown=true;clearViews();body.addView(text("Offline pairing & authority",24));
        body.addView(text("Import direct Presence plus optional R5 fallback, client ID, witness, pinned gateway P256 public key, and signed lease from a trusted Windows pairing handoff. Direct Presence is always preferred; cloud fallback is used only when the direct socket cannot be opened. HELLO/authentication failures never downgrade to cloud.",14));
        body.addView(text("FAST PAIRING PACKAGE",18));
        EditText pairingPackage=input("Paste one-time Windows pairing package JSON", "", true);
        pairingPackage.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button importPackage=button("Import pairing package");
        importPackage.setOnClickListener(v -> {
            try{
                FounderPairingBundle bundle=FounderPairingBundle.parse(
                    pairingPackage.getText().toString(),System.currentTimeMillis());
                applyPairingBundle(bundle);
                pairingPackage.setText("");
                lastLocalEvent="Pairing package imported atomically; secrets are Keystore wrapped";
                recordEvidence("PAIRING_PACKAGE_IMPORTED",null,-1);
                showSurface();
            }catch(Exception failure){notice("Pairing package held: "+safeCode(failure));}
        });
        body.addView(importPackage);
        body.addView(text("The package is bounded and expires quickly. It may contain endpoint-specific R5 keys, but never the R5 master secret. Manual fields below remain a recovery path.",13));
        EditText endpoint=input("Direct Presence host:port or [IPv6]:port (optional when cloud is configured)",configuration.getString("endpoint",""),false);
        Button localNetworkPermission=button("Enable direct LAN permission when required");
        localNetworkPermission.setOnClickListener(v -> {
            try{
                String destination=endpoint.getText().toString().trim();
                if(destination.isEmpty()){notice("Direct LAN permission held: ENDPOINT_REQUIRED");return;}
                Endpoint parsed=Endpoint.parse(destination);
                if(!AndroidLocalNetworkPolicy.needsRuntimePermission(android.os.Build.VERSION.SDK_INT,parsed.host)){
                    notice("Direct endpoint does not require Android local-network permission");
                    return;
                }
                if(hasDirectLocalPermission(parsed.host)){
                    notice("Direct local-network permission already granted");
                    return;
                }
                requestDirectLocalPermission(parsed.host);
            }catch(Exception failure){notice("Direct LAN permission held: "+safeCode(failure));}
        });
        body.addView(localNetworkPermission);
        EditText client=input("Presence client ID",configuration.getString("client",""),false);
        EditText pin=input("Pinned gateway SPKI | canonical base64url",configuration.getString("pin",""),true);
        EditText witness=input("Witness secret | leave empty to retain saved value","",false);
        witness.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText lease=input("Signed lease JSON | leave empty to retain saved lease","",true);
        lease.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(text("R5 cloud fallback | optional; keys are Android Keystore wrapped",18));
        EditText cloudWss=input("R5 rendezvous WSS URL",configuration.getString("cloud_wss",""),false);
        EditText cloudPeer=input("Android cloud peer ID",configuration.getString("cloud_peer",""),false);
        EditText cloudTarget=input("Windows cloud peer ID",configuration.getString("cloud_target",""),false);
        EditText cloudAdmission=input("Peer admission key (base64, 32 bytes) | leave empty to retain","",false);
        cloudAdmission.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText cloudTunnel=input("Tunnel key (base64, 32 bytes) | leave empty to retain","",false);
        cloudTunnel.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button save=button("Save trusted pairing material");
        save.setOnClickListener(v -> {
            try {
                String destination=endpoint.getText().toString().trim();
                if(!destination.isEmpty())Endpoint.parse(destination);
                String clientId=client.getText().toString().trim();
                if(!clientId.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("CLIENT_ID_INVALID");
                String pinned=pin.getText().toString().trim();E2eCodec.pinnedKey(pinned);
                String witnessText=witness.getText().toString().trim(),leaseText=lease.getText().toString().trim();
                if(!leaseText.isEmpty())AioProjectionMembrane.validateLeaseImport(leaseText,System.currentTimeMillis());
                if(witnessText.isEmpty()&&!secrets.hasSecret())throw new SecurityException("WITNESS_REQUIRED");

                String wss=cloudWss.getText().toString().trim();
                String cloudPeerId=cloudPeer.getText().toString().trim();
                String cloudTargetId=cloudTarget.getText().toString().trim();
                String admissionText=cloudAdmission.getText().toString().trim();
                String tunnelText=cloudTunnel.getText().toString().trim();
                boolean cloudRequested=!wss.isEmpty()||!cloudPeerId.isEmpty()||!cloudTargetId.isEmpty()||!admissionText.isEmpty()||!tunnelText.isEmpty();
                if(cloudRequested){
                    if(!wss.startsWith("wss://")||wss.length()>512)throw new SecurityException("CLOUD_WSS_REQUIRED");
                    if(!cloudPeerId.matches("[A-Za-z0-9_.:-]{1,128}")||!cloudTargetId.matches("[A-Za-z0-9_.:-]{1,128}"))
                        throw new SecurityException("CLOUD_PEER_ID_INVALID");
                    if(admissionText.isEmpty()&&!secrets.hasText("cloud_admission"))throw new SecurityException("CLOUD_ADMISSION_REQUIRED");
                    if(tunnelText.isEmpty()&&!secrets.hasText("cloud_tunnel"))throw new SecurityException("CLOUD_TUNNEL_KEY_REQUIRED");
                    if(!admissionText.isEmpty())validateCloudKey(admissionText,"CLOUD_ADMISSION_KEY_INVALID");
                    if(!tunnelText.isEmpty())validateCloudKey(tunnelText,"CLOUD_TUNNEL_KEY_INVALID");
                }
                if(destination.isEmpty()&&!cloudRequested)throw new SecurityException("PRESENCE_ROUTE_REQUIRED");

                boolean cloudIdentityChanged=cloudRequested&&(
                    !wss.equals(configuration.getString("cloud_wss",""))||
                    !cloudPeerId.equals(configuration.getString("cloud_peer",""))||
                    !cloudTargetId.equals(configuration.getString("cloud_target","")));
                if(cloudIdentityChanged&&(admissionText.isEmpty()||tunnelText.isEmpty()))
                    throw new SecurityException("CLOUD_KEYS_REQUIRED_FOR_IDENTITY_CHANGE");
                boolean principalChanged=!clientId.equals(configuration.getString("client",""))||
                    !pinned.equals(configuration.getString("pin",""));
                if(principalChanged&&(witnessText.isEmpty()||leaseText.isEmpty()))
                    throw new SecurityException("WITNESS_AND_LEASE_REQUIRED_FOR_IDENTITY_CHANGE");

                disconnect("PAIRING_CONFIGURATION_CHANGED");
                String generation=UUID.randomUUID().toString();
                secrets.savePairingUpdate(
                    witnessText.isEmpty()?null:witnessText,
                    leaseText.isEmpty()?null:leaseText,
                    admissionText.isEmpty()?null:admissionText,
                    tunnelText.isEmpty()?null:tunnelText,
                    !cloudRequested,
                    generation);
                if(!configuration.edit()
                    .putString("endpoint",destination).putString("client",clientId).putString("pin",pinned)
                    .putString("cloud_wss",cloudRequested?wss:"")
                    .putString("cloud_peer",cloudRequested?cloudPeerId:"")
                    .putString("cloud_target",cloudRequested?cloudTargetId:"")
                    .putString("pairing_generation",generation).commit())
                    throw new IOException("CONFIGURATION_COMMIT_FAILED");
                witness.setText("");lease.setText("");cloudAdmission.setText("");cloudTunnel.setText("");
                recordEvidence("PAIRING_IMPORTED",null,-1);
                lastLocalEvent="Pairing saved locally; direct Presence preferred, R5 available only as socket-connect fallback";showSurface();
            }catch(Exception failure){notice("Pairing held: "+safeCode(failure));}
        });body.addView(save);
        Button clear=button("Clear pairing & disconnect");
        clear.setOnClickListener(v -> {
            disconnect("PAIRING_CLEARED");secrets.clear();configuration.edit().clear().commit();
            witness.setText("");lease.setText("");pin.setText("");endpoint.setText("");client.setText("");
            cloudWss.setText("");cloudPeer.setText("");cloudTarget.setText("");cloudAdmission.setText("");cloudTunnel.setText("");
            recordEvidence("PAIRING_CLEARED",null,-1);lastLocalEvent="Pairing cleared; conversation evidence retained";showSurface();
        });body.addView(clear);
        Button back=button("Back");back.setOnClickListener(v -> showSurface());body.addView(back);
        body.addView(text("Witness, lease, cloud admission key, and tunnel key are Keystore encrypted at rest. Provider sign-in and biometric authority unlock: CONFIG_REQUIRED. Clearing pairing cancels local work; already admitted PC proposals remain in PC receipts.",13));
    }
    private void applyPairingBundle(FounderPairingBundle bundle)throws Exception{
        if(bundle==null)throw new IllegalArgumentException("PAIRING_BUNDLE_REQUIRED");
        if(!bundle.directEndpoint.isEmpty())Endpoint.parse(bundle.directEndpoint);
        String generation=UUID.randomUUID().toString();
        disconnect("PAIRING_CONFIGURATION_CHANGED");
        secrets.savePairingUpdate(
            bundle.witnessSecretB64,
            bundle.signedLeaseJson,
            bundle.cloud==null?null:bundle.cloud.peerAdmissionKeyB64,
            bundle.cloud==null?null:bundle.cloud.tunnelKeyB64,
            bundle.cloud==null,
            generation);
        if(!configuration.edit()
            .putString("endpoint",bundle.directEndpoint)
            .putString("client",bundle.clientId)
            .putString("pin",bundle.pinnedGatewaySpkiB64Url)
            .putString("cloud_wss",bundle.cloud==null?"":bundle.cloud.wssUrl)
            .putString("cloud_peer",bundle.cloud==null?"":bundle.cloud.androidPeerId)
            .putString("cloud_target",bundle.cloud==null?"":bundle.cloud.windowsPeerId)
            .putString("pairing_generation",generation)
            .commit())
            throw new IOException("CONFIGURATION_COMMIT_FAILED");
    }

    private boolean pairingGenerationMatches(){
        try{
            String configGeneration=configuration.getString("pairing_generation","");
            String secretGeneration=secrets.pairingGeneration();
            return !configGeneration.isEmpty()&&configGeneration.equals(secretGeneration);
        }catch(Exception failure){return false;}
    }

    private void validateCloudKey(String encoded,String code)throws Exception{
        byte[] raw;
        try{raw=Base64.getDecoder().decode(encoded);}
        catch(IllegalArgumentException failure){throw new SecurityException(code);}
        try{if(raw.length!=32)throw new SecurityException(code);}
        finally{Arrays.fill(raw,(byte)0);}
    }
    private void refreshViews() {
        Session selected=session;
        if(stateView!=null)stateView.setText((historyHeld?"HISTORY_HOLD | ":"")+state+"\n"+lastLocalEvent+"\n"+
            (peerIdentityVerified?"Gateway identity confirmed by pinned E2E response":"Gateway E2E identity not yet confirmed")+"\nTransport: "+
            (selected==null?"NOT_CONNECTED":selected.route));
        if(connectButton!=null)connectButton.setText(selected==null?"Connect":"Disconnect");
        if(sendButton!=null){
            boolean gptMode=voiceRoom.mode()==AioVoiceRoom.Mode.GPT;
            boolean gptSend=!submitting&&!historyHeld&&chatGptReady()&&draftPrivacy==3;
            boolean aioSend=!submitting&&!historyHeld;
            sendButton.setEnabled(gptMode?gptSend:aioSend);
        }
        if(chatGptStateView!=null)chatGptStateView.setText(chatGptSummary(chatGpt.snapshot()));
        if(chatGptStreamView!=null)chatGptStreamView.setText(
            chatGptStreamingText.isEmpty()?"Provider stream: DORMANT":"Provider stream:\n"+chatGptStreamingText);
        if(liveVoiceButton!=null)liveVoiceButton.setText(liveVoiceMode?"End Voice":"Live Voice");
        if(historyView!=null)historyView.setText(historySummary());
        if(fabricView!=null)fabricView.setText("Presence discovery:\n"+presence.projectAndroidShadow().summary+
            "\nLast discovery refresh: "+(lastStatusAt==0?"NOT_MEASURED":Long.toString(lastStatusAt))+
            "\nRequester roundtrip: "+(lastRoundTripMs<0?"NOT_MEASURED":lastRoundTripMs+" ms")+
            "\nPinned encrypted identity: "+(peerIdentityVerified?"VERIFIED_RESPONSE":"NOT_CONFIRMED"));
        if(nodeResourceView!=null){
            try{nodeResourceView.setText(AndroidResourceProjection.capture(this).summary());}
            catch(Exception failure){nodeResourceView.setText("ANDROID RESOURCE NODE\nNOT_MEASURED: "+safeCode(failure));}
        }
    }
    private String historySummary() {
        synchronized(historyLock) {
            if(historyHeld)return "HOLD | encrypted history preserved after read failure";
            if(history.length()==0)return "No local conversation turns yet.";
            JSONArray visible;
            try{
                visible=historyRepresentation==null?
                    tailFromMemory(history,16):
                    AioNativeCellStateCodec.tail(historyRepresentation,16);
            }catch(Exception failure){
                visible=tailFromMemory(history,16);
            }
            StringBuilder summary=new StringBuilder();
            for(int i=0;i<visible.length();i++){
                JSONObject row=visible.optJSONObject(i);if(row==null)continue;
                String content=row.optString("text");
                summary.append(row.optString("role")).append(" | ").append(row.optString("provider","AIO")).append(" | ").append(row.optString("privacyClass"))
                    .append("\nintent=").append(row.optString("intentId")).append(" | ").append(row.optString("state"))
                    .append("\nrequest=").append(row.optString("requestId"))
                    .append("\n").append(content.length()>5000?content.substring(0,5000)+"\n[display abbreviated]":content).append("\n\n");
                if("OUTBOUND_ATTEMPT_OUTCOME_UNKNOWN".equals(row.optString("state")))
                    summary.append("No verified receipt in this row. Check PC receipts before resubmitting.\n\n");
            }
            return summary.toString();
        }
    }

    private static JSONArray appendHot(JSONArray source,JSONObject row,int maximum){
        JSONArray out=new JSONArray();
        int start=Math.max(0,source.length()-(maximum-1));
        for(int i=start;i<source.length();i++)out.put(source.opt(i));
        out.put(row);
        return out;
    }

    private static JSONArray tailFromMemory(JSONArray source,int count){
        JSONArray out=new JSONArray();
        for(int i=Math.max(0,source.length()-count);i<source.length();i++){
            try{out.put(source.get(i));}catch(Exception ignored){}
        }
        return out;
    }
    private void toggleConnection() {
        if(session!=null){disconnect("FOUNDER_DISCONNECT");return;}
        try{
            if(!secrets.hasSecret())throw new SecurityException("PAIRING_REQUIRED");
            if(!pairingGenerationMatches())throw new SecurityException("PAIRING_STATE_INCONSISTENT");
            String destination=configuration.getString("endpoint","").trim();
            Endpoint endpoint=destination.isEmpty()?null:Endpoint.parse(destination);
            if(destination.isEmpty()&&!cloudConfigured())throw new SecurityException("PRESENCE_ROUTE_REQUIRED");
            if(endpoint!=null&&AndroidLocalNetworkPolicy.needsRuntimePermission(android.os.Build.VERSION.SDK_INT,endpoint.host)
                &&!hasDirectLocalPermission(endpoint.host)&&!cloudConfigured()){
                requestDirectLocalPermission(endpoint.host);
                state="LOCAL_NETWORK_PERMISSION_REQUIRED";
                lastLocalEvent="Direct Presence requires local-network permission; approve Nearby devices/local network, then reconnect";
                refreshViews();return;
            }
            String client=configuration.getString("client",""),pin=configuration.getString("pin","");
            E2eCodec.pinnedKey(pin);
            if(!client.matches("[A-Za-z0-9_.-]{1,128}"))throw new SecurityException("CLIENT_ID_INVALID");
            Session next=new Session(epoch.invalidate(),client,pin);
            session=next;peerIdentityVerified=false;state="CONNECTING";refreshViews();io.execute(()->connect(next,destination));
        }catch(Exception failure){state="PAIRING_REQUIRED";lastLocalEvent=safeCode(failure);refreshViews();}
    }
    private boolean hasDirectLocalPermission(String host){
        if(!AndroidLocalNetworkPolicy.needsRuntimePermission(android.os.Build.VERSION.SDK_INT,host))return true;
        String permission=AndroidLocalNetworkPolicy.permissionName(android.os.Build.VERSION.SDK_INT);
        return !permission.isEmpty()&&checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private void requestDirectLocalPermission(String host){
        if(!AndroidLocalNetworkPolicy.needsRuntimePermission(android.os.Build.VERSION.SDK_INT,host))return;
        String permission=AndroidLocalNetworkPolicy.permissionName(android.os.Build.VERSION.SDK_INT);
        if(permission.isEmpty())return;
        requestPermissions(new String[]{permission},LOCAL_NETWORK_PERMISSION_REQUEST);
    }

    private void requestNodeNotificationPermissionIfNeeded(){
        if(android.os.Build.VERSION.SDK_INT<33)return;
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==
            android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION_REQUEST);
    }

    private void bindScreenProjection(){
        if(screenProjectionBound)return;
        screenProjectionBound=bindService(new Intent(this,AndroidScreenProjectionService.class),
            screenProjectionConnection,Context.BIND_AUTO_CREATE);
    }
    private void unbindScreenProjection(){
        if(!screenProjectionBound)return;
        try{unbindService(screenProjectionConnection);}catch(Exception ignored){}
        screenProjectionBound=false;screenProjectionBinder=null;
    }
    private void bindPersistentNode(){
        if(persistentNodeBound)return;
        Intent intent=new Intent(this,AioPersistentNodeService.class);
        persistentNodeBound=bindService(intent,persistentNodeConnection,Context.BIND_AUTO_CREATE);
    }
    private void unbindPersistentNode(){
        if(!persistentNodeBound)return;
        try{unbindService(persistentNodeConnection);}catch(Exception ignored){}
        persistentNodeBound=false;persistentNodeBinder=null;
    }
    private void restorePersistentSession(){
        AioPersistentNodeService.LocalBinder binder=persistentNodeBinder;
        if(binder==null||session!=null)return;
        CloudPresenceTransport cloud=binder.cloudLink();
        if(cloud==null)return;
        try{
            String client=configuration.getString("client",""),pin=configuration.getString("pin","");
            E2eCodec.pinnedKey(pin);
            if(!client.matches("[A-Za-z0-9_.-]{1,128}"))throw new SecurityException("CLIENT_ID_INVALID");
            Session restored=new Session(epoch.invalidate(),client,pin);
            restored.transport=cloud;restored.route=cloud.label();restored.persistentOwned=true;
            session=restored;state="RESTORING_PERSISTENT_NODE";peerIdentityVerified=binder.remoteCapabilityEligible();
            refreshViews();io.execute(()->reauthenticatePersistentSession(restored,binder));
        }catch(Exception failure){
            state="CONNECTION_HOLD";lastLocalEvent=safeCode(failure);refreshViews();
        }
    }
    private void reauthenticatePersistentSession(Session selected,AioPersistentNodeService.LocalBinder binder){
        try{
            byte[] witness=freshWitness(selected);
            PresenceProtocol.Frame hello,status,pong;long elapsed;byte[] ping;
            try{
                hello=exchange(selected,PresenceProtocol.HELLO,PresenceProtocol.HELLO_REPLY,UUID.randomUUID(),witness);
            }finally{Arrays.fill(witness,(byte)0);}
            if(hello.flags!=0)throw new SecurityException("PRESENCE_WITNESS_REJECTED");
            status=exchange(selected,PresenceProtocol.STATUS,PresenceProtocol.STATUS_REPLY,UUID.randomUUID(),new byte[0]);
            ping=PresenceProtocol.newNonce().getBytes(StandardCharsets.US_ASCII);long started=System.nanoTime();
            pong=exchange(selected,PresenceProtocol.PING,PresenceProtocol.PING_REPLY,UUID.randomUUID(),ping);
            elapsed=(System.nanoTime()-started)/1_000_000L;
            if(pong.flags!=0||!Arrays.equals(ping,pong.payload))throw new SecurityException("PING_INVALID");
            synchronized(authorityLock){
                if(!current(selected))return;
                AioProjectionMembrane.absorbHello(presence,hello);
                AioProjectionMembrane.absorbStatus(presence,status,elapsed);
                if(!presence.projectAndroidShadow().ready)throw new IOException("PC_CORE_NOT_READY");
                selected.authenticated=true;
            }
            boolean verified=binder!=null&&binder.remoteCapabilityEligible();
            peerIdentityVerified=verified;
            recordEvidence("PERSISTENT_NODE_SESSION_RESTORED",null,elapsed);
            ui(selected,()->{
                state="PRESENCE_READY";lastStatusAt=System.currentTimeMillis();lastRoundTripMs=elapsed;
                lastLocalEvent=verified?
                    "Persistent R5 node restored; pinned peer verification retained in-process":
                    "Persistent R5 node restored; send Dialogue to re-establish pinned E2E peer verification";
            });
        }catch(Exception failure){failSession(selected,failure);}
    }

    private void maybeAdoptPersistentLink(){
        Session selected=session;
        AioPersistentNodeService.LocalBinder binder=persistentNodeBinder;
        if(binder==null||selected==null||selected.persistentOwned||!selected.authenticated||
            !(selected.transport instanceof CloudPresenceTransport)||
            !AioPersistentNodeController.founderEnabled(this))return;
        try{
            CloudPresenceTransport cloud=(CloudPresenceTransport)selected.transport;
            binder.adoptCloudLink(cloud);
            selected.persistentOwned=true;
            if(peerIdentityVerified)binder.peerVerified(cloud);
            recordEvidence("PERSISTENT_NODE_LINK_ADOPTED",null,-1);
        }catch(Exception failure){
            lastLocalEvent="Persistent node adoption held: "+safeCode(failure);
        }
    }
    private boolean current(Session selected){return !destroyed&&session==selected&&epoch.accepts(selected.generation);}
    private void ui(Session selected,Runnable work){
        runOnUiThread(()->{if(current(selected)){work.run();refreshViews();}});
    }
    private byte[] freshWitness(Session selected)throws Exception{
        byte[] raw=secrets.readWitnessSecret();if(raw==null)throw new SecurityException("PAIRING_REQUIRED");
        try{
            long now=System.currentTimeMillis();String nonce=PresenceProtocol.newNonce();
            return AioProjectionMembrane.projectWitnessCell(selected.client,now,nonce,PresenceProtocol.witness(selected.client,now,nonce,raw));
        }finally{Arrays.fill(raw,(byte)0);}
    }
    private boolean cloudConfigured(){
        return configuration.getString("cloud_wss","").startsWith("wss://")
            &&configuration.getString("cloud_peer","").matches("[A-Za-z0-9_.:-]{1,128}")
            &&configuration.getString("cloud_target","").matches("[A-Za-z0-9_.:-]{1,128}")
            &&secrets.hasText("cloud_admission")&&secrets.hasText("cloud_tunnel");
    }
    private PresenceTransport createCloudTransport()throws Exception{
        String admissionText=secrets.readText("cloud_admission"),tunnelText=secrets.readText("cloud_tunnel");
        if(admissionText==null||tunnelText==null)throw new SecurityException("CLOUD_KEYS_REQUIRED");
        byte[] admission=null,tunnel=null;
        try{
            admission=Base64.getDecoder().decode(admissionText);tunnel=Base64.getDecoder().decode(tunnelText);
            if(admission.length!=32||tunnel.length!=32)throw new SecurityException("CLOUD_KEY_LENGTH");
            return new CloudPresenceTransport(configuration.getString("cloud_wss",""),
                configuration.getString("cloud_peer",""),admission,tunnel,configuration.getString("cloud_target",""));
        }finally{
            if(admission!=null)Arrays.fill(admission,(byte)0);if(tunnel!=null)Arrays.fill(tunnel,(byte)0);
        }
    }
    private PresenceProtocol.Frame exchange(Session selected,byte type,byte expected,UUID id,byte[] payload)throws Exception{
        if(!current(selected)||selected.transport==null)throw new IOException("REQUEST_CANCELLED");
        PresenceProtocol.Frame response=FounderPresenceSessionController.exchange(selected.transport,type,expected,id,payload);
        if(!current(selected))throw new IOException("REQUEST_CANCELLED");
        return response;
    }
    private void connect(Session selected,String destination){
        try{
            PresenceTransport direct=null;
            if(!destination.isEmpty()){
                Endpoint endpoint=Endpoint.parse(destination);
                boolean permissionReady=!AndroidLocalNetworkPolicy.needsRuntimePermission(
                    android.os.Build.VERSION.SDK_INT,endpoint.host)||hasDirectLocalPermission(endpoint.host);
                if(permissionReady)direct=new DirectPresenceTransport(endpoint.host,endpoint.port);
                else if(cloudConfigured())
                    recordEvidence("DIRECT_LOCAL_PERMISSION_MISSING_R5_FALLBACK",null,-1);
                else throw new SecurityException("LOCAL_NETWORK_PERMISSION_REQUIRED");
            }
            FounderPresenceSessionController.CloudFactory cloudFactory=cloudConfigured()?this::createCloudTransport:null;
            byte[] witness=freshWitness(selected);
            FounderPresenceSessionController.Ready ready;
            try{ready=new FounderPresenceSessionController().connect(direct,cloudFactory,witness);}
            finally{Arrays.fill(witness,(byte)0);}
            if(!current(selected)){ready.transport.close();return;}
            selected.transport=ready.transport;selected.route=ready.transport.label();
            synchronized(authorityLock){
                if(!current(selected)){ready.transport.close();return;}
                AioProjectionMembrane.absorbHello(presence,ready.hello);
                AioProjectionMembrane.absorbStatus(presence,ready.status,ready.pingRoundTripMs);
                if(!presence.projectAndroidShadow().ready)throw new IOException("PC_CORE_NOT_READY");
                selected.authenticated=true;
            }
            if(!destination.isEmpty()&&"R5_CLOUD".equals(selected.route))
                recordEvidence("DIRECT_PRESENCE_UNAVAILABLE_CLOUD_FALLBACK",null,-1);
            recordEvidence("PRESENCE_CONNECTED_"+selected.route,null,ready.pingRoundTripMs);
            if(selected.transport instanceof CloudPresenceTransport){
                if(AioPersistentNodeController.founderEnabled(this)){
                    bindPersistentNode();
                    maybeAdoptPersistentLink();
                }else startAndroidNodeService(selected,(CloudPresenceTransport)selected.transport);
            }
            ui(selected,()->{
                state="PRESENCE_READY";
                lastLocalEvent="DIRECT".equals(selected.route)?
                    "Connected directly; Dialogue uses the pinned encrypted gateway":
                    "Connected through E2E R5 transport to the pinned encrypted gateway";
                lastStatusAt=System.currentTimeMillis();lastRoundTripMs=ready.pingRoundTripMs;
            });
        }catch(Exception failure){failSession(selected,failure);}
    }
    private void startAndroidNodeService(Session selected,CloudPresenceTransport cloud){
        nodeIo.execute(()->serveAndroidNode(selected,cloud));
    }
    private void serveAndroidNode(Session selected,CloudPresenceTransport cloud){
        while(current(selected)&&selected.authenticated&&!destroyed){
            PresenceProtocol.Frame frame=null;
            byte[] reply=null;
            try{
                frame=cloud.pollNodeRequest(1000);
                if(frame==null)continue;
                if(!current(selected)||!selected.authenticated)break;

                AndroidCapabilityDispatcher.Result result;
                if(!peerIdentityVerified){
                    result=deniedCapabilityResult("UNVERIFIED","UNVERIFIED","REMOTE_PEER_NOT_VERIFIED");
                }else if(capabilityAuditHeld){
                    result=deniedCapabilityResult("UNKNOWN","UNKNOWN","AUDIT_STATE_UNAVAILABLE");
                }else{
                    AndroidCapabilityProtocol.Request preview=null;
                    try{
                        preview=AndroidCapabilityProtocol.parse(frame.payload);
                        synchronized(historyLock){
                            JSONObject attempt=stateStore.appendCapabilityAttempt(
                                frame.id,cloud.remotePeerId(),cloud.remoteSessionEpoch(),
                                preview.capability.name(),preview.action);
                            evidence=appendHot(evidence,attempt,AioFounderStateStore.HOT_EVIDENCE_ROWS);
                            evidenceRepresentation=AioNativeCellStateCodec.encodeArray(evidence);
                        }
                        result=capabilityDispatcher.dispatch(cloud.remotePeerId(),frame.id,frame.payload);
                    }catch(Exception auditOrParseFailure){
                        if(preview==null)
                            result=capabilityDispatcher.dispatch(cloud.remotePeerId(),frame.id,frame.payload);
                        else{
                            capabilityAuditHeld=true;
                            result=deniedCapabilityResult(preview.capability.name(),preview.action,"AUDIT_STATE_UNAVAILABLE");
                        }
                    }
                }

                if(!capabilityAuditHeld){
                    try{
                        synchronized(historyLock){
                            JSONObject outcome=stateStore.appendCapabilityResult(
                                frame.id,cloud.remotePeerId(),cloud.remoteSessionEpoch(),result);
                            evidence=appendHot(evidence,outcome,AioFounderStateStore.HOT_EVIDENCE_ROWS);
                            evidenceRepresentation=AioNativeCellStateCodec.encodeArray(evidence);
                        }
                    }catch(Exception auditFailure){
                        capabilityAuditHeld=true;
                        lastLocalEvent="CAPABILITY_AUDIT_HOLD";
                    }
                }

                reply=result.payload;
                cloud.sendNodeReply(frame.id,result.accepted,reply);
            }catch(Exception failure){
                if(current(selected))failSession(selected,failure);
                break;
            }finally{
                if(frame!=null&&frame.payload!=null)Arrays.fill(frame.payload,(byte)0);
                if(reply!=null)Arrays.fill(reply,(byte)0);
            }
        }
    }
    private static AndroidCapabilityDispatcher.Result deniedCapabilityResult(String capability,String action,String code){
        return new AndroidCapabilityDispatcher.Result(
            false,
            AndroidCapabilityProtocol.reply(false,code,null),
            capability,action,code,0,AioAndroidCausalPlanner.Dependency.values().length);
    }

    private void refreshPresence(){
        Session selected=session;if(selected==null||!selected.authenticated||!current(selected))return;
        try{
            long started=System.nanoTime();
            PresenceProtocol.Frame response=exchange(selected,PresenceProtocol.STATUS,PresenceProtocol.STATUS_REPLY,UUID.randomUUID(),new byte[0]);
            long elapsed=(System.nanoTime()-started)/1_000_000L;
            synchronized(authorityLock){
                if(!current(selected))return;
                AioProjectionMembrane.absorbStatus(presence,response,elapsed);
                if(!presence.projectAndroidShadow().ready)throw new IOException("PC_CORE_NOT_READY");
            }
            ui(selected,()->{lastStatusAt=System.currentTimeMillis();lastRoundTripMs=elapsed;});
        }catch(Exception failure){failSession(selected,failure);}
    }
    private void submitIntent(){
        captureDraft();
        if(historyHeld||submitting)return;
        if(voiceRoom.mode()==AioVoiceRoom.Mode.GPT){submitChatGpt();return;}
        if(voiceRoom.mode()==AioVoiceRoom.Mode.AIO_GPT_SUPERVISED){
            notice("AIO_GPT_SUPERVISOR_NOT_QUALIFIED");return;
        }
        if(draftPrivacy>=2){
            notice("AIO_REMOTE_PRIVACY_REQUIRES_LOCAL_CLASS");return;
        }

        try{
            AioLocalReasoningRouter.Decision local=AioLocalReasoningRouter.route(draftText,new AioLocalReasoningRouter.ProjectionSource(){
                @Override public String resources(){return AndroidResourceProjection.capture(MainActivity.this).summary();}
                @Override public String readiness(){return readinessReport().summary();}
                @Override public String capabilities(){return AioLocalReasoningRouter.capabilitySummary();}
                @Override public String evidence(){synchronized(historyLock){return AioEvidenceCollapse.project(evidence,16);}}
                @Override public String history(){return historySummary();}
                @Override public String nativeState(){return nativeStateSummary();}
            });
            if(local.handledLocally){
                String intentId=UUID.randomUUID().toString();
                String privacy=selectedPrivacyClass();
                appendHistory("Founder",intentId,privacy,"","LOCAL_INPUT",draftText);
                appendHistory("AIO Local",intentId,privacy,"","LOCAL_ANSWER",local.response);
                recordEvidence("LOCAL_REASONING_"+local.route.name(),null,0);
                lastLocalEvent="Answered locally via "+local.route+"; no Windows/R5 round trip";
                if(liveVoiceMode){
                    voiceRoom.beginResponse();voiceRoom.beginSpeech();voiceRuntime.speak(local.response);
                    io.schedule(() -> runOnUiThread(() -> {
                        if(liveVoiceMode&&!destroyed){
                            try{voiceRoom.finishSpeech();voiceRoom.startListening();voiceRuntime.startDictation(true);}
                            catch(Exception ignored){}
                            refreshViews();
                        }
                    }),Math.min(4200,Math.max(1200,local.response.length()*18L)),TimeUnit.MILLISECONDS);
                }
                refreshViews();return;
            }
        }catch(Exception failure){
            notice("Local reasoning held: "+safeCode(failure));return;
        }

        Session selected=session;
        if(selected==null||!selected.authenticated||!current(selected)){
            lastLocalEvent="REMOTE_REASONING_REQUIRES_CONNECTED_AIO";
            refreshViews();return;
        }
        try{
            FounderDialogueSubmit submit=new FounderDialogueSubmit(UUID.randomUUID().toString(),draftText,
                selectedPrivacyClass(),"AIO","pending","pending");
            submitting=true;lastLocalEvent="Preparing a bounded encrypted AIO message";refreshViews();io.execute(()->sendIntent(selected,submit));
        }catch(Exception failure){notice("Intent held: "+safeCode(failure));}
    }
    private void submitChatGpt(){
        String privacy=selectedPrivacyClass();
        if(!chatGptReady()){notice("CHATGPT_PROVIDER_NOT_READY");return;}
        if(!"EXTERNAL_ALLOWED".equals(privacy)){
            notice("CHATGPT_PRIVACY_PROJECTION_DENIED");return;
        }
        if(draftText==null||draftText.trim().isEmpty()||draftText.length()>4096){
            notice("CHATGPT_INPUT_TEXT_INVALID");return;
        }
        final String intentId=UUID.randomUUID().toString();
        try{
            ChatGptHistoryProjection.Plan context=stateStore.chatGptContext(draftText,48*1024);
            appendHistoryProvider("Founder","ChatGPT",intentId,privacy,"","CHATGPT_OUTBOUND_ATTEMPT",draftText);
            submitting=true;chatGptStreamingText="";
            lastLocalEvent="GPT causal projection | rows="+context.causal.manifestedRows+"/"+context.causal.totalRows+
                " | chars="+context.causal.manifestedChars+"/"+context.causal.totalChars;
            recordEvidence("CHATGPT_CAUSAL_CONTEXT_PROJECTED",null,0);
            refreshViews();
            final long started=System.nanoTime();
            String instructions="You are ChatGPT operating as an authorized intelligence provider inside AIO. "+
                "AIO owns durable project memory, device authority, execution custody, and receipts. "+
                "Continue coherently from the supplied AIO compatibility projection. "+
                "When the Founder requests work that requires the Windows AIO/PC environment, use aio_windows_objective_submit. "+
                "That tool submits a bounded objective into AIO; it is not a shell. Treat only its returned verified receipt as evidence of submission, never as proof that execution finished. "+
                "Never claim a PC, Android, Git, build, test, or deployment effect unless supplied AIO evidence proves it.";
            chatGpt.respond(intentId,privacy,draftText,null,instructions,context.input,
                (name,arguments)->executeChatGptTool(name,arguments),
                new ChatGptProviderRuntime.Listener(){
                    @Override public void onDelta(String delta){
                        runOnUiThread(()->{
                            if(destroyed)return;
                            if(chatGptStreamingText.length()<64*1024)chatGptStreamingText+=delta;
                            if(chatGptStreamView!=null)chatGptStreamView.setText("Provider stream:\n"+chatGptStreamingText);
                        });
                    }
                    @Override public void onCompleted(ChatGptResponsesContract.Completion completion){
                        long elapsed=(System.nanoTime()-started)/1_000_000L;
                        try{
                            appendHistoryProvider("ChatGPT","ChatGPT",intentId,privacy,"","CHATGPT_COMPLETED",completion.text);
                            recordEvidence("CHATGPT_RESPONSE_COMPLETED",null,elapsed);
                        }catch(Exception storeFailure){historyHeld=true;}
                        runOnUiThread(()->{
                            submitting=false;lastRoundTripMs=elapsed;chatGptStreamingText=completion.text;
                            lastLocalEvent="ChatGPT completed | "+elapsed+" ms | encrypted AIO continuity retained";
                            if(liveVoiceMode&&!completion.text.isBlank()){
                                voiceRoom.beginResponse();voiceRoom.beginSpeech();voiceRuntime.speak(completion.text);
                                io.schedule(()->runOnUiThread(()->{
                                    if(liveVoiceMode&&!destroyed){
                                        try{voiceRoom.finishSpeech();voiceRoom.startListening();voiceRuntime.startDictation(true);}
                                        catch(Exception ignored){}
                                        refreshViews();
                                    }
                                }),Math.min(6000,Math.max(1500,completion.text.length()*14L)),TimeUnit.MILLISECONDS);
                            }
                            refreshViews();
                        });
                    }
                    @Override public void onFailure(String code){
                        try{
                            appendHistoryProvider("Local evidence","AIO",intentId,privacy,"","CHATGPT_INCOMPLETE",
                                "No verified completed ChatGPT response: "+code);
                            recordEvidence("CHATGPT_RESPONSE_HOLD",null,-1);
                        }catch(Exception storeFailure){historyHeld=true;}
                        runOnUiThread(()->{
                            submitting=false;lastLocalEvent=code+" | partial provider stream is not a verified assistant turn";
                            refreshViews();
                        });
                    }
                });
        }catch(Exception failure){notice("GPT projection held: "+safeCode(failure));}
    }

    private String executeChatGptTool(String name,String arguments)throws Exception{
        ChatGptAioToolContract.requireKnown(name);
        String instruction=ChatGptAioToolContract.parseInstruction(arguments);
        Session selected=session;
        if(selected==null||!selected.authenticated||!current(selected))
            return new JSONObject().put("ok",false).put("error","WINDOWS_AIO_NOT_CONNECTED").toString();
        try{
            return submitChatGptWindowsObjective(selected,instruction);
        }catch(Exception failure){
            try{failSession(selected,failure);}catch(Exception ignored){}
            return new JSONObject().put("ok",false).put("error",safeCode(failure)).toString();
        }
    }

    private String submitChatGptWindowsObjective(Session selected,String instruction)throws Exception{
        E2eCodec.Request request=null;byte[] command=null,plaintext=null;
        UUID requestId=UUID.randomUUID();String toolIntentId=UUID.randomUUID().toString();
        try{
            if(!current(selected))throw new IOException("REQUEST_CANCELLED");
            String lease=secrets.readText("lease");
            byte[] witness=freshWitness(selected);
            String witnessText;
            try{witnessText=new String(witness,StandardCharsets.UTF_8);}
            finally{Arrays.fill(witness,(byte)0);}
            FounderDialogueSubmit submit=new FounderDialogueSubmit(
                toolIntentId,instruction,"LOCAL_ONLY","AIO",lease,witnessText);
            command=AioProjectionMembrane.projectIntentCommand(submit);
            request=E2eCodec.encrypt(selected.pin,requestId.toString(),System.currentTimeMillis(),command);
            appendHistoryProvider("ChatGPT Tool","ChatGPT",toolIntentId,"LOCAL_ONLY",
                requestId.toString(),"TOOL_OUTBOUND_ATTEMPT",instruction);

            selected.transport.setReadTimeoutMillis(15000);
            long started=System.nanoTime();
            PresenceProtocol.Frame response=exchange(selected,PresenceProtocol.E2E,
                PresenceProtocol.E2E_REPLY,requestId,AioProjectionMembrane.projectEnvelope(request));
            plaintext=request.context.decrypt(AioProjectionMembrane.absorbEncryptedResponse(response.payload));
            JSONObject result=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));
            String schema=result.optString("schema");
            if("aio.private-gateway.receipt.v1".equals(schema)){
                if(!requestId.toString().equals(result.getString("requestId"))||
                    !FounderIntentCapsule.ACTION.equals(result.getString("action")))
                    throw new SecurityException("RECEIPT_CORRELATION_INVALID");
            }else if(!"aio.private-gateway.error.v1".equals(schema)){
                throw new SecurityException("RECEIPT_SCHEMA_INVALID");
            }

            long elapsed=(System.nanoTime()-started)/1_000_000L;
            updateHistoryReceipt(toolIntentId,requestId.toString(),
                response.flags==0?"VERIFIED_TOOL_RECEIPT":"VERIFIED_TOOL_REJECTION",result.toString());
            recordEvidence(response.flags==0?"CHATGPT_TOOL_RECEIPT_VERIFIED":
                "CHATGPT_TOOL_REJECTION_VERIFIED",requestId.toString(),elapsed);
            if(response.flags==0)peerIdentityVerified=true;

            JSONObject toolResult=new JSONObject();
            toolResult.put("ok",response.flags==0);
            toolResult.put("roundTripMs",elapsed);
            toolResult.put("gatewayReceipt",result);
            return toolResult.toString();
        }finally{
            if(request!=null)request.context.close();
            if(command!=null)Arrays.fill(command,(byte)0);
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            try{if(current(selected)&&selected.transport!=null)selected.transport.setReadTimeoutMillis(7000);}
            catch(Exception ignored){}
        }
    }

    private void sendIntent(Session selected,FounderDialogueSubmit submit){
        E2eCodec.Request request=null;byte[] command=null,plaintext=null;UUID requestId=UUID.randomUUID();boolean attempted=false;
        try{
            if(!current(selected))return;
            String lease=secrets.readText("lease");
            submit=new FounderDialogueSubmit(submit.intentId,submit.text,submit.privacyClass,"AIO",lease,
                new String(freshWitness(selected),StandardCharsets.UTF_8));
            command=AioProjectionMembrane.projectIntentCommand(submit);
            request=E2eCodec.encrypt(selected.pin,requestId.toString(),System.currentTimeMillis(),command);
            appendHistory("Founder",submit.intentId,submit.privacyClass,requestId.toString(),"OUTBOUND_ATTEMPT_OUTCOME_UNKNOWN",submit.text);
            if(!current(selected))return;
            selected.transport.setReadTimeoutMillis(15000);attempted=true;long started=System.nanoTime();
            PresenceProtocol.Frame response=exchange(selected,PresenceProtocol.E2E,PresenceProtocol.E2E_REPLY,requestId,AioProjectionMembrane.projectEnvelope(request));
            plaintext=request.context.decrypt(AioProjectionMembrane.absorbEncryptedResponse(response.payload));
            JSONObject result=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));String schema=result.optString("schema");
            if("aio.private-gateway.receipt.v1".equals(schema)){
                if(!requestId.toString().equals(result.getString("requestId"))||!FounderIntentCapsule.ACTION.equals(result.getString("action")))
                    throw new SecurityException("RECEIPT_CORRELATION_INVALID");
            }else if(!"aio.private-gateway.error.v1".equals(schema))throw new SecurityException("RECEIPT_SCHEMA_INVALID");
            long elapsed=(System.nanoTime()-started)/1_000_000L;
            if(!current(selected))return;
            updateHistoryReceipt(submit.intentId,requestId.toString(),response.flags==0?"VERIFIED_RECEIPT":"VERIFIED_REJECTION",result.toString());
            recordEvidence(response.flags==0?"E2E_RECEIPT_VERIFIED":"E2E_REJECTION_VERIFIED",requestId.toString(),elapsed);
            ui(selected,()->{
                submitting=false;lastRoundTripMs=elapsed;
                try{
                    if(selected.persistentOwned){
                        if(persistentNodeBinder==null)throw new IllegalStateException("PERSISTENT_NODE_BINDER_MISSING");
                        persistentNodeBinder.peerVerified(
                            selected.transport instanceof CloudPresenceTransport?(CloudPresenceTransport)selected.transport:null);
                    }
                    peerIdentityVerified=true;
                    lastLocalEvent="Pinned encrypted AIO response received | proposal receipt only, execution not claimed";
                }catch(Exception verificationFailure){
                    peerIdentityVerified=false;
                    lastLocalEvent="Pinned response received but persistent peer transition held: "+safeCode(verificationFailure);
                }
                if(liveVoiceMode&&peerIdentityVerified){
                    voiceRoom.beginResponse();voiceRoom.beginSpeech();
                    voiceRuntime.speak(response.flags==0?
                        "AIO received your instruction. The Windows node accepted the queued proposal.":
                        "AIO received a verified rejection from the Windows node.");
                    io.schedule(() -> runOnUiThread(() -> {
                        if(liveVoiceMode&&!destroyed){
                            try{voiceRoom.finishSpeech();voiceRoom.startListening();voiceRuntime.startDictation(true);}
                            catch(Exception ignored){}
                            refreshViews();
                        }
                    }),2800,TimeUnit.MILLISECONDS);
                }
            });
        }catch(Exception failure){
            if(current(selected)){
                try{appendHistory("Local evidence",submit.intentId,submit.privacyClass,requestId.toString(),attempted?"OUTCOME_UNKNOWN":"LOCAL_HOLD",
                    attempted?"No verified receipt. Check PC receipts using this intent/request ID before resubmitting.":
                        "No network submission completed: "+safeCode(failure));}catch(Exception storeFailure){historyHeld=true;}
                recordEvidence(attempted?"E2E_OUTCOME_UNKNOWN":"E2E_LOCAL_HOLD",requestId.toString(),-1);failSession(selected,failure);
            }
        }finally{
            if(request!=null)request.context.close();if(command!=null)Arrays.fill(command,(byte)0);if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            try{if(current(selected)&&selected.transport!=null)selected.transport.setReadTimeoutMillis(7000);}catch(Exception ignored){}
        }
    }
    private void appendHistory(String role,String intentId,String privacyClass,String requestId,String resultState,String content)throws Exception{
        appendHistoryProvider(role,"AIO",intentId,privacyClass,requestId,resultState,content);
    }
    private void appendHistoryProvider(String role,String provider,String intentId,String privacyClass,String requestId,String resultState,String content)throws Exception{
        synchronized(historyLock){
            if(historyHeld)throw new IOException("HISTORY_HOLD");
            JSONObject row=new JSONObject();row.put("role",role);row.put("provider",provider);row.put("privacyClass",privacyClass);
            row.put("intentId",intentId);row.put("requestId",requestId);row.put("state",resultState);
            row.put("timestampUnixMs",System.currentTimeMillis());row.put("text",content);
            stateStore.appendHistory(row);
            history=appendHot(history,row,AioFounderStateStore.HOT_HISTORY_ROWS);
            historyRepresentation=AioNativeCellStateCodec.encodeArray(history);
        }
    }
    private void updateHistoryReceipt(String intentId,String requestId,String resultState,String receipt)throws Exception{
        synchronized(historyLock){
            JSONArray next=new JSONArray(history.toString());
            for(int i=next.length()-1;i>=0;i--){JSONObject row=next.optJSONObject(i);
                if(row!=null&&intentId.equals(row.optString("intentId"))&&requestId.equals(row.optString("requestId"))){
                    row.put("state",resultState);row.put("serverReceipt",receipt);
                    JSONObject r=new JSONObject(receipt);
                    JSONObject binding=r.optJSONObject("objectiveBinding");
                    if(binding==null){
                        JSONObject result=r.optJSONObject("result");
                        if(result!=null)binding=result.optJSONObject("objectiveBinding");
                    }
                    if(binding==null)binding=r;
                    if(binding.has("objectiveId"))row.put("objectiveId",binding.get("objectiveId"));
                    if(binding.has("objectiveVersion"))row.put("objectiveVersion",binding.get("objectiveVersion"));
                    if(binding.has("objectiveFingerprint"))row.put("objectiveFingerprint",binding.get("objectiveFingerprint"));
                    break;
                }
            }
            JSONObject updated=null;
            for(int i=next.length()-1;i>=0;i--){
                JSONObject candidate=next.optJSONObject(i);
                if(candidate!=null&&intentId.equals(candidate.optString("intentId"))&&requestId.equals(candidate.optString("requestId"))){
                    updated=candidate;break;
                }
            }
            if(updated==null)throw new IOException("HISTORY_RECEIPT_TARGET_MISSING");
            stateStore.appendHistory(updated);
            history=tailFromMemory(next,AioFounderStateStore.HOT_HISTORY_ROWS);
            historyRepresentation=AioNativeCellStateCodec.encodeArray(history);
        }
    }
    private void recordEvidence(String code,String requestId,long elapsedMs){
        synchronized(historyLock){
            try{
                JSONObject row=new JSONObject();row.put("code",code);row.put("atUnixMs",System.currentTimeMillis());
                if(requestId!=null)row.put("requestId",requestId);if(elapsedMs>=0)row.put("requesterRoundTripMs",elapsedMs);
                stateStore.appendEvidence(row);
                evidence=appendHot(evidence,row,AioFounderStateStore.HOT_EVIDENCE_ROWS);
                evidenceRepresentation=AioNativeCellStateCodec.encodeArray(evidence);
            }catch(Exception failure){historyHeld=true;}
        }
    }
    private void failSession(Session selected,Exception failure){
        try{
            if(selected.persistentOwned&&persistentNodeBinder!=null)
                persistentNodeBinder.releaseCloudLink(selected.transport instanceof CloudPresenceTransport?
                    (CloudPresenceTransport)selected.transport:null);
            else if(selected.transport!=null)selected.transport.close();
        }catch(Exception ignored){}
        selected.persistentOwned=false;
        ui(selected,()->{epoch.invalidate();session=null;submitting=false;peerIdentityVerified=false;
            state="CONNECTION_HOLD";lastLocalEvent=safeCode(failure)+" | reconnect explicitly";
            presence.transition(AioPresenceField.Phase.HELD,"CONNECTION_LOST");});
    }
    private void disconnect(String reason){
        Session previous;
        synchronized(authorityLock){
            epoch.invalidate();previous=session;session=null;
            presence.transition(AioPresenceField.Phase.DORMANT,reason);
        }
        submitting=false;peerIdentityVerified=false;
        if(previous!=null)try{
            if(previous.persistentOwned&&persistentNodeBinder!=null)
                persistentNodeBinder.releaseCloudLink(previous.transport instanceof CloudPresenceTransport?
                    (CloudPresenceTransport)previous.transport:null);
            else if(previous.transport!=null)previous.transport.close();
            previous.persistentOwned=false;
        }catch(Exception ignored){}
        state="DISCONNECTED";
        lastLocalEvent="Disconnected; local request generation cancelled";refreshViews();
    }
    private String safeCode(Exception failure){
        String message=failure.getMessage();
        return message!=null&&message.matches("[A-Za-z0-9_.:-]{3,128}")?message:failure.getClass().getSimpleName();
    }
    private void notice(String message){
        lastLocalEvent=message;Toast.makeText(this,message,Toast.LENGTH_LONG).show();refreshViews();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==UPDATE_APK_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null){
                lastLocalEvent="ANDROID_UPDATE_SELECTION_CANCELLED";refreshViews();return;
            }
            android.net.Uri source=data.getData();
            lastLocalEvent="Staging selected AIO APK for signature/version verification";refreshViews();
            io.execute(()->{
                try{
                    AndroidUpdateStager.StageResult staged=updateStager.stage(source);
                    runOnUiThread(()->{
                        recordEvidence("ANDROID_UPDATE_STAGED",null,-1);
                        lastLocalEvent="Signed AIO self-update staged: v"+staged.versionCode+" sha256="+staged.sha256;
                        showSurface();
                    });
                }catch(Exception failure){
                    runOnUiThread(()->notice("Update stage held: "+safeCode(failure)));
                }
            });
            return;
        }
        if(requestCode==SCREEN_PROJECTION_REQUEST){
            if(resultCode!=RESULT_OK||data==null){
                lastLocalEvent="ANDROID_SCREEN_PROJECTION_CONSENT_DENIED";
                recordEvidence("ANDROID_SCREEN_PROJECTION_CONSENT_DENIED",null,-1);
                refreshViews();return;
            }
            try{
                AndroidScreenProjectionController.startAfterConsent(this,resultCode,data);
                bindScreenProjection();
                lastLocalEvent="Founder approved one Android screen-observation session; remote screen authority remains ungranted";
                recordEvidence("ANDROID_SCREEN_PROJECTION_STARTED",null,-1);
                refreshViews();
            }catch(Exception failure){
                lastLocalEvent=safeCode(failure);refreshViews();
            }
            return;
        }
        if(requestCode!=FILE_TREE_REQUEST)return;
        if(resultCode!=RESULT_OK||data==null){
            lastLocalEvent="ANDROID_FILE_TREE_GRANT_CANCELLED";
            refreshViews();return;
        }
        try{
            capabilityBroker.acceptStorageGrant(data);
            recordEvidence("ANDROID_FILE_TREE_GRANT_ACCEPTED",null,-1);
            lastLocalEvent="Founder file-tree grant persisted locally; no remote peer authority was granted";
            showSurface();
        }catch(Exception failure){
            lastLocalEvent=safeCode(failure);
            refreshViews();
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCAL_NETWORK_PERMISSION_REQUEST){
            boolean granted=grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            lastLocalEvent=granted?
                "Direct local-network permission granted; Presence can try LAN first":
                (cloudConfigured()?
                    "Direct local-network permission denied; encrypted R5 fallback remains available":
                    "LOCAL_NETWORK_PERMISSION_REQUIRED");
            refreshViews();return;
        }
        if(requestCode==NOTIFICATION_PERMISSION_REQUEST){
            boolean granted=grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            lastLocalEvent=granted?
                "Persistent-node notifications enabled":
                "Persistent node remains allowed, but Android may show it only in Active apps while notification permission is denied";
            refreshViews();return;
        }
        if(requestCode==VoiceRuntime.AUDIO_PERMISSION_REQUEST){
            boolean granted=grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            lastLocalEvent=granted?"Microphone permission granted | tap Dictate or Live Voice":"MIC_PERMISSION_DENIED";
            refreshViews();
        }
    }
    @Override protected void onStart(){
        super.onStart();
        AioAppVisibility.enteredForeground();
        bindScreenProjection();
        if(AioPersistentNodeController.founderEnabled(this)){
            AioPersistentNodeController.startFromVisibleFounder(this);
            bindPersistentNode();
        }
    }
    @Override protected void onStop(){
        AioAppVisibility.leftForeground();
        unbindScreenProjection();
        unbindPersistentNode();
        super.onStop();
    }
    @Override protected void onPause(){
        super.onPause();
        if(liveVoiceMode){
            liveVoiceMode=false;
            lastLocalEvent="Live Voice paused by Android lifecycle; explicit restart required";
        }
        if(voiceRuntime!=null)voiceRuntime.stopRecognition();
        voiceRoom.pauseLifecycle();
        refreshViews();
    }
    @Override protected void onResume(){
        super.onResume();
        try{voiceRoom.resumeLifecycle();}catch(Exception ignored){}
        refreshViews();
    }
    @Override protected void onDestroy(){
        Session previous;
        synchronized(authorityLock){destroyed=true;epoch.invalidate();previous=session;session=null;}
        if(previous!=null)try{
            if(!previous.persistentOwned&&previous.transport!=null)previous.transport.close();
        }catch(Exception ignored){}
        unbindScreenProjection();
        unbindPersistentNode();
        if(voiceRuntime!=null)voiceRuntime.close();
        voiceRoom.close();
        io.shutdownNow();nodeIo.shutdownNow();super.onDestroy();
    }
    private static final class Session{
        final long generation;final String client,pin;volatile boolean authenticated,persistentOwned;volatile PresenceTransport transport;volatile String route="NOT_CONNECTED";
        Session(long generation,String client,String pin){this.generation=generation;this.client=client;this.pin=pin;}
    }
    static final class Endpoint{
        final String host;final int port;
        Endpoint(String host,int port){this.host=host;this.port=port;}
        static Endpoint parse(String value){
            if(value==null||value.length()>320||value.matches(".*\\s.*"))throw new IllegalArgumentException("ENDPOINT_INVALID");
            String host,portText;
            if(value.startsWith("[")){
                int close=value.indexOf(']');
                if(close<=1||close+2>=value.length()||value.charAt(close+1)!=':')throw new IllegalArgumentException("ENDPOINT_INVALID");
                host=value.substring(1,close);portText=value.substring(close+2);
            }else{
                int colon=value.lastIndexOf(':');
                if(colon<=0||value.indexOf(':')!=colon)throw new IllegalArgumentException("ENDPOINT_INVALID");
                host=value.substring(0,colon);portText=value.substring(colon+1);
            }
            if(host.contains("/")||host.contains("@")||host.contains("#")||host.isEmpty())throw new IllegalArgumentException("ENDPOINT_INVALID");
            int port=Integer.parseInt(portText);if(port<1||port>65535)throw new IllegalArgumentException("ENDPOINT_INVALID");
            return new Endpoint(host,port);
        }
    }
}

