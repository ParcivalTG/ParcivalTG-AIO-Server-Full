package com.aio.founder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * AIO-native reversible representation portfolio for private Android state.
 *
 * Android/JSON remains a compatibility projection at the edge. Internally, exact
 * representations compete by full stored-character cost. If none wins, the original
 * UTF-8 string is retained byte-for-byte.
 */
final class AioNativeStateCodec {
    private static final String PREFIX="@AIOREP1:";
    static final int MAX_RAW_BYTES=2*1024*1024;

    enum Kind {
        HFMS_HFR1_RESIDUAL,
        STRUCTURAL_DEFLATE,
        DEFLATE,
        STRUCTURAL,
        RUNLEN_CONTINUATION,
        REPEAT_GENERATOR,
        MOBIUS_MIRROR
    }

    static final class Decision {
        final String stored;
        final Kind kind;
        final int rawBytes,payloadBytes,storedChars;
        final boolean transformed;
        Decision(String stored,Kind kind,int rawBytes,int payloadBytes,int storedChars,boolean transformed){
            this.stored=stored;this.kind=kind;this.rawBytes=rawBytes;this.payloadBytes=payloadBytes;
            this.storedChars=storedChars;this.transformed=transformed;
        }
        int savedCharacters(int rawCharacters){return Math.max(0,rawCharacters-storedChars);}
    }

    private static final byte[][] STRUCTURAL_TOKENS = new byte[][]{
        bytes("\"objectiveFingerprint\":"),bytes("\"requesterRoundTripMs\":"),bytes("\"timestampUnixMs\":"),
        bytes("\"privacyClass\":"),bytes("\"objectiveVersion\":"),bytes("\"serverReceipt\":"),
        bytes("\"objectiveId\":"),bytes("\"requestId\":"),bytes("\"intentId\":"),
        bytes("\"provider\":"),bytes("\"schema\":"),bytes("\"state\":"),
        bytes("\"atUnixMs\":"),bytes("\"capability\":"),bytes("\"action\":"),
        bytes("\"clientId\":"),bytes("\"status\":"),bytes("\"role\":"),
        bytes("\"text\":"),bytes("\"code\":"),bytes("\"args\":")
    };

    private AioNativeStateCodec(){}

    static String encodeForStorage(String raw)throws Exception{
        return decide(raw).stored;
    }

    static Decision decide(String raw)throws Exception{
        if(raw==null)throw new IllegalArgumentException("AIO_STATE_REQUIRED");
        byte[] source=raw.getBytes(StandardCharsets.UTF_8);
        if(source.length>MAX_RAW_BYTES)throw new IllegalArgumentException("AIO_STATE_BUDGET");
        List<Candidate> candidates=new ArrayList<>();
        try{
            if(AioHfmsResidualCodec.sensorEligible(source)){
                AioHfmsResidualCodec.Encoded hfms=AioHfmsResidualCodec.encodeDefault(source);
                candidates.add(new Candidate(Kind.HFMS_HFR1_RESIDUAL,hfms.stream));
            }
            byte[] structural=structuralEncode(source);
            candidates.add(new Candidate(Kind.STRUCTURAL_DEFLATE,deflate(structural)));
            candidates.add(new Candidate(Kind.DEFLATE,deflate(source)));
            candidates.add(new Candidate(Kind.STRUCTURAL,structural));
            candidates.add(new Candidate(Kind.RUNLEN_CONTINUATION,runLengthEncode(source)));
            byte[] repeat=repeatGenerator(source);if(repeat!=null)candidates.add(new Candidate(Kind.REPEAT_GENERATOR,repeat));
            byte[] mirror=mirrorEncode(source);if(mirror!=null)candidates.add(new Candidate(Kind.MOBIUS_MIRROR,mirror));

            String hash=sha256(source);
            Candidate best=null;String bestStored=null;
            for(Candidate candidate:candidates){
                String stored=pack(candidate.kind,source.length,hash,candidate.payload);
                if(bestStored==null||stored.length()<bestStored.length()){
                    best=candidate;bestStored=stored;
                }
            }
            if(bestStored==null||bestStored.length()>=raw.length())
                return new Decision(raw,null,source.length,source.length,raw.length(),false);
            int payloadBytes=best.payload.length;
            return new Decision(bestStored,best.kind,source.length,payloadBytes,bestStored.length(),true);
        }finally{
            Arrays.fill(source,(byte)0);
            for(Candidate candidate:candidates)if(candidate.payload!=null)Arrays.fill(candidate.payload,(byte)0);
        }
    }

    static String decodeFromStorage(String stored)throws Exception{
        if(stored==null)return null;
        if(!stored.startsWith(PREFIX))return stored;
        String[] parts=stored.split(":",5);
        if(parts.length!=5||!("@AIOREP1".equals(parts[0])))throw new SecurityException("AIO_STATE_HEADER");
        Kind kind;
        try{kind=Kind.valueOf(parts[1]);}catch(Exception invalid){throw new SecurityException("AIO_STATE_KIND");}
        int rawLength;
        try{rawLength=Integer.parseInt(parts[2]);}catch(Exception invalid){throw new SecurityException("AIO_STATE_LENGTH");}
        if(rawLength<0||rawLength>MAX_RAW_BYTES)throw new SecurityException("AIO_STATE_LENGTH");
        String expectedHash=parts[3];
        if(!expectedHash.matches("[0-9a-f]{64}"))throw new SecurityException("AIO_STATE_HASH");
        byte[] payload;
        try{payload=Base64.getDecoder().decode(parts[4]);}
        catch(IllegalArgumentException invalid){throw new SecurityException("AIO_STATE_BASE64");}
        byte[] raw=null;
        try{
            switch(kind){
                case HFMS_HFR1_RESIDUAL: raw=AioHfmsResidualCodec.decode(payload);break;
                case STRUCTURAL_DEFLATE: raw=structuralDecode(inflate(payload,rawLength*4+4096));break;
                case DEFLATE: raw=inflate(payload,rawLength);break;
                case STRUCTURAL: raw=structuralDecode(payload);break;
                case RUNLEN_CONTINUATION: raw=runLengthDecode(payload,rawLength);break;
                case REPEAT_GENERATOR: raw=repeatDecode(payload,rawLength);break;
                case MOBIUS_MIRROR: raw=mirrorDecode(payload,rawLength);break;
                default: throw new SecurityException("AIO_STATE_KIND");
            }
            if(raw.length!=rawLength)throw new SecurityException("AIO_STATE_RECONSTRUCTION_LENGTH");
            if(!sha256(raw).equals(expectedHash))throw new SecurityException("AIO_STATE_RECONSTRUCTION_HASH");
            return new String(raw,StandardCharsets.UTF_8);
        }finally{
            Arrays.fill(payload,(byte)0);
            if(raw!=null)Arrays.fill(raw,(byte)0);
        }
    }

    static String describeStored(String stored){
        if(stored==null)return "EMPTY";
        if(!stored.startsWith(PREFIX))return "RAW_UTF8 | chars="+stored.length();
        String[] parts=stored.split(":",5);
        if(parts.length<4)return "CORRUPT_AIO_STATE";
        return parts[1]+" | rawBytes="+parts[2]+" | storedChars="+stored.length();
    }

    private static String pack(Kind kind,int rawLength,String hash,byte[] payload){
        return PREFIX+kind.name()+":"+rawLength+":"+hash+":"+Base64.getEncoder().encodeToString(payload);
    }

    private static byte[] deflate(byte[] input){
        Deflater deflater=new Deflater(6,false);
        byte[] buffer=new byte[Math.max(64,input.length+128)];
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{
            deflater.setInput(input);deflater.finish();
            while(!deflater.finished()){
                int n=deflater.deflate(buffer);
                if(n<=0&&!deflater.needsInput())throw new IllegalStateException("AIO_DEFLATE_STALL");
                out.write(buffer,0,n);
            }
            return out.toByteArray();
        }finally{deflater.end();Arrays.fill(buffer,(byte)0);}
    }

    private static byte[] inflate(byte[] input,int maximum)throws Exception{
        if(maximum<0||maximum>MAX_RAW_BYTES*4+4096)throw new SecurityException("AIO_INFLATE_BUDGET");
        Inflater inflater=new Inflater(false);
        byte[] buffer=new byte[8192];
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{
            inflater.setInput(input);
            while(!inflater.finished()){
                int n=inflater.inflate(buffer);
                if(n==0){
                    if(inflater.needsDictionary()||inflater.needsInput())throw new SecurityException("AIO_INFLATE_TRUNCATED");
                    throw new SecurityException("AIO_INFLATE_STALL");
                }
                if(out.size()+n>maximum)throw new SecurityException("AIO_INFLATE_BUDGET");
                out.write(buffer,0,n);
            }
            return out.toByteArray();
        }finally{inflater.end();Arrays.fill(buffer,(byte)0);}
    }

    private static byte[] structuralEncode(byte[] input){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<input.length;){
            if(input[i]==0){out.write(0);out.write(0);i++;continue;}
            int match=-1;
            for(int t=0;t<STRUCTURAL_TOKENS.length;t++){
                byte[] token=STRUCTURAL_TOKENS[t];
                if(i+token.length>input.length)continue;
                boolean same=true;
                for(int j=0;j<token.length;j++)if(input[i+j]!=token[j]){same=false;break;}
                if(same){match=t;break;}
            }
            if(match>=0){out.write(0);out.write(match+1);i+=STRUCTURAL_TOKENS[match].length;}
            else{out.write(input[i]&0xff);i++;}
        }
        return out.toByteArray();
    }

    private static byte[] structuralDecode(byte[] input)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<input.length;i++){
            int value=input[i]&0xff;
            if(value!=0){out.write(value);continue;}
            if(++i>=input.length)throw new SecurityException("AIO_STRUCTURAL_TRUNCATED");
            int code=input[i]&0xff;
            if(code==0){out.write(0);continue;}
            if(code<1||code>STRUCTURAL_TOKENS.length)throw new SecurityException("AIO_STRUCTURAL_TOKEN");
            out.write(STRUCTURAL_TOKENS[code-1]);
        }
        return out.toByteArray();
    }

    private static byte[] runLengthEncode(byte[] input){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<input.length;){
            int j=i+1;
            while(j<input.length&&input[j]==input[i]&&j-i<255)j++;
            int run=j-i;
            if(run>=4||(input[i]&0xff)==255){
                out.write(255);out.write(run);out.write(input[i]&0xff);
            }else out.write(input,i,run);
            i=j;
        }
        return out.toByteArray();
    }

    private static byte[] runLengthDecode(byte[] input,int rawLength)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<input.length;i++){
            int value=input[i]&0xff;
            if(value!=255){out.write(value);continue;}
            if(i+2>=input.length)throw new SecurityException("AIO_RUNLEN_TRUNCATED");
            int run=input[++i]&0xff;int repeated=input[++i]&0xff;
            if(run<1)throw new SecurityException("AIO_RUNLEN_COUNT");
            for(int n=0;n<run;n++)out.write(repeated);
            if(out.size()>rawLength)throw new SecurityException("AIO_RUNLEN_BUDGET");
        }
        return out.toByteArray();
    }

    private static byte[] repeatGenerator(byte[] input){
        int maximum=Math.min(256,input.length/2);
        for(int period=1;period<=maximum;period++){
            boolean same=true;
            for(int i=period;i<input.length;i++)if(input[i]!=input[i%period]){same=false;break;}
            if(same)return Arrays.copyOf(input,period);
        }
        return null;
    }

    private static byte[] repeatDecode(byte[] generator,int rawLength)throws Exception{
        if(generator.length<1||generator.length>256)throw new SecurityException("AIO_GENERATOR_BOUNDS");
        byte[] out=new byte[rawLength];
        for(int i=0;i<out.length;i++)out[i]=generator[i%generator.length];
        return out;
    }

    private static byte[] mirrorEncode(byte[] input){
        if((input.length&1)!=0)return null;
        int half=input.length/2;
        for(int i=0;i<half;i++)if(input[half+i]!=input[half-1-i])return null;
        return Arrays.copyOf(input,half);
    }

    private static byte[] mirrorDecode(byte[] half,int rawLength)throws Exception{
        if((rawLength&1)!=0||half.length!=rawLength/2)throw new SecurityException("AIO_MIRROR_BOUNDS");
        byte[] out=new byte[rawLength];
        System.arraycopy(half,0,out,0,half.length);
        for(int i=0;i<half.length;i++)out[half.length+i]=half[half.length-1-i];
        return out;
    }

    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        try{
            StringBuilder out=new StringBuilder(64);
            for(byte value:digest)out.append(String.format(Locale.ROOT,"%02x",value&0xff));
            return out.toString();
        }finally{Arrays.fill(digest,(byte)0);}
    }

    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}

    private static final class Candidate{
        final Kind kind;final byte[] payload;
        Candidate(Kind kind,byte[] payload){this.kind=kind;this.payload=payload;}
    }
}
