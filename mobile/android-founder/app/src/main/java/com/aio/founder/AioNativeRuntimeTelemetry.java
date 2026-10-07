package com.aio.founder;

import java.util.concurrent.atomic.AtomicLong;

final class AioNativeRuntimeTelemetry {
    static final class Snapshot {
        final long stateWrites,cellArchiveWrites,wholePortfolioWrites;
        final long logicalBytes,storedBytes;
        final long selectiveQueries,requestedLogicalCells,manifestedPhysicalCells,availableLogicalCells;
        final long causalActions,manifestedDependencies,unmanifestedDependencies;

        Snapshot(long stateWrites,long cellArchiveWrites,long wholePortfolioWrites,
                 long logicalBytes,long storedBytes,long selectiveQueries,long requestedLogicalCells,
                 long manifestedPhysicalCells,long availableLogicalCells,long causalActions,
                 long manifestedDependencies,long unmanifestedDependencies){
            this.stateWrites=stateWrites;this.cellArchiveWrites=cellArchiveWrites;
            this.wholePortfolioWrites=wholePortfolioWrites;this.logicalBytes=logicalBytes;this.storedBytes=storedBytes;
            this.selectiveQueries=selectiveQueries;this.requestedLogicalCells=requestedLogicalCells;
            this.manifestedPhysicalCells=manifestedPhysicalCells;this.availableLogicalCells=availableLogicalCells;
            this.causalActions=causalActions;this.manifestedDependencies=manifestedDependencies;
            this.unmanifestedDependencies=unmanifestedDependencies;
        }

        String summary(){
            double representationRatio=storedBytes<=0?1.0:(double)logicalBytes/(double)storedBytes;
            double cellNonManifestation=availableLogicalCells<=0?0.0:
                1.0-(double)manifestedPhysicalCells/(double)availableLogicalCells;
            double dependencyNonManifestation=(manifestedDependencies+unmanifestedDependencies)<=0?0.0:
                (double)unmanifestedDependencies/(double)(manifestedDependencies+unmanifestedDependencies);
            return "AIO NATIVE RUNTIME TELEMETRY"+
                "\nState writes: "+stateWrites+" | cell="+cellArchiveWrites+" | whole="+wholePortfolioWrites+
                "\nLogical/stored: "+logicalBytes+"B / "+storedBytes+"B | effective ratio="+
                    String.format(java.util.Locale.ROOT,"%.3fx",representationRatio)+
                "\nSelective queries: "+selectiveQueries+" | requested="+requestedLogicalCells+
                    " | physical manifests="+manifestedPhysicalCells+
                    " | latent/non-manifested="+String.format(java.util.Locale.ROOT,"%.2f%%",cellNonManifestation*100.0)+
                "\nCausal actions: "+causalActions+" | dependencies manifested="+manifestedDependencies+
                    " | avoided="+unmanifestedDependencies+
                    " | non-manifestation="+String.format(java.util.Locale.ROOT,"%.2f%%",dependencyNonManifestation*100.0);
        }
    }

    private static final AtomicLong stateWrites=new AtomicLong();
    private static final AtomicLong cellArchiveWrites=new AtomicLong();
    private static final AtomicLong wholePortfolioWrites=new AtomicLong();
    private static final AtomicLong logicalBytes=new AtomicLong();
    private static final AtomicLong storedBytes=new AtomicLong();
    private static final AtomicLong selectiveQueries=new AtomicLong();
    private static final AtomicLong requestedLogicalCells=new AtomicLong();
    private static final AtomicLong manifestedPhysicalCells=new AtomicLong();
    private static final AtomicLong availableLogicalCells=new AtomicLong();
    private static final AtomicLong causalActions=new AtomicLong();
    private static final AtomicLong manifestedDependencies=new AtomicLong();
    private static final AtomicLong unmanifestedDependencies=new AtomicLong();

    private AioNativeRuntimeTelemetry(){}

    static void recordState(long logical,long stored,boolean cellArchive){
        stateWrites.incrementAndGet();
        if(cellArchive)cellArchiveWrites.incrementAndGet();else wholePortfolioWrites.incrementAndGet();
        logicalBytes.addAndGet(Math.max(0,logical));
        storedBytes.addAndGet(Math.max(0,stored));
    }

    static void recordSelective(int requested,int manifested,int available){
        selectiveQueries.incrementAndGet();
        requestedLogicalCells.addAndGet(Math.max(0,requested));
        manifestedPhysicalCells.addAndGet(Math.max(0,manifested));
        availableLogicalCells.addAndGet(Math.max(0,available));
    }

    static void recordCausal(int manifested,int total){
        causalActions.incrementAndGet();
        int safeManifested=Math.max(0,manifested),safeTotal=Math.max(safeManifested,total);
        manifestedDependencies.addAndGet(safeManifested);
        unmanifestedDependencies.addAndGet(safeTotal-safeManifested);
    }

    static Snapshot snapshot(){
        return new Snapshot(
            stateWrites.get(),cellArchiveWrites.get(),wholePortfolioWrites.get(),
            logicalBytes.get(),storedBytes.get(),selectiveQueries.get(),requestedLogicalCells.get(),
            manifestedPhysicalCells.get(),availableLogicalCells.get(),causalActions.get(),
            manifestedDependencies.get(),unmanifestedDependencies.get());
    }
}
