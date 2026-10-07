package com.aio.founder;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AIO-native exact representation fabric for bounded Android state cells.
 *
 * Logical cells are preserved independently from their selected representation.
 * The portfolio currently promotes only mechanisms with exact mobile courts:
 * RAW, local run field, witness reference, and mirror-witness reference.
 *
 * References are recursively bounded by an escape radius. If a candidate would
 * exceed the allowed dependency depth, the selector falls back to a local form.
 */
final class AioNativeRepresentationFabric {
    enum Kind { RAW, RUN_FIELD, WITNESS, MIRROR_WITNESS }

    static final class Cell {
        final Kind kind;
        final byte[] payload;
        final int reference;
        final int logicalBytes;
        final int depth;
        Cell(Kind kind,byte[] payload,int reference,int logicalBytes,int depth){
            this.kind=kind;this.payload=payload;this.reference=reference;
            this.logicalBytes=logicalBytes;this.depth=depth;
        }
    }

    static final class Manifestation {
        final byte[] bytes;
        final int manifestedCells;
        final int unmanifestedCells;
        Manifestation(byte[] bytes,int manifestedCells,int unmanifestedCells){
            this.bytes=bytes;this.manifestedCells=manifestedCells;this.unmanifestedCells=unmanifestedCells;
        }
    }

    static final class Metrics {
        final int cells,rawCells,runCells,witnessCells,mirrorCells,maxDepth;
        final long logicalBytes,representationBytes;
        Metrics(int cells,int rawCells,int runCells,int witnessCells,int mirrorCells,int maxDepth,
                long logicalBytes,long representationBytes){
            this.cells=cells;this.rawCells=rawCells;this.runCells=runCells;
            this.witnessCells=witnessCells;this.mirrorCells=mirrorCells;this.maxDepth=maxDepth;
            this.logicalBytes=logicalBytes;this.representationBytes=representationBytes;
        }
        double ratio(){
            return representationBytes<=0?1.0:(double)logicalBytes/(double)representationBytes;
        }
    }

    static final class Archive {
        private final List<Cell> cells;
        private final int escapeRadius;
        private final Metrics metrics;

        Archive(List<Cell> cells,int escapeRadius,Metrics metrics){
            this.cells=List.copyOf(cells);this.escapeRadius=escapeRadius;this.metrics=metrics;
        }

        int size(){return cells.size();}
        int escapeRadius(){return escapeRadius;}
        Metrics metrics(){return metrics;}
        Cell cell(int index){return cells.get(index);}

        Manifestation manifest(int index)throws Exception{
            if(index<0||index>=cells.size())throw new IndexOutOfBoundsException("AIO_CELL_INDEX");
            HashSet<Integer> visited=new HashSet<>();
            byte[] bytes=materialize(index,visited,0);
            return new Manifestation(bytes,visited.size(),cells.size()-visited.size());
        }

        List<byte[]> manifestAll()throws Exception{
            ArrayList<byte[]> out=new ArrayList<>(cells.size());
            for(int i=0;i<cells.size();i++)out.add(manifest(i).bytes);
            return out;
        }

        int countKind(Kind kind){
            int count=0;for(Cell cell:cells)if(cell.kind==kind)count++;return count;
        }

        long logicalBytesWithoutMaterializing(){
            long total=0;for(Cell cell:cells)total+=cell.logicalBytes;return total;
        }

        void clearPayloads(){
            for(Cell cell:cells)Arrays.fill(cell.payload,(byte)0);
        }

        private byte[] materialize(int index,Set<Integer> visited,int recursion)throws Exception{
            if(recursion>escapeRadius)throw new IllegalStateException("AIO_ESCAPE_RADIUS_VIOLATED");
            if(!visited.add(index))throw new IllegalStateException("AIO_REPRESENTATION_CYCLE");
            Cell cell=cells.get(index);
            switch(cell.kind){
                case RAW:
                    return Arrays.copyOf(cell.payload,cell.payload.length);
                case RUN_FIELD:
                    return decodeRuns(cell.payload,cell.logicalBytes);
                case WITNESS:{
                    byte[] prior=materialize(cell.reference,visited,recursion+1);
                    if(prior.length!=cell.logicalBytes)throw new IllegalStateException("AIO_WITNESS_LENGTH");
                    return prior;
                }
                case MIRROR_WITNESS:{
                    byte[] prior=materialize(cell.reference,visited,recursion+1);
                    if(prior.length!=cell.logicalBytes)throw new IllegalStateException("AIO_MIRROR_LENGTH");
                    reverseInPlace(prior);return prior;
                }
                default:throw new IllegalStateException("AIO_REPRESENTATION_KIND");
            }
        }
    }

    private AioNativeRepresentationFabric(){}

    static Archive encode(List<byte[]> logicalCells,int escapeRadius)throws Exception{
        if(logicalCells==null)throw new IllegalArgumentException("AIO_CELLS_REQUIRED");
        if(escapeRadius<0||escapeRadius>64)throw new IllegalArgumentException("AIO_ESCAPE_RADIUS_INVALID");

        ArrayList<Cell> cells=new ArrayList<>(logicalCells.size());
        Map<String,Integer> exactAnchor=new HashMap<>();
        long logicalBytes=0,representationBytes=0;
        int raw=0,runs=0,witness=0,mirror=0,maxDepth=0;

        for(byte[] input:logicalCells){
            if(input==null)throw new IllegalArgumentException("AIO_CELL_NULL");
            byte[] logical=Arrays.copyOf(input,input.length);
            logicalBytes+=logical.length;

            String exactKey=key(logical);
            byte[] reversed=Arrays.copyOf(logical,logical.length);reverseInPlace(reversed);
            String reverseKey=key(reversed);
            Integer witnessRef=exactAnchor.get(exactKey);
            Integer mirrorRef=exactAnchor.get(reverseKey);

            byte[] runPayload=encodeRuns(logical);
            long rawCost=1L+logical.length;
            long runCost=5L+runPayload.length;
            long witnessCost=9L;
            long mirrorCost=10L;

            Kind selected=Kind.RAW;
            byte[] payload=logical;
            int reference=-1;
            int depth=0;
            long cost=rawCost;

            if(runCost<cost){
                selected=Kind.RUN_FIELD;payload=runPayload;cost=runCost;
            }

            if(witnessRef!=null){
                Cell parent=cells.get(witnessRef);
                int candidateDepth=parent.depth+1;
                if(candidateDepth<=escapeRadius&&witnessCost<cost){
                    selected=Kind.WITNESS;payload=new byte[0];reference=witnessRef;
                    depth=candidateDepth;cost=witnessCost;
                }
            }

            if(mirrorRef!=null){
                Cell parent=cells.get(mirrorRef);
                int candidateDepth=parent.depth+1;
                if(candidateDepth<=escapeRadius&&mirrorCost<cost){
                    selected=Kind.MIRROR_WITNESS;payload=new byte[0];reference=mirrorRef;
                    depth=candidateDepth;cost=mirrorCost;
                }
            }

            Cell cell=new Cell(selected,payload,reference,logical.length,depth);
            int index=cells.size();cells.add(cell);
            representationBytes+=cost;maxDepth=Math.max(maxDepth,depth);
            switch(selected){
                case RAW:raw++;break;
                case RUN_FIELD:runs++;break;
                case WITNESS:witness++;break;
                case MIRROR_WITNESS:mirror++;break;
            }

            // Keep the shallowest exact witness anchor instead of blindly chaining
            // through the latest reference. A later local representation can replace
            // a deeper reference and reset the dependency radius.
            Integer priorAnchor=exactAnchor.get(exactKey);
            if(priorAnchor==null||cell.depth<cells.get(priorAnchor).depth)
                exactAnchor.put(exactKey,index);
            Arrays.fill(reversed,(byte)0);
            if(selected!=Kind.RAW)Arrays.fill(logical,(byte)0);
            if(selected!=Kind.RUN_FIELD)Arrays.fill(runPayload,(byte)0);
        }

        Metrics metrics=new Metrics(cells.size(),raw,runs,witness,mirror,maxDepth,logicalBytes,representationBytes);
        return new Archive(cells,escapeRadius,metrics);
    }

    private static byte[] encodeRuns(byte[] input){
        if(input.length==0)return new byte[0];
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int i=0;
        while(i<input.length){
            byte value=input[i];int count=1;
            while(i+count<input.length&&input[i+count]==value&&count<255)count++;
            out.write(count);out.write(value&0xff);i+=count;
        }
        return out.toByteArray();
    }

    private static byte[] decodeRuns(byte[] payload,int expected)throws Exception{
        if((payload.length&1)!=0)throw new IllegalStateException("AIO_RUN_FIELD_ENCODING");
        ByteArrayOutputStream out=new ByteArrayOutputStream(expected);
        for(int i=0;i<payload.length;i+=2){
            int count=payload[i]&0xff;
            if(count<1)throw new IllegalStateException("AIO_RUN_FIELD_ZERO");
            byte value=payload[i+1];
            if(out.size()+count>expected)throw new IllegalStateException("AIO_RUN_FIELD_OVERFLOW");
            for(int j=0;j<count;j++)out.write(value);
        }
        if(out.size()!=expected)throw new IllegalStateException("AIO_RUN_FIELD_LENGTH");
        return out.toByteArray();
    }

    private static String key(byte[] bytes){
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void reverseInPlace(byte[] bytes){
        for(int a=0,b=bytes.length-1;a<b;a++,b--){
            byte t=bytes[a];bytes[a]=bytes[b];bytes[b]=t;
        }
    }
}
