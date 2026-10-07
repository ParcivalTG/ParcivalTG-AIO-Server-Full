package com.aio.founder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptInputBudgetTest {
    @Test public void keepsNewestItemsWithinBudget(){
        ArrayList<ChatGptResponsesContract.InputItem> items=new ArrayList<>();
        items.add(ChatGptResponsesContract.message("user","old-"+repeat('a',80)));
        items.add(ChatGptResponsesContract.message("assistant","mid-"+repeat('b',80)));
        items.add(ChatGptResponsesContract.message("user","new-"+repeat('c',20)));
        List<ChatGptResponsesContract.InputItem> selected=
            ChatGptInputBudget.newestWithin(items,8,180);
        assertFalse(selected.isEmpty());
        assertTrue(selected.get(selected.size()-1).json().contains("new-"));
        int total=selected.stream().mapToInt(x->x.json().length()).sum();
        assertTrue(total<=180);
    }

    @Test public void oversizedNewestItemIsSkippedForOlderUsefulContinuity(){
        ChatGptResponsesContract.InputItem older=ChatGptResponsesContract.message("user","usable");
        ChatGptResponsesContract.InputItem huge=ChatGptResponsesContract.message("assistant",repeat('x',1000));
        List<ChatGptResponsesContract.InputItem> selected=
            ChatGptInputBudget.newestWithin(List.of(older,huge),8,256);
        assertEquals(1,selected.size());
        assertTrue(selected.get(0).json().contains("usable"));
    }

    @Test public void badBudgetsFailClosed(){
        try{ChatGptInputBudget.newestWithin(List.of(),129,10);fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_INPUT_BUDGET_INVALID",expected.getMessage());}
    }

    private static String repeat(char c,int count){
        char[] chars=new char[count];
        java.util.Arrays.fill(chars,c);
        return new String(chars);
    }
}
