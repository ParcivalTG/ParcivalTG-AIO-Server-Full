package com.aio.founder;

import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class FounderDialogueSubmitTest {
    @Test public void exactNormalShapeHasOnlySixCallerFields() {
        Set<String> names = Arrays.stream(FounderDialogueSubmit.class.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("intentId","text","privacyClass","provider","lease","presenceWitness"), names);
        assertFalse(names.contains("targetObjectiveId"));
        assertFalse(names.contains("expectedObjectiveVersion"));
        assertFalse(names.contains("acceptanceCriteria"));
        assertFalse(names.contains("objectiveFingerprint"));
    }

    @Test public void validLocalSubmitPreservesFounderText() {
        String id=UUID.randomUUID().toString();
        String text="  exact Founder intent\nUnicode: �  ";
        FounderDialogueSubmit s=new FounderDialogueSubmit(id,text,"TRADE_SECRET_LOCAL_ONLY","AIO","lease","witness");
        assertEquals(id,s.intentId);
        assertEquals(text,s.text);
        assertEquals("TRADE_SECRET_LOCAL_ONLY",s.privacyClass);
        assertEquals("AIO",s.provider);
    }

    @Test(expected=SecurityException.class) public void nonAioProviderRejected() {
        new FounderDialogueSubmit(UUID.randomUUID().toString(),"hello","LOCAL_ONLY","GPT","lease","witness");
    }

    @Test(expected=SecurityException.class) public void externalPrivacyRejected() {
        new FounderDialogueSubmit(UUID.randomUUID().toString(),"hello","EXTERNAL_ALLOWED","AIO","lease","witness");
    }

    @Test(expected=SecurityException.class) public void missingAuthorityProofRejected() {
        new FounderDialogueSubmit(UUID.randomUUID().toString(),"hello","LOCAL_ONLY","AIO","lease","");
    }

    @Test(expected=IllegalArgumentException.class) public void oversizedFounderTextRejected() {
        char[] chars=new char[4097]; Arrays.fill(chars,'a');
        new FounderDialogueSubmit(UUID.randomUUID().toString(),new String(chars),"LOCAL_ONLY","AIO","lease","witness");
    }
}
