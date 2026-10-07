package com.aio.founder;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ChatGptReasoningStore {
    private static final int MAX_REPLAY_ITEMS=16;
    private final AioSelectiveJournal journal;

    ChatGptReasoningStore(Context context){
        Context app=context.getApplicationContext();
        File root=new File(app.getFilesDir(),"aio-native-state-v1");
        journal=new AioSelectiveJournal(
            new File(root,"chatgpt-reasoning.aioj"),new AioJournalCipher("chatgpt-reasoning"));
    }

    synchronized void append(String intentId,List<String> reasoningItems)throws Exception{
        if(reasoningItems==null||reasoningItems.isEmpty())return;
        if(reasoningItems.size()>64)throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_COUNT");
        long now=System.currentTimeMillis();
        for(int i=0;i<reasoningItems.size();i++){
            String encoded=ChatGptReasoningRecord.encode(intentId,Math.max(1,now+i),reasoningItems.get(i));
            journal.append(encoded,Math.max(1,now+i));
        }
    }

    synchronized List<ChatGptResponsesContract.InputItem> replayTail(int limit)throws Exception{
        if(limit<0||limit>MAX_REPLAY_ITEMS)throw new IllegalArgumentException("CHATGPT_REASONING_REPLAY_LIMIT");
        AioSelectiveJournal.TailResult tail=journal.tail(limit);
        ArrayList<ChatGptResponsesContract.InputItem> out=new ArrayList<>();
        for(AioSelectiveJournal.Entry entry:tail.entries)
            out.add(ChatGptReasoningRecord.decode(entry.value).asInput());
        return List.copyOf(out);
    }

    synchronized String summary()throws Exception{
        AioSelectiveJournal.TailResult tail=journal.tail(Math.min(8,MAX_REPLAY_ITEMS));
        return "CHATGPT REASONING CONTINUITY"+
            "\nrecords="+journal.countRecords()+
            " | tail="+tail.entries.size()+
            " | materialized="+String.format(java.util.Locale.ROOT,"%.4f%%",tail.materializationFraction()*100.0);
    }

    synchronized void clear(){journal.clear();}
}
