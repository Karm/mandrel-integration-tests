/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
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
package reslocations;

import com.sun.imageio.plugins.common.I18N;
import com.sun.imageio.plugins.common.I18NImpl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;


/**
 * Related to:
 * https://github.com/quarkusio/quarkus/pull/22403
 * https://github.com/quarkusio/quarkus/pull/24588
 * https://github.com/oracle/graal/issues/4326
 */
public class Main {

    private static final String R = "iio-plugin.properties";
    private static final String S_R = "/iio-plugin.properties";
    private static final String R_X = "com/sun/imageio/plugins/common/iio-plugin.properties";
    private static final String S_R_X = "/com/sun/imageio/plugins/common/iio-plugin.properties";

    private static final String F = "folder";
    private static final String F_R = "folder/";
    private static final String F_R_R = "folder/./";
    private static final String F_R_R_R = "folder/././";

    public static void main(String[] args) throws ClassNotFoundException, IOException {

        System.out.println("Resources folders:");
        final URL[] urls = new URL[]{
                /* 0*/ MethodHandles.lookup().lookupClass().getResource(F),
                /* 1*/ MethodHandles.lookup().lookupClass().getResource(F_R),
                /* 2*/ MethodHandles.lookup().lookupClass().getResource(F_R_R),
                /* 3*/ MethodHandles.lookup().lookupClass().getResource(F_R_R_R),
                /* 4*/ Thread.currentThread().getContextClassLoader().getResource(F),
                /* 5*/ Thread.currentThread().getContextClassLoader().getResource(F_R),
                /* 6*/ Thread.currentThread().getContextClassLoader().getResource(F_R_R),
                /* 7*/ Thread.currentThread().getContextClassLoader().getResource(F_R_R_R),
                /* 8*/ Main.class.getResource(F),
                /* 9*/ Main.class.getResource(F_R),
                /*10*/ Main.class.getResource(F_R_R),
                /*11*/ Main.class.getResource(F_R_R_R)
        };
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.length; i++) {
            sb.append(i).append(i < 10 ? ":  " : ": ")
                    .append(markDir(urls[i]))
                    .append("\n");
        }
        System.out.println(sb);

        System.out.println("iio-plugin.properties:");
        final InputStream[] isa = new InputStream[]{
                /* 0*/ MethodHandles.lookup().lookupClass().getResourceAsStream(R),
                /* 1*/ MethodHandles.lookup().lookupClass().getResourceAsStream(S_R),
                /* 2*/ MethodHandles.lookup().lookupClass().getResourceAsStream(R_X),
                /* 3*/ MethodHandles.lookup().lookupClass().getResourceAsStream(S_R_X),
                /* 4*/ Class.forName("com.sun.imageio.plugins.common.I18NImpl").getResourceAsStream(R),
                /* 5*/ Class.forName("com.sun.imageio.plugins.common.I18NImpl").getResourceAsStream(S_R),
                /* 6*/ Class.forName("com.sun.imageio.plugins.common.I18NImpl").getResourceAsStream(R_X),
                /* 7*/ Class.forName("com.sun.imageio.plugins.common.I18NImpl").getResourceAsStream(S_R_X),
                /* 8*/ Class.forName("com.sun.imageio.plugins.common.I18N").getResourceAsStream(R),
                /* 9*/ Class.forName("com.sun.imageio.plugins.common.I18N").getResourceAsStream(S_R),
                /*10*/ Class.forName("com.sun.imageio.plugins.common.I18N").getResourceAsStream(R_X),
                /*11*/ Class.forName("com.sun.imageio.plugins.common.I18N").getResourceAsStream(S_R_X),
                /*12*/ Thread.currentThread().getContextClassLoader().getResourceAsStream(R),
                /*13*/ Thread.currentThread().getContextClassLoader().getResourceAsStream(S_R),
                /*14*/ Thread.currentThread().getContextClassLoader().getResourceAsStream(R_X),
                /*15*/ Thread.currentThread().getContextClassLoader().getResourceAsStream(S_R_X),
                /*16*/ Thread.currentThread().getContextClassLoader().loadClass("com.sun.imageio.plugins.common.I18N").getResourceAsStream(R),
                /*17*/ Thread.currentThread().getContextClassLoader().loadClass("com.sun.imageio.plugins.common.I18N").getResourceAsStream(S_R),
                /*18*/ Thread.currentThread().getContextClassLoader().loadClass("com.sun.imageio.plugins.common.I18N").getResourceAsStream(R_X),
                /*19*/ Thread.currentThread().getContextClassLoader().loadClass("com.sun.imageio.plugins.common.I18N").getResourceAsStream(S_R_X),
                /*20*/ I18NImpl.class.getResourceAsStream(R),
                /*21*/ I18NImpl.class.getResourceAsStream(S_R),
                /*22*/ I18NImpl.class.getResourceAsStream(R_X),
                /*23*/ I18NImpl.class.getResourceAsStream(S_R_X),
                /*24*/ I18N.class.getResourceAsStream(R),
                /*25*/ I18N.class.getResourceAsStream(S_R),
                /*26*/ I18N.class.getResourceAsStream(R_X),
                /*27*/ I18N.class.getResourceAsStream(S_R_X),
                /*28*/ ModuleLayer.boot().findModule("java.desktop").get().getResourceAsStream(R),
                /*29*/ ModuleLayer.boot().findModule("java.desktop").get().getResourceAsStream(S_R),
                /*30*/ ModuleLayer.boot().findModule("java.desktop").get().getResourceAsStream(R_X),
                /*31*/ ModuleLayer.boot().findModule("java.desktop").get().getResourceAsStream(S_R_X)
        };
        sb.setLength(0);
        for (int i = 0; i < isa.length; i++) {
            sb.append(i).append(i < 10 ? ":  " : ": ")
                    .append(markContent(isa[i])).append("\n");
        }
        System.out.println(sb);
    }

    private static String markDir(URL url) throws IOException {
        if (url == null) {
            return "N/A";
        }
        if (url.toString().endsWith("/")) {
            return "SLASH " + markContent(new URL(url + "iio-plugin.properties").openStream());
        } else {
            return "NO_SLASH " + markContent(new URL(url + "/iio-plugin.properties").openStream());
        }
    }

    private static String markContent(InputStream is) throws IOException {
        if (is == null) {
            return "N/A";
        }
        final int len = new String(is.readAllBytes(), StandardCharsets.UTF_8).length();
        if (len == 5) {
            return "APP";
            // It's the length of the property file in JDK 11 and 17.
            // It might seem fragile, but the file is old and stable.
        } else if (len == 1902) {
            return "JDK";
        } else if (len == 15) {
            return "FOLDER";
        } else {
            return "???";
        }
    }
}
