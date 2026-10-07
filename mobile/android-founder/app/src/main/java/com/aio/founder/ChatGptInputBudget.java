package com.aio.founder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Selective stateless-input admission. Keeps the newest useful items within a hard JSON budget. */
final class ChatGptInputBudget {
    private ChatGptInputBudget(){}

    static List<ChatGptResponsesContract.InputItem> newestWithin(
            List<ChatGptResponsesContract.InputItem> items,int maxItems,int maxJsonChars){
        if(items==null||maxItems<0||maxItems>128||maxJsonChars<0||maxJsonChars>512*1024)
            throw new IllegalArgumentException("CHATGPT_INPUT_BUDGET_INVALID");
        ArrayList<ChatGptResponsesContract.InputItem> reversed=new ArrayList<>();
        int chars=0;
        for(int i=items.size()-1;i>=0&&reversed.size()<maxItems;i--){
            ChatGptResponsesContract.InputItem item=items.get(i);
            if(item==null||item.json()==null)continue;
            int cost=item.json().length();
            if(cost>maxJsonChars)continue;
            if(chars+cost>maxJsonChars)break;
            reversed.add(item);
            chars+=cost;
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }
}
