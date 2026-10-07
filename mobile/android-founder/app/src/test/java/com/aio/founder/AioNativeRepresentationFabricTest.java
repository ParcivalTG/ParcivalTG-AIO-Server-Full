package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class AioNativeRepresentationFabricTest {
    private static byte[] text(String value){return value.getBytes(StandardCharsets.UTF_8);}

    @Test public void portfolioRoundTripsRawRunsWitnessAndMirror()throws Exception{
        List<byte[]> logical=List.of(
            text("abcdefghijklmnopqrstuvwxyz"),
            new byte[]{7,7,7,7,7,7,7,7,7,7},
            text("abcdefghijklmnopqrstuvwxyz"),
            text("zyxwvutsrqponmlkjihgfedcba"),
            text("unique-cell")
        );
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,3);
        for(int i=0;i<logical.size();i++)
            assertArrayEquals(logical.get(i),archive.manifest(i).bytes);
        assertTrue(archive.countKind(AioNativeRepresentationFabric.Kind.RUN_FIELD)>=1);
        assertTrue(archive.countKind(AioNativeRepresentationFabric.Kind.WITNESS)>=1);
        assertTrue(archive.countKind(AioNativeRepresentationFabric.Kind.MIRROR_WITNESS)>=1);
    }

    @Test public void witnessCollapseReproduces7500To85PatternFamily()throws Exception{
        ArrayList<byte[]> logical=new ArrayList<>();
        ArrayList<byte[]> witnesses=new ArrayList<>();
        for(int i=0;i<85;i++)witnesses.add(text("witness-"+i+"-"+"x".repeat(40)));
        for(int i=0;i<7500;i++)logical.add(witnesses.get(i%85));

        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,2);
        assertTrue(archive.countKind(AioNativeRepresentationFabric.Kind.WITNESS)>7000);
        assertTrue(archive.metrics().representationBytes<archive.metrics().logicalBytes/3);
        assertArrayEquals(logical.get(7499),archive.manifest(7499).bytes);
    }

    @Test public void randomCellsCorrectlyStayLocal()throws Exception{
        Random random=new Random(9917);
        ArrayList<byte[]> logical=new ArrayList<>();
        for(int i=0;i<128;i++){
            byte[] row=new byte[96];random.nextBytes(row);logical.add(row);
        }
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,4);
        assertEquals(128,archive.countKind(AioNativeRepresentationFabric.Kind.RAW));
        assertEquals(0,archive.countKind(AioNativeRepresentationFabric.Kind.WITNESS));
        assertEquals(0,archive.countKind(AioNativeRepresentationFabric.Kind.MIRROR_WITNESS));
    }

    @Test public void escapeRadiusCapsRecursiveWitnessDepth()throws Exception{
        ArrayList<byte[]> logical=new ArrayList<>();
        byte[] repeated=text("this is repeated structural state with enough bytes to make refs win");
        for(int i=0;i<30;i++)logical.add(repeated);
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,2);
        assertTrue(archive.metrics().maxDepth<=2);
        for(int i=0;i<logical.size();i++)assertArrayEquals(repeated,archive.manifest(i).bytes);
    }

    @Test public void selectiveManifestationLeavesUnrelatedCellsUnrealized()throws Exception{
        ArrayList<byte[]> logical=new ArrayList<>();
        for(int i=0;i<64;i++)logical.add(text("branch-"+i+"-"+"q".repeat(64)));
        logical.add(logical.get(7));
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,3);
        AioNativeRepresentationFabric.Manifestation m=archive.manifest(64);
        assertArrayEquals(logical.get(7),m.bytes);
        assertTrue(m.manifestedCells<=2);
        assertTrue(m.unmanifestedCells>=63);
    }

    @Test public void mirrorRepresentationIsExactAndSelective()throws Exception{
        byte[] base=text("AIO-native-mirror-structure-123456789");
        byte[] mirror=Arrays.copyOf(base,base.length);
        for(int a=0,b=mirror.length-1;a<b;a++,b--){byte t=mirror[a];mirror[a]=mirror[b];mirror[b]=t;}
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(List.of(base,mirror),2);
        assertEquals(AioNativeRepresentationFabric.Kind.MIRROR_WITNESS,archive.cell(1).kind);
        AioNativeRepresentationFabric.Manifestation m=archive.manifest(1);
        assertArrayEquals(mirror,m.bytes);
        assertEquals(2,m.manifestedCells);
    }

    @Test public void runFieldNeverWinsWhenItExpandsRandomishInput()throws Exception{
        byte[] alternating=new byte[100];
        for(int i=0;i<alternating.length;i++)alternating[i]=(byte)i;
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(List.of(alternating),1);
        assertEquals(AioNativeRepresentationFabric.Kind.RAW,archive.cell(0).kind);
    }

    @Test public void fourThousandCellSelectiveManifestationKeepsAlmostAllStateUnrealized()throws Exception{
        ArrayList<byte[]> logical=new ArrayList<>();
        for(int i=0;i<4095;i++)logical.add(text("unique-causal-cell-"+i+"-"+"z".repeat(48)));
        logical.add(logical.get(1234));
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,4);
        AioNativeRepresentationFabric.Manifestation manifestation=archive.manifest(4095);
        assertArrayEquals(logical.get(1234),manifestation.bytes);
        assertTrue(manifestation.manifestedCells<=2);
        assertTrue(manifestation.unmanifestedCells>=4094);
        assertTrue((double)manifestation.unmanifestedCells/archive.size()>0.999);
    }

    @Test public void fullLogicalByteCountNeedsNoMaterialization()throws Exception{
        ArrayList<byte[]> logical=new ArrayList<>();
        long expected=0;
        for(int i=0;i<2048;i++){
            byte[] row=text("cell-"+i+"-"+"r".repeat(i%17));
            logical.add(row);expected+=row.length;
        }
        AioNativeRepresentationFabric.Archive archive=AioNativeRepresentationFabric.encode(logical,4);
        assertEquals(expected,archive.logicalBytesWithoutMaterializing());
    }

    @Test public void exactDecodeDetectsInvalidRadiusRequest(){
        try{AioNativeRepresentationFabric.encode(List.of(text("x")),-1);fail();}
        catch(Exception expected){assertEquals("AIO_ESCAPE_RADIUS_INVALID",expected.getMessage());}
    }
}
