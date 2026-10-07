package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidCapabilityBrokerPathTest {
    @Test public void rootPathAllowed()throws Exception{
        assertEquals("",AndroidCapabilityBroker.normalize(""));
        assertEquals("docs/a.txt",AndroidCapabilityBroker.normalize("docs/a.txt"));
    }
    @Test public void traversalSegmentsRejected(){
        for(String path:new String[]{"../a","a/../b","./a","a/./b","a//b","/a","a/"}){
            try{AndroidCapabilityBroker.normalize(path);fail(path);}
            catch(Exception expected){assertEquals("PATH_OUTSIDE_GRANTED_TREE",expected.getMessage());}
        }
    }
    @Test public void windowsSeparatorsNormalizeWithinTree()throws Exception{
        assertEquals("docs/a.txt",AndroidCapabilityBroker.normalize("docs\\a.txt"));
    }
}
