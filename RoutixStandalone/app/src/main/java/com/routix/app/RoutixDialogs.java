package com.routix.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

final class RoutixDialogs {
    static final class Builder extends AlertDialog.Builder {
        private final Context host;
        Builder(Context context){super(context,RoutixTheme.from(context).light()?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert);host=context;}
        @Override public AlertDialog create(){AlertDialog d=super.create();d.setOnShowListener(v->style(d,host));return d;}
        @Override public AlertDialog show(){AlertDialog d=super.show();style(d,host);return d;}
    }
    static void style(AlertDialog dialog,Context context){
        RoutixTheme.Tokens t=RoutixTheme.from(context);float density=context.getResources().getDisplayMetrics().density;
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(RoutixTheme.surface(t,t.base,22,density));tint(dialog.getWindow().getDecorView(),t);}
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=dialog.getButton(id);if(b!=null){b.setTextColor(t.accent);b.setAllCaps(false);b.setMinHeight((int)(52*density));}}
        ListView list=dialog.getListView();if(list!=null){list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View p,View c){tint(c,t);}public void onChildViewRemoved(View p,View c){}});}
    }
    private static void tint(View v,RoutixTheme.Tokens t){
        if(v instanceof TextView&&!(v instanceof Button)){TextView text=(TextView)v;if(v.getId()==android.R.id.text1||v.getId()==android.R.id.message||v.getId()==android.R.id.title||v.getId()==v.getResources().getIdentifier("alertTitle","id","android"))text.setTextColor(t.text);}
        if(v instanceof EditText){((EditText)v).setTextColor(t.text);((EditText)v).setHintTextColor(t.muted());v.setBackgroundTintList(ColorStateList.valueOf(t.accent));}
        if(v instanceof CheckedTextView)((CheckedTextView)v).setCheckMarkTintList(ColorStateList.valueOf(t.accent));
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)tint(g.getChildAt(i),t);}
    }
}
