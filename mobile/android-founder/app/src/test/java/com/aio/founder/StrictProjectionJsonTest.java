package com.aio.founder;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class StrictProjectionJsonTest {
    private void expect(String json,int budget,String code){
        try{
            StrictProjectionJson.object(json.getBytes(StandardCharsets.UTF_8),budget);
            fail();
        }catch(Exception expected){
            assertEquals(code,expected.getMessage());
        }
    }

    @Test public void parsesSmallObject()throws Exception{
        StrictProjectionJson.ObjectValue value=StrictProjectionJson.object(
            "{\"a\":1,\"b\":true,\"c\":\"x\"}".getBytes(StandardCharsets.UTF_8),1024);
        assertEquals(1L,value.get("a"));
        assertEquals(Boolean.TRUE,value.get("b"));
        assertEquals("x",value.get("c"));
    }

    @Test public void duplicateKeysFailClosed(){
        expect("{\"a\":1,\"a\":2}",1024,"FABRIC_JSON_DUPLICATE_OR_KEY_LIMIT");
    }

    @Test public void nonObjectRootFailsClosed(){
        expect("[1,2,3]",1024,"FABRIC_JSON_ROOT");
    }

    @Test public void malformedSurrogateFailsClosed(){
        expect("{\"x\":\"\\uD800\"}",1024,"FABRIC_JSON_UNICODE");
    }

    @Test public void nonCanonicalNumberFailsClosed(){
        expect("{\"x\":+1}",1024,"FABRIC_JSON_NUMBER");
    }

    @Test public void wireBudgetFailsBeforeParsing(){
        expect("{\"a\":1}",4,"FABRIC_WIRE_BUDGET");
    }

    @Test public void excessiveDepthFailsClosed(){
        String json="{\"x\":".repeat(18)+"1"+"}".repeat(18);
        expect(json,4096,"FABRIC_JSON_COMPLEXITY");
    }

    @Test public void trailingContentFailsClosed(){
        expect("{\"a\":1} {}",1024,"FABRIC_JSON_ROOT");
    }
}
