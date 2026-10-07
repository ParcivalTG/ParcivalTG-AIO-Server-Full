package com.aio.founder;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidAioNativeArchitectureContractTest {
    @SuppressWarnings("unchecked")
    @Test public void canonicalNativeArchitectureMatchesProductionKinds()throws Exception{
        byte[] bytes;
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("android-aio-native-internals-v1.json")){
            assertNotNull(in);bytes=in.readAllBytes();
        }
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(bytes,64*1024);
        assertEquals("aio.android.native-internals.v1",root.get("schema"));

        StrictProjectionJson.ObjectValue boundary=(StrictProjectionJson.ObjectValue)root.get("boundary");
        assertEquals("COMPATIBILITY_SHELL_ONLY",boundary.get("androidFrameworkRole"));
        assertEquals("EDGE_PROJECTION_ONLY",boundary.get("jsonRole"));
        assertEquals("AUTHORITATIVE_INTERNAL_SUBSTRATE",boundary.get("aioNativeStateRole"));

        StrictProjectionJson.ObjectValue rules=(StrictProjectionJson.ObjectValue)root.get("promotionRules");
        for(String key:new String[]{
            "bitExactRequired","fullStoredCostRequired","sameWorkloadEvidenceRequired",
            "rawFallbackRequired","encryptedAtRestRequired","selectiveMaterializationPreferred",
            "causalNonManifestationPreferred","unprovenSpecialistsMayNotBecomeDefault"})
            assertEquals(key,Boolean.TRUE,rules.get(key));

        Set<String> portfolio=new HashSet<>();
        for(Object row:(List<Object>)root.get("productionRepresentationPortfolio"))portfolio.add((String)row);
        Set<String> runtimePortfolio=new HashSet<>();
        for(AioNativeStateCodec.Kind kind:AioNativeStateCodec.Kind.values())runtimePortfolio.add(kind.name());
        assertEquals(runtimePortfolio,portfolio);

        Set<String> cells=new HashSet<>();
        for(Object row:(List<Object>)root.get("cellFabricKinds"))cells.add((String)row);
        Set<String> runtimeCells=new HashSet<>();
        for(AioNativeRepresentationFabric.Kind kind:AioNativeRepresentationFabric.Kind.values())runtimeCells.add(kind.name());
        assertEquals(runtimeCells,cells);

        Set<String> deps=new HashSet<>();
        for(Object row:(List<Object>)root.get("causalDependencyUniverse"))deps.add((String)row);
        Set<String> runtimeDeps=new HashSet<>();
        for(AioAndroidCausalPlanner.Dependency dep:AioAndroidCausalPlanner.Dependency.values())runtimeDeps.add(dep.name());
        assertEquals(runtimeDeps,deps);

        Set<String> routes=new HashSet<>();
        for(Object row:(List<Object>)root.get("localReasoningRoutes"))routes.add((String)row);
        Set<String> runtimeRoutes=new HashSet<>();
        for(AioLocalReasoningRouter.Route route:AioLocalReasoningRouter.Route.values())runtimeRoutes.add(route.name());
        assertEquals(runtimeRoutes,routes);
    }
}
