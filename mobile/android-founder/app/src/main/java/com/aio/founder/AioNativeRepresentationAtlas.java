package com.aio.founder;

import java.util.List;

final class AioNativeRepresentationAtlas {
    enum Status { QUALIFIED_ANDROID_CORE, VALIDATED_TARGETED_ANDROID_PORT_PENDING, EXPERIMENTAL_HELD }

    static final class Family {
        final String name;
        final Status status;
        final String role;
        final String promotionGate;
        Family(String name,Status status,String role,String promotionGate){
            this.name=name;this.status=status;this.role=role;this.promotionGate=promotionGate;
        }
    }

    private static final List<Family> FAMILIES=List.of(
        new Family("WITNESS_COLLAPSE",Status.QUALIFIED_ANDROID_CORE,
            "Collapse repeated exact state cells into reconstructible witnesses",
            "Exact decode + bounded memory"),
        new Family("MIRROR_WITNESS",Status.QUALIFIED_ANDROID_CORE,
            "Represent reverse-equivalent cells by exact mirror relation",
            "Exact decode + cost win"),
        new Family("RUN_FIELD",Status.QUALIFIED_ANDROID_CORE,
            "Local repeated-byte field representation",
            "Exact decode + lower representation cost"),
        new Family("DEPENDENCY_ESCAPE_RADIUS",Status.QUALIFIED_ANDROID_CORE,
            "Bound recursive reconstruction depth and fall back locally",
            "Depth never exceeds configured radius"),
        new Family("SELECTIVE_MANIFESTATION",Status.QUALIFIED_ANDROID_CORE,
            "Materialize only requested causal representation cells",
            "Exact selected result + unrelated cells unmanifested"),
        new Family("COMPRESSED_DOMAIN_QUERY",Status.QUALIFIED_ANDROID_CORE,
            "Answer representation metadata queries without full manifestation",
            "No payload decode required"),
        new Family("STRUCTURAL_TOKEN_PORTFOLIO",Status.QUALIFIED_ANDROID_CORE,
            "Factor recurring AIO/JSON structural tokens before entropy coding",
            "Exact SHA-verified reconstruction + cost win"),
        new Family("REPEAT_GENERATOR",Status.QUALIFIED_ANDROID_CORE,
            "Represent exactly periodic Android state from a short generator",
            "Exact SHA-verified reconstruction + full-cost win"),
        new Family("MOBIUS_MIRROR_STATE",Status.QUALIFIED_ANDROID_CORE,
            "Represent exact half+reverse-half state with one half witness",
            "Exact SHA-verified reconstruction + full-cost win"),

        new Family("HFMS",Status.VALIDATED_TARGETED_ANDROID_PORT_PENDING,
            "Shared generator + recursive fractal/mirror representation; previously exact on targeted structural workloads",
            "Port exact mechanism to Android and beat current Android portfolio on the same workload including metadata"),
        new Family("CW_HFMS_V3",Status.VALIDATED_TARGETED_ANDROID_PORT_PENDING,
            "Collision-wave structural representation; previously exact and strongly positive on mosaic/structural corpora",
            "Android same-workload resource/latency court versus current portfolio"),
        new Family("MOSAIC_PROJECTION_PORTFOLIO_V7",Status.VALIDATED_TARGETED_ANDROID_PORT_PENDING,
            "Projection/transposition regional portfolio with prior exact large structural gains",
            "Android exact decode + mobile memory/latency/cost win"),
        new Family("TRI_INFINITY",Status.EXPERIMENTAL_HELD,
            "Multi-coordinate triadic completion/representation",
            "Held-out Android court + deterministic decode"),
        new Family("TEMPORAL_RESIDUE_FIELD",Status.EXPERIMENTAL_HELD,
            "Reuse revisable temporal residues across state transitions",
            "Physical Android latency/memory win with zero stale-state errors"),
        new Family("VARIABLE_MASS_COLLISION_FIELD",Status.EXPERIMENTAL_HELD,
            "Information-mass weighted collision/resonance representation",
            "Android same-workload causal win beyond simpler portfolio")
    );

    private AioNativeRepresentationAtlas(){}

    static List<Family> families(){return FAMILIES;}

    static String summary(){
        StringBuilder out=new StringBuilder("AIO NATIVE REPRESENTATION ATLAS");
        for(Family family:FAMILIES){
            out.append("\n").append(family.name)
                .append(": ").append(family.status)
                .append(" | ").append(family.role);
        }
        return out.toString();
    }
}
