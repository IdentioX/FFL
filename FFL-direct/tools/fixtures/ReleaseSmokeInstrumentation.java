package ru.ffl24.audit;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import java.nio.charset.StandardCharsets;

/** Framework-only black-box harness. No app, Kotlin, AndroidX or JDBC dependencies. */
public final class ReleaseSmokeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private String step="start";
    private interface Match { boolean accepts(AccessibilityNodeInfo node); }

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments=args;
        start();
    }

    private void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private AccessibilityNodeInfo visit(AccessibilityNodeInfo node, Match match) {
        if (node==null) return null;
        if (match.accepts(node)) return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo result=visit(node.getChild(i),match);
            if(result!=null) return result;
        }
        return null;
    }

    private AccessibilityNodeInfo find(Match match) {
        return visit(getUiAutomation().getRootInActiveWindow(),match);
    }

    private AccessibilityNodeInfo await(Match match) {
        long until=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<until) {
            AccessibilityNodeInfo node=find(match);
            if(node!=null) return node;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Expected controlled UI element did not appear");
    }

    private String value(AccessibilityNodeInfo node) {
        CharSequence raw=node.getText();
        if(raw==null || raw.length()==0) raw=node.getHintText();
        if(raw==null || raw.length()==0) raw=node.getContentDescription();
        String text=raw==null ? "" : raw.toString();
        if(text.startsWith("● ")) text=text.substring(2);
        return text.startsWith("№") ? text.substring(1) : text;
    }

    private boolean matches(AccessibilityNodeInfo node,String expected,boolean prefix) {
        String text=value(node);
        String description=node.getContentDescription()==null?"":node.getContentDescription().toString();
        return prefix ? text.startsWith(expected)||description.startsWith(expected) : text.equals(expected)||description.equals(expected);
    }

    private AccessibilityNodeInfo text(String expected, boolean prefix) {
        step="text: "+expected;
        return await(node -> matches(node,expected,prefix));
    }

    private void click(String expected, boolean prefix) {
        step="click: "+expected;
        try { getUiAutomation().waitForIdle(250,5000); } catch(java.util.concurrent.TimeoutException ignored) { }
        AccessibilityNodeInfo node=await(candidate -> {
            if (!matches(candidate,expected,prefix)) return false;
            while(candidate!=null && !candidate.isClickable()) candidate=candidate.getParent();
            return candidate!=null && candidate.isEnabled() && candidate.isVisibleToUser();
        });
        while(!node.isClickable()) node=node.getParent();
        require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Controlled click failed");
    }

    private void input(String value) {
        AccessibilityNodeInfo edit=await(AccessibilityNodeInfo::isEditable);
        Bundle data=new Bundle();
        data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        require(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data),"Controlled text input failed");
    }

    private void scrollTo(String expected) {
        step="scroll or await: "+expected;
        long until=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<until) {
            if(find(node -> matches(node,expected,false)&&node.isVisibleToUser())!=null)return;
            AccessibilityNodeInfo scroll=find(AccessibilityNodeInfo::isScrollable);
            if(scroll!=null)scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(250);
        }
        throw new AssertionError("Controlled action was not reachable by scrolling");
    }

    private void launch() {
        require("true".equals(arguments.getString("fflOwnedLocalRelease")),"Owned local release flag required");
        require("ru.ffl24.direct.dev".equals(getTargetContext().getPackageName()),"Unexpected target");
        Intent intent=new Intent().setComponent(new ComponentName("ru.ffl24.direct.dev","ru.ffl24.direct.MainActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getTargetContext().startActivity(intent);
    }

    private void loginAndDelivery() {
        launch();text("Поиск по имени",false);input("FFL E2E");
        click("FFL E2E Driver",true);click("Продолжить",false);
        text("Введите пароль",false);input("ffl-e2e-only-42");click("Войти",false);
        text("Первый рейс",true);click("Настройки",false);
        click("Выбранная дата",true);text("Рабочая дата",false);input("2099-12-31");
        click("Применить дату",false);click("Назад",false);
        click("FFL-E2E-FIRST",true);
        text("Адрес доставки",false);text("FFL controlled address",false);
        click("Сдал",false);click("Да, сдал",false);
        text("Заказ сдан",false);
    }

    private void coldRestoreAndLogout() {
        launch();text("Рабочая дата 2099-12-31",false);
        click("Сданные заказы",true);text("FFL-E2E-FIRST",true);
        click("Настройки",false);scrollTo("Выйти из аккаунта");
        click("Выйти из аккаунта",false);text("Поиск по имени",false);
    }

    private void describe(AccessibilityNodeInfo node, StringBuilder result, int depth) {
        if(node==null || depth>35) return;
        result.append(depth).append(" ").append(value(node)).append(" enabled=").append(node.isEnabled())
            .append(" visible=").append(node.isVisibleToUser()).append(" clickable=").append(node.isClickable()).append("\n");
        for(int i=0;i<node.getChildCount();i++) describe(node.getChild(i),result,depth+1);
    }

    private String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    @Override public void onStart() {
        String method=arguments.getString("method","");
        long started=SystemClock.elapsedRealtime();
        Throwable failure=null;
        try {
            if("loginAndDelivery".equals(method)) loginAndDelivery();
            else if("coldRestoreAndLogout".equals(method)) coldRestoreAndLogout();
            else throw new AssertionError("Unknown audit method");
        } catch(Throwable error) { failure=error; }
        String xml="<testsuite tests=\"1\" failures=\""+(failure==null?"0":"1")+"\" errors=\"0\" skipped=\"0\" time=\""+
            ((SystemClock.elapsedRealtime()-started)/1000.0)+"\"><testcase classname=\"ru.ffl24.audit.ReleaseSmokeInstrumentation\" name=\""+
            escape(method)+"\">"+(failure==null?"":"<failure>"+escape(failure.toString()+"; "+step)+"</failure>")+"</testcase></testsuite>";
        Bundle report=new Bundle();
        report.putString("fflAuditJUnitBase64",Base64.encodeToString(xml.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
        report.putString("fflAuditComplete",failure==null?"PASS":"FAIL");
        if(failure!=null) {
            StringBuilder tree=new StringBuilder();
            describe(getUiAutomation().getRootInActiveWindow(),tree,0);
            report.putString("fflPrivateHierarchyBase64",Base64.encodeToString(tree.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
        }
        sendStatus(0,report);
        finish(failure==null ? -1 : 0,report);
    }
}
