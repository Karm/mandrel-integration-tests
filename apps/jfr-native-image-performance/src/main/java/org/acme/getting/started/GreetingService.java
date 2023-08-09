package org.acme.getting.started;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.locks.LockSupport;

@ApplicationScoped
public class GreetingService {
    public String greeting(String name) {
        return "hello " + name + "JFR TEST ";
    }

    private String getNextString(String text) throws InterruptedException {
        LockSupport.parkNanos(1);
        LockSupport.parkNanos(this,1);
        return Integer.toString(text.hashCode() % (int) (Math.random() * 100));
    }

    /** This endpoint is used to test new JFR development changes. Always with JFR recording.
     * Tries to emphasize impact of JFR changes on performance.*/
    public String work(String text) {
        String result = "";
        for (int i = 0; i < 1000; i++){
            try {
                result += getNextString(text);
            } catch (Exception e) {
                // Doesn't matter. Do nothing
            }
            CustomEvent customEvent = new CustomEvent();
            // Only commit the first few letters (goal is to spend time in the JFR piping not committing chars)
            customEvent.message = result.substring(0,2);
            customEvent.commit();
        }

        return result;
    }

    /** This endpoint is used to compare between with/without JFR built into the image.
     * Therefore it must not use any custom JFR events or the Event API at all, to avoid runtime errors.
     * It should have less unrealistic tasks, unlike GreetingService#work which simply loops to create many events.*/
    public String regular(String text) {
        String result = text;
        int count = (int) (Math.random() * (20)) + 10;

        String temp = Integer.toString(result.hashCode()).repeat(count);
        result = "";
        for (int j = 0; j < temp.length(); j += 2) {
            result += temp.charAt(j);
        }

        // Ensure an event is emitted
        LockSupport.parkNanos(this,1);
        return result;
    }
}
