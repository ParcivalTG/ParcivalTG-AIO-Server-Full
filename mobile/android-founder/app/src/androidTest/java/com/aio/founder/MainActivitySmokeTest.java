package com.aio.founder;

import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {
    @Test public void allFounderSurfacesRenderAndSurviveRecreation(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("AIO for Android | Founder candidate")).check(matches(isDisplayed()));
            onView(allOf(withText("Dialogue"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(allOf(withText("Live Fabric"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(allOf(withText("Desktop Lens"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(allOf(withText("Readiness"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(withText("Pairing & authority")).check(matches(isDisplayed()));

            onView(allOf(withText("Readiness"),isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withSubstring("ANDROID READINESS")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("Refresh readiness")).perform(scrollTo()).check(matches(isDisplayed()));

            onView(allOf(withText("Live Fabric"),isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withSubstring("PERSISTENT ANDROID NODE")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withSubstring("BACKGROUND READINESS")).perform(scrollTo()).check(matches(isDisplayed()));

            onView(allOf(withText("Desktop Lens"),isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withSubstring("ANDROID SCREEN OBSERVATION")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("Android AIO Node")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withSubstring("GOVERNED AIO SELF-UPDATE")).perform(scrollTo()).check(matches(isDisplayed()));

            scenario.recreate();
            onView(withText("AIO for Android | Founder candidate")).check(matches(isDisplayed()));
            onView(allOf(withText("Dialogue"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(allOf(withText("Readiness"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
        }
    }

    @Test public void FounderUiRendersAfterLandscapeConfigurationChange(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            onView(withText("AIO for Android | Founder candidate")).check(matches(isDisplayed()));
            onView(allOf(withText("Dialogue"),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));
            onView(allOf(withText("Live Fabric"),isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withSubstring("PERSISTENT ANDROID NODE")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void pairingSurfaceCanOpenWithoutNetworkOrSecrets(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Pairing & authority")).perform(click());
            onView(withText("Offline pairing & authority")).check(matches(isDisplayed()));
            onView(withText("Enable direct LAN permission when required")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("Save trusted pairing material")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
}
