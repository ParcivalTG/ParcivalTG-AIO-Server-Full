package com.aio.founder;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

final class AndroidClipboardBroker {
    private final Context context;
    private final ClipboardManager clipboard;

    AndroidClipboardBroker(Context context){
        this.context=context.getApplicationContext();
        this.clipboard=(ClipboardManager)this.context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(this.clipboard==null)throw new IllegalStateException("CLIPBOARD_SERVICE_UNAVAILABLE");
    }

    String readText(){
        AndroidClipboardPolicy.requireForeground(AioAppVisibility.isForegroundVisible());
        ClipData clip=clipboard.getPrimaryClip();
        if(clip==null||clip.getItemCount()<1)throw new IllegalStateException("CLIPBOARD_EMPTY_OR_UNAVAILABLE");
        CharSequence value=clip.getItemAt(0).coerceToText(context);
        if(value==null)throw new IllegalStateException("CLIPBOARD_TEXT_UNAVAILABLE");
        return AndroidClipboardPolicy.validateText(value.toString());
    }

    void writeText(String text){
        AndroidClipboardPolicy.requireForeground(AioAppVisibility.isForegroundVisible());
        String value=AndroidClipboardPolicy.validateText(text);
        ClipData clip=ClipData.newPlainText("AIO",value);
        PersistableBundle extras=new PersistableBundle();
        if(Build.VERSION.SDK_INT>=33)extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true);
        else extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
        clip.getDescription().setExtras(extras);
        clipboard.setPrimaryClip(clip);
    }
}
