package com.aio.founder;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Recursive witness-collapse projection for Founder UI. Raw encrypted evidence remains preserved. */
final class AioEvidenceCollapse {
    static String project(JSONArray evidence,int maxGroups){
        LinkedHashMap<String,Group> groups=new LinkedHashMap<>();
        ArrayList<byte[]> codes=new ArrayList<>();
        for(int i=0;i<evidence.length();i++){
            JSONObject row=evidence.optJSONObject(i);if(row==null)continue;
            String code=row.optString("code","UNKNOWN");
            codes.add(code.getBytes(StandardCharsets.UTF_8));
            Group g=groups.get(code);
            if(g==null){g=new Group(code);groups.put(code,g);}
            g.count++;g.lastAt=Math.max(g.lastAt,row.optLong("atUnixMs",0));
            if(row.has("requesterRoundTripMs")){
                long v=row.optLong("requesterRoundTripMs",-1);
                if(v>=0){g.samples++;g.sumMs+=v;g.maxMs=Math.max(g.maxMs,v);}
            }
        }

        StringBuilder out=new StringBuilder();
        if(!codes.isEmpty()){
            try{
                AioNativeRepresentationFabric.Archive archive=
                    AioNativeRepresentationFabric.encode(codes,2);
                AioNativeRepresentationFabric.Metrics m=archive.metrics();
                AioNativeRepresentationFabric.Manifestation last=archive.manifest(archive.size()-1);
                out.append("AIO representation field: logical=")
                   .append(m.logicalBytes).append("B | represented=")
                   .append(m.representationBytes).append("B | ratio=")
                   .append(String.format(Locale.ROOT,"%.2fx",m.ratio()))
                   .append(" | witnesses=").append(m.witnessCells)
                   .append(" | mirrors=").append(m.mirrorCells)
                   .append(" | runs=").append(m.runCells)
                   .append(" | maxDepth=").append(m.maxDepth)
                   .append(" | lastManifested=").append(last.manifestedCells)
                   .append('/').append(archive.size()).append("\n");
            }catch(Exception failure){
                out.append("AIO representation field: HOLD ")
                   .append(failure.getClass().getSimpleName()).append("\n");
            }finally{
                for(byte[] row:codes)java.util.Arrays.fill(row,(byte)0);
            }
        }

        int skip=Math.max(0,groups.size()-maxGroups),index=0;
        for(Map.Entry<String,Group> e:groups.entrySet()){
            if(index++<skip)continue;
            Group g=e.getValue();
            out.append(g.code).append(" x").append(g.count)
               .append(" | last=").append(g.lastAt);
            if(g.samples>0)out.append(" | avg=").append(g.sumMs/g.samples)
                .append("ms | max=").append(g.maxMs).append("ms");
            out.append('\n');
        }
        if(groups.size()>maxGroups)
            out.insert(0,"collapsed groups: "+groups.size()+" (showing "+maxGroups+")\n");
        return out.length()==0?"No local evidence yet.":out.toString();
    }
    private static final class Group{
        final String code;int count,samples;long lastAt,sumMs,maxMs;
        Group(String code){this.code=code;}
    }
    private AioEvidenceCollapse(){}
}
