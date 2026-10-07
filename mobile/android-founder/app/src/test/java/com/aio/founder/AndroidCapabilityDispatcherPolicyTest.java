package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidCapabilityDispatcherPolicyTest {
    private AndroidCapabilityProtocol.Request parse(String capability,String action,String args)throws Exception{
        String json="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\""+capability+
            "\",\"action\":\""+action+"\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":"+args+"}";
        return AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void rootListMayOmitPath()throws Exception{
        AndroidCapabilityProtocol.Request r=parse("FILE_READ","file.list","{}");
        AndroidCapabilityDispatcher.args(r.args,Set.of("path"),Set.of());
        assertEquals("",AndroidCapabilityProtocol.optionalString(r.args,"path",512,""));
    }

    @Test public void readBudgetDefaultsWhenOmitted()throws Exception{
        AndroidCapabilityProtocol.Request r=parse("FILE_READ","file.read","{\"path\":\"docs/a.txt\"}");
        AndroidCapabilityDispatcher.args(r.args,Set.of("path","maxBytes"),Set.of("path"));
        assertEquals(262144L,AndroidCapabilityProtocol.integer(r.args,"maxBytes",1,262144,262144));
    }

    @Test public void readRejectsUnknownArgument()throws Exception{
        AndroidCapabilityProtocol.Request r=parse("FILE_READ","file.read","{\"path\":\"docs/a.txt\",\"surprise\":1}");
        try{
            AndroidCapabilityDispatcher.args(r.args,Set.of("path","maxBytes"),Set.of("path"));
            fail();
        }catch(IllegalArgumentException expected){assertEquals("ANDROID_ARGS_KEYS",expected.getMessage());}
    }

    @Test public void renameRequiresNewName()throws Exception{
        AndroidCapabilityProtocol.Request r=parse("FILE_WRITE","file.rename","{\"path\":\"docs/a.txt\"}");
        try{
            AndroidCapabilityDispatcher.args(r.args,Set.of("path","newName"),Set.of("path","newName"));
            fail();
        }catch(IllegalArgumentException expected){assertEquals("ANDROID_ARGS_KEYS",expected.getMessage());}
    }
}
