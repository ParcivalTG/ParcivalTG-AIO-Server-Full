package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioPresenceFieldTest {
    @Test public void helloAndStatusProjectTruthfully(){
        AioPresenceField field=new AioPresenceField();
        field.absorbHello("founder","aio.core.v1");
        AioPresenceField.AndroidShadow hello=field.projectAndroidShadow();
        assertEquals("AUTHENTICATED",hello.status);
        assertFalse(hello.ready);
        assertTrue(hello.summary.contains("principal=founder"));

        field.absorbStatus(true,"aio.core.v1",42);
        AioPresenceField.AndroidShadow ready=field.projectAndroidShadow();
        assertEquals("READY",ready.status);
        assertTrue(ready.ready);
        assertTrue(ready.summary.contains("42 ms"));
        assertFalse(ready.summary.contains("�"));
    }

    @Test public void malformedPrincipalFailsClosed(){
        try{new AioPresenceField().absorbHello(" ","aio.core.v1");fail();}
        catch(IllegalArgumentException expected){assertEquals("PRESENCE_PRINCIPAL_INVALID",expected.getMessage());}
    }

    @Test public void malformedSchemaFailsClosed(){
        try{new AioPresenceField().absorbHello("founder","");fail();}
        catch(IllegalArgumentException expected){assertEquals("PRESENCE_SCHEMA_INVALID",expected.getMessage());}
    }

    @Test public void impossibleLatencyFailsClosed(){
        AioPresenceField field=new AioPresenceField();
        field.absorbHello("founder","aio.core.v1");
        try{field.absorbStatus(true,"aio.core.v1",-1);fail();}
        catch(IllegalArgumentException expected){assertEquals("PRESENCE_LATENCY_INVALID",expected.getMessage());}
    }
}
