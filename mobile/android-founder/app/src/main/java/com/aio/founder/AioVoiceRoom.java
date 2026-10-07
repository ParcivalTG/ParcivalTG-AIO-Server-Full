package com.aio.founder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** AIO-native dialogue/voice state. Android audio APIs exist only in VoiceRuntime. */
final class AioVoiceRoom {
    enum Mode { AIO, GPT, AIO_GPT_SUPERVISED }
    enum State { IDLE, LISTENING, THINKING, SPEAKING, MUTED, PAUSED, CLOSED }
    enum Privacy { LOCAL_ONLY, TRADE_SECRET_LOCAL_ONLY, EXTERNAL_MINIMIZED, EXTERNAL_ALLOWED }
    enum Target { FOUNDER, GPT, AIO, BOTH }
    enum ExternalStatus { READY, CONFIG_REQUIRED, BLOCKED_PRIVACY, BLOCKED_MUTED }

    static final class Turn {
        final String id, speaker, text;
        final Privacy privacy;
        final Instant at;
        Turn(String id,String speaker,String text,Privacy privacy,Instant at){
            this.id=id;this.speaker=speaker;this.text=text;this.privacy=privacy;this.at=at;
        }
    }
    static final class VoiceIntentField {
        final Target target;
        final String intent;
        final int interactionBudget;
        VoiceIntentField(Target target,String intent,int budget){
            this.target=target;this.intent=intent;this.interactionBudget=budget;
        }
    }
    static final class TeachingCapsule {
        final String id, summary;
        final int remainingBudget;
        TeachingCapsule(String id,String summary,int budget){this.id=id;this.summary=summary;this.remainingBudget=budget;}
    }
    static final class LearningDelta {
        final String capsuleId, delta;
        LearningDelta(String capsuleId,String delta){this.capsuleId=capsuleId;this.delta=delta;}
    }

    private State state=State.IDLE;
    private State beforePause=State.IDLE;
    private Mode mode=Mode.AIO;
    private boolean founderMic, externalGranted;
    private int budget=8;
    private final ArrayList<Turn> turns=new ArrayList<>();

    synchronized State state(){return state;}
    synchronized Mode mode(){return mode;}
    synchronized List<Turn> turns(){return List.copyOf(turns);}
    synchronized void selectMode(Mode next){requireOpen();mode=Objects.requireNonNull(next);}
    synchronized void startListening(){requireOpen();founderMic=true;state=State.LISTENING;}
    synchronized void stopListening(){founderMic=false;if(state==State.LISTENING)state=State.IDLE;}
    synchronized void mute(){requireOpen();founderMic=false;state=State.MUTED;}
    synchronized void unmute(){requireOpen();if(state==State.MUTED)state=State.IDLE;}
    synchronized void bargeIn(){requireOpen();founderMic=true;state=State.LISTENING;}
    synchronized void beginResponse(){requireOpen();if(!founderMic&&state!=State.MUTED)state=State.THINKING;}
    synchronized void beginSpeech(){requireOpen();if(state!=State.MUTED)state=State.SPEAKING;}
    synchronized void finishSpeech(){requireOpen();if(state==State.SPEAKING)state=State.IDLE;}
    synchronized void pauseLifecycle(){
        if(state!=State.CLOSED){
            beforePause=state;
            founderMic=false;
            state=State.PAUSED;
        }
    }
    synchronized void resumeLifecycle(){
        requireOpen();
        if(state==State.PAUSED)state=beforePause==State.MUTED?State.MUTED:State.IDLE;
    }
    synchronized void close(){founderMic=false;state=State.CLOSED;}
    synchronized void grantExternalProjection(boolean allowed){requireOpen();externalGranted=allowed;}

    synchronized Turn commit(String speaker,String text,Privacy privacy){
        requireOpen();
        String safeSpeaker=bounded(speaker,64,"VOICE_SPEAKER_INVALID");
        String safeText=bounded(text,4096,"VOICE_TURN_EMPTY");
        Privacy safePrivacy=Objects.requireNonNull(privacy,"VOICE_PRIVACY_REQUIRED");
        if((safePrivacy==Privacy.EXTERNAL_ALLOWED||safePrivacy==Privacy.EXTERNAL_MINIMIZED)&&!externalGranted)
            throw new SecurityException("EXTERNAL_PROJECTION_DENIED");
        Turn t=new Turn(UUID.randomUUID().toString(),safeSpeaker,safeText,safePrivacy,Instant.now());
        turns.add(t);return t;
    }
    synchronized ExternalStatus externalStatus(Privacy privacy){
        Privacy safePrivacy=Objects.requireNonNull(privacy,"VOICE_PRIVACY_REQUIRED");
        if(state==State.MUTED)return ExternalStatus.BLOCKED_MUTED;
        if(safePrivacy==Privacy.LOCAL_ONLY||safePrivacy==Privacy.TRADE_SECRET_LOCAL_ONLY)return ExternalStatus.BLOCKED_PRIVACY;
        return externalGranted?ExternalStatus.READY:ExternalStatus.CONFIG_REQUIRED;
    }
    synchronized VoiceIntentField intent(Target target,String intent){
        requireOpen();
        return new VoiceIntentField(Objects.requireNonNull(target,"VOICE_TARGET_REQUIRED"),
            bounded(intent,4096,"VOICE_INTENT_INVALID"),budget);
    }
    synchronized TeachingCapsule teach(String summary){
        requireOpen();
        if(budget<=0)throw new IllegalStateException("VOICE_INTERACTION_BUDGET_EXHAUSTED");
        budget--;return new TeachingCapsule(UUID.randomUUID().toString(),bounded(summary,4096,"VOICE_TEACHING_INVALID"),budget);
    }
    synchronized LearningDelta learn(TeachingCapsule capsule,String delta){
        requireOpen();
        TeachingCapsule safe=Objects.requireNonNull(capsule,"VOICE_CAPSULE_REQUIRED");
        return new LearningDelta(safe.id,bounded(delta,4096,"VOICE_LEARNING_INVALID"));
    }
    private static String bounded(String value,int max,String code){
        if(value==null)throw new IllegalArgumentException(code);
        String clean=value.trim();
        if(clean.isEmpty()||clean.length()>max)throw new IllegalArgumentException(code);
        return clean;
    }
    private void requireOpen(){if(state==State.CLOSED)throw new IllegalStateException("VOICE_ROOM_CLOSED");}
}
