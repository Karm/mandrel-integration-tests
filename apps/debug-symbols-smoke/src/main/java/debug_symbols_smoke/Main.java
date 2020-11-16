/*
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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
package debug_symbols_smoke;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This is a dummy example code to test some aspects of native-image debug capabilities
 * !!! DO NOT TOUCH THE SOURCE WITHOUT EDITING GDBSession.java !!!
 * Reads lines of text from stdin.
 * Creates instances with numbers and strings as attributes.
 * Stores those instances in a list.
 * Iterates over the list one by one and adds to ByteArray
 * Computes hash of the whole huge thing
 * Writes down the hash
 * !!! DO NOT TOUCH THE SOURCE WITHOUT EDITING GDBSession.java !!!
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class Main {

    public static void thisIsTheEnd(List<ClassA> ays) throws NoSuchAlgorithmException {
        final ByteArrayOutputStream ba = new ByteArrayOutputStream(ays.size() * 60); // ballpark magic constant hardcoded for test_data.txt purpose
        ays.forEach(i -> ba.writeBytes(i.toString().getBytes(UTF_8)));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ba.toByteArray());
        System.out.println(String.format("%064x", new BigInteger(1, digest.digest())));
        /* Used to verify the hash is what we think it is:
        $ sha256sum  /tmp/TEST.out
        b6951775b0375ea13fc977581e54eb36d483e95ed3bc1e62fcb8da59830f1ef9  /tmp/TEST.out
        try (FileOutputStream fos = new FileOutputStream("/tmp/TEST.out")) {
            fos.write(ba.toByteArray());
        }*/
        System.exit(0);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        final Pattern p = Pattern.compile("([\\+-]?\\d+)");
        final List<ClassA> ays = new ArrayList<>();
        String myString = null;
        int myNumber = Integer.MIN_VALUE;
        System.out.println("Q to quit");
        try (Scanner sc = new Scanner(System.in, UTF_8)) {
            while (sc.hasNextLine()) {
                if (myString != null && myNumber != Integer.MIN_VALUE) {
                    ays.add(new ClassA(myNumber, myString));
                    myString = null;
                    myNumber = Integer.MIN_VALUE;
                }
                String l = sc.nextLine();
                if ("Q".equals(l)) {
                    thisIsTheEnd(ays);
                }
                if (myNumber == Integer.MIN_VALUE) {
                    Matcher m = p.matcher(l);
                    if (m.matches()) {
                        myNumber = Integer.parseInt(m.group(1));
                    } else {
                        myString = l;
                    }
                } else {
                    myString = l;
                }
            }
        }
    }
}
