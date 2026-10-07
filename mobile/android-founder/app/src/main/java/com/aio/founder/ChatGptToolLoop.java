package com.aio.founder;

import java.util.ArrayList;
import java.util.List;

final class ChatGptToolLoop {
    static final class Execution {
        final String output;
        final List<ChatGptResponsesContract.InputItem> continuationInputs;
        Execution(String output,List<ChatGptResponsesContract.InputItem> continuationInputs){
            this.output=output;
            this.continuationInputs=continuationInputs==null?List.of():List.copyOf(continuationInputs);
        }
        static Execution text(String output){return new Execution(output,List.of());}
    }

    interface Executor {
        Execution execute(String name,String arguments)throws Exception;
    }

    static final class Step {
        final boolean terminal;
        final ChatGptResponsesContract.Completion completion;
        final List<ChatGptResponsesContract.InputItem> nextInput;
        Step(boolean terminal,ChatGptResponsesContract.Completion completion,
             List<ChatGptResponsesContract.InputItem> nextInput){
            this.terminal=terminal;this.completion=completion;this.nextInput=nextInput;
        }
    }

    private ChatGptToolLoop(){}

    static Step advance(List<ChatGptResponsesContract.InputItem> current,
                        ChatGptResponsesContract.Completion completion,
                        Executor executor)throws Exception{
        if(current==null||completion==null||executor==null)
            throw new IllegalArgumentException("CHATGPT_TOOL_LOOP_INPUT_REQUIRED");
        if(completion.functionCalls.isEmpty())
            return new Step(true,completion,List.copyOf(current));
        if(completion.functionCalls.size()!=1)
            throw new SecurityException("CHATGPT_PARALLEL_TOOL_CALL_DENIED");

        ChatGptResponsesContract.FunctionCall call=completion.functionCalls.get(0);
        ChatGptAioToolContract.requireKnown(call.name);

        ArrayList<ChatGptResponsesContract.InputItem> next=new ArrayList<>(current);
        for(String item:completion.reasoningItems)
            next.add(ChatGptResponsesContract.reasoning(item));
        next.add(call.asInput());

        Execution execution=executor.execute(call.name,call.arguments);
        if(execution==null||execution.output==null||execution.output.isBlank()||
           execution.output.length()>128*1024)
            throw new IllegalArgumentException("CHATGPT_FUNCTION_OUTPUT_INVALID");
        next.add(ChatGptResponsesContract.functionOutput(call.callId,execution.output));
        for(ChatGptResponsesContract.InputItem item:execution.continuationInputs){
            if(item==null||item.json()==null||item.json().isBlank())
                throw new IllegalArgumentException("CHATGPT_TOOL_CONTINUATION_INVALID");
            next.add(item);
        }

        if(next.size()>128)throw new IllegalArgumentException("CHATGPT_INPUT_COUNT_INVALID");
        return new Step(false,null,List.copyOf(next));
    }
}
