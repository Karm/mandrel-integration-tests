/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package calendar;

import java.util.Calendar;
import java.util.Locale;

/**
 * @author Severin Gehwolf <sgehwolf@redhat.com>
 */
public class Main {

    private static void doCalTest(String type) {
        Calendar cal = new Calendar.Builder()
                .setCalendarType(type)
                .setFields(Calendar.YEAR, 1, Calendar.DAY_OF_YEAR, 1)
                .build();
        int year = cal.get(Calendar.YEAR);
        String t = cal.getCalendarType();
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        System.out.printf("Year: %d, dayOfYear: %d, type: %s%n", year, dayOfYear, t);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US); // Don't rely on the default locale of the system.
        String[] types = new String[] { "japanese", "buddhist", "gregory" };
        for (String t : types) {
            doCalTest(t);
        }
    }
}
